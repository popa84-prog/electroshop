import { useEffect, useMemo, useRef, useState } from 'react';
import productService from '../../api/productService';
import adminService from '../../api/adminService';
import orderService from '../../api/orderService';
import AdminNav from '../../components/AdminNav';
import Modal from '../../components/Modal';
import Pagination from '../../components/Pagination';
import { showToast, ToastHost } from '../../components/Toast';
import {
  GeoIcon,
  HoloInput,
  HoloLoader,
  NeonBadge,
  NeonButton,
  SectionHeader,
  TiltCard,
} from '../../components/xxii';
import { formatPrice, resolveImage, formatDate } from '../../utils/format';
import { ACTION_LABELS } from '../../utils/auditLabels';
import { useDebounce } from '../../hooks/useDebounce';
import { cachedList, invalidateListCache } from '../../utils/listCache';

/** Cache namespace for this page's list (feature #7 — cache pentru liste mari). */
const LIST_CACHE_NS = 'admin-products';

/** Selectable page sizes for the products table. */
const PAGE_SIZES = [10, 20, 50, 100, 200];
const PAGE_SIZE_KEY = 'admin.products.pageSize';

/**
 * XXII — TASK 6 asks for "product management with 3D cards and hover tilt". A
 * 200-row spreadsheet is the wrong place for that, so the page carries both
 * surfaces and lets the operator pick: `table` for bulk work (inline edit,
 * multi-select, dense scanning) and `grid` for visual work (checking that the
 * catalogue photographs well, spotting the products with no image at all).
 * The choice is remembered, because an operator who prefers one view prefers
 * it every day.
 */
const VIEW_MODE_KEY = 'admin.products.view';
const VIEW_MODES = ['table', 'grid'];

/** Word the operator must type to confirm a deletion. */
const DELETE_KEYWORD = 'STERG';

/**
 * Word the operator must type to confirm the separate, irreversible
 * force-delete override — deliberately different from {@link DELETE_KEYWORD}
 * so the two confirmations can never be typed on autopilot from muscle
 * memory. This path removes a product's order/purchase history rows
 * permanently, not just the product row.
 */
const FORCE_DELETE_KEYWORD = 'ISTORIC';

/** Quick-filter shortcuts (feature: filtre rapide) — mirrors the backend's `quickFilter` param. */
const QUICK_FILTERS = [
  { key: null, label: 'Toate', icon: 'grid' },
  { key: 'low_stock', label: 'Stoc redus', icon: 'alert' },
  { key: 'out_of_stock', label: 'Fără stoc', icon: 'box' },
  { key: 'no_image', label: 'Fără imagine', icon: 'zoom' },
];

/** Remembers the chosen page size between visits; falls back to 10. */
function readStoredPageSize() {
  try {
    const stored = Number(window.localStorage.getItem(PAGE_SIZE_KEY));
    return PAGE_SIZES.includes(stored) ? stored : 10;
  } catch {
    return 10;
  }
}

/** Remembers the chosen surface between visits; falls back to the table. */
function readStoredViewMode() {
  try {
    const stored = window.localStorage.getItem(VIEW_MODE_KEY);
    return VIEW_MODES.includes(stored) ? stored : 'table';
  } catch {
    return 'table';
  }
}

const emptyForm = {
  name: '',
  description: '',
  price: '',
  purchasePrice: '',
  stockQuantity: '',
  category: '',
  subcategory: '',
  brand: '',
  sku: '',
  imageUrl: '',
};

export default function AdminProducts() {
  const [products, setProducts] = useState([]);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(readStoredPageSize);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [search, setSearch] = useState('');
  const [quickFilter, setQuickFilter] = useState(null);
  const [loading, setLoading] = useState(true);

  // XXII — the slide-in filter panel (TASK 6). Collapsed by default so the
  // table starts higher up the screen; the active-filter count stays visible on
  // the trigger, so nothing is ever filtered invisibly.
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [viewMode, setViewMode] = useState(readStoredViewMode);

  // Multi-select: ids of the rows ticked on the current page.
  const [selectedIds, setSelectedIds] = useState(() => new Set());
  const selectAllRef = useRef(null);

  // Two-step delete confirmation. `pending` holds the products queued for
  // removal; `deleteStep` is 1 (review) or 2 (type the keyword).
  const [pending, setPending] = useState([]);
  const [deleteStep, setDeleteStep] = useState(0);
  const [deleteWord, setDeleteWord] = useState('');
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteError, setDeleteError] = useState(null);

  // Force-delete offer: shown right after a normal delete/bulk-delete leaves
  // one or more products deactivated instead of removed (they have
  // order/purchase history). `forceCandidates` holds exactly those products
  // — never the whole original selection — and `forceStep` is 0 (closed) or
  // 1 (type-the-keyword confirmation). A single step is enough here: the
  // operator already reviewed the product list during the normal delete
  // flow moments earlier, so this only needs the one, more severe warning.
  const [forceCandidates, setForceCandidates] = useState([]);
  const [forceStep, setForceStep] = useState(0);
  const [forceWord, setForceWord] = useState('');
  const [forceBusy, setForceBusy] = useState(false);
  const [forceError, setForceError] = useState(null);

  // Spreadsheet export of the stock list.
  const [exporting, setExporting] = useState(false);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [imageFile, setImageFile] = useState(null);

  // Image gallery manager (feature #5 + Task 4: reorder, delete confirm, dimensions)
  const [images, setImages] = useState([]);
  const [imgBusy, setImgBusy] = useState(false);
  const [imgError, setImgError] = useState(null);
  const [dragOver, setDragOver] = useState(false);
  const [confirmDeleteImageId, setConfirmDeleteImageId] = useState(null);
  const [dragImageId, setDragImageId] = useState(null);
  const [dragOverImageId, setDragOverImageId] = useState(null);

  // Inline edit (feature: editare rapidă direct în tabel) — { id, field } or null.
  const [editingCell, setEditingCell] = useState(null);
  const [editValue, setEditValue] = useState('');
  const [editBusy, setEditBusy] = useState(false);

  // Quick preview modal (feature: previzualizare produs)
  const [previewProduct, setPreviewProduct] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  // Per-product audit history shown in the preview popup (feature #5 — istoric
  // prețuri / istoric stoc / istoric imagini).
  const [previewHistory, setPreviewHistory] = useState([]);
  const [previewHistoryLoading, setPreviewHistoryLoading] = useState(false);
  const [activeBusyId, setActiveBusyId] = useState(null);
  // Batch activate/deactivate — the selection toolbar's "Activează selectate" /
  // "Dezactivează selectate" actions (mirrors the per-row toggleActive below,
  // just running over every ticked product in one request).
  const [bulkActiveBusy, setBulkActiveBusy] = useState(false);

  // Feature #10 — "VÂNDUT" quick-sale cart. The operator adds one or more
  // distinct products (each with its own quantity/price — "3 of this, 1 of
  // that") by clicking 💵 on each row, then reviews and finalizes the whole
  // cart as ONE order from the floating bar / modal below. Cart state is
  // intentionally independent of the table's page/search/filter state so it
  // survives while the operator searches for the next product to add.
  const [saleCart, setSaleCart] = useState([]);
  const [saleCartOpen, setSaleCartOpen] = useState(false);
  const [saleCartBusy, setSaleCartBusy] = useState(false);
  const [saleCartError, setSaleCartError] = useState(null);

  const ALLOWED_TYPES = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
  const MAX_BYTES = 5 * 1024 * 1024;

  // Import state
  const [importOpen, setImportOpen] = useState(false);
  const [importFile, setImportFile] = useState(null);
  const [importReport, setImportReport] = useState(null);
  const [importBusy, setImportBusy] = useState(false);
  const [importError, setImportError] = useState(null);
  const [importDone, setImportDone] = useState(null);
  // When true, the modal only syncs purchase prices (no create/update of other fields).
  const [syncMode, setSyncMode] = useState(false);
  // When true, import runs in "intrare marfă" mode: existing products get stock
  // added and a quantity-weighted average purchase price.
  const [restockMode, setRestockMode] = useState(false);

  // Feature #7 (performance): the input stays instantly responsive, but the
  // actual request only fires 350ms after the operator stops typing — instead
  // of one API call per keystroke.
  const debouncedSearch = useDebounce(search, 350);

  const load = () => {
    setLoading(true);
    const params = { page, size: pageSize, search: debouncedSearch, quickFilter: quickFilter || undefined };
    // Admin endpoint: includes purchasePrice + profit (only visible to admins).
    // Feature #7: short-TTL cache — paging back to a page/filter combo seen in
    // the last few seconds skips the network round-trip entirely.
    cachedList(LIST_CACHE_NS, params, () => adminService.listAdminProducts(params))
      .then((data) => {
        setProducts(data.content);
        setTotalPages(data.totalPages);
        setTotalElements(data.totalElements ?? data.content.length);
      })
      .catch(() => setProducts([]))
      .finally(() => setLoading(false));
  };

  useEffect(load, [page, pageSize, debouncedSearch, quickFilter]);

  // A tick only ever refers to a row the operator can currently see, so the
  // selection is dropped whenever the visible set changes.
  useEffect(() => {
    setSelectedIds(new Set());
  }, [page, pageSize, debouncedSearch, quickFilter]);

  const allOnPageSelected = products.length > 0 && products.every((p) => selectedIds.has(p.id));
  const someOnPageSelected = products.some((p) => selectedIds.has(p.id));

  // "Partially selected" has no HTML attribute — it must be set on the node.
  useEffect(() => {
    if (selectAllRef.current) {
      selectAllRef.current.indeterminate = someOnPageSelected && !allOnPageSelected;
    }
  }, [someOnPageSelected, allOnPageSelected]);

  const selectedProducts = useMemo(
    () => products.filter((p) => selectedIds.has(p.id)),
    [products, selectedIds]
  );

  const toggleOne = (id) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const toggleAllOnPage = () => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (products.every((p) => next.has(p.id))) {
        products.forEach((p) => next.delete(p.id));
      } else {
        products.forEach((p) => next.add(p.id));
      }
      return next;
    });
  };

  const changePageSize = (size) => {
    setPage(0);
    setPageSize(size);
    try {
      window.localStorage.setItem(PAGE_SIZE_KEY, String(size));
    } catch {
      // Storage unavailable (private mode) — the choice simply is not remembered.
    }
  };

  const chooseQuickFilter = (key) => {
    setPage(0);
    setQuickFilter(key);
  };

  /** Switches surface and remembers it. Paging/selection are deliberately kept:
   *  the same products stay on screen, only their presentation changes. */
  const changeViewMode = (mode) => {
    setViewMode(mode);
    try {
      window.localStorage.setItem(VIEW_MODE_KEY, mode);
    } catch {
      // Storage unavailable (private mode) — the choice simply is not remembered.
    }
  };

  // ---- Inline edit (feature: editare rapidă) ----
  // The backend's PUT /products/{id} replaces every field, so a save must
  // resend the full current product payload with only the edited field changed.
  const startEdit = (p, field) => {
    setEditingCell({ id: p.id, field });
    setEditValue(field === 'price' ? p.price : p.stockQuantity);
  };

  const cancelEdit = () => {
    setEditingCell(null);
    setEditValue('');
  };

  const saveEdit = async (p) => {
    if (!editingCell || editingCell.id !== p.id) return;
    const { field } = editingCell;
    const numeric = Number(editValue);
    if (editValue === '' || Number.isNaN(numeric) || numeric < 0) {
      showToast('Valoare invalidă.', 'error');
      cancelEdit();
      return;
    }
    // No real change — skip the round-trip entirely.
    if (numeric === Number(p[field])) {
      cancelEdit();
      return;
    }
    setEditBusy(true);
    try {
      const payload = {
        name: p.name,
        description: p.description || '',
        price: field === 'price' ? numeric : Number(p.price),
        purchasePrice: p.purchasePrice ?? null,
        stockQuantity: field === 'stockQuantity' ? numeric : Number(p.stockQuantity),
        category: p.category || '',
        subcategory: p.subcategory || '',
        brand: p.brand || '',
        sku: p.sku || '',
        imageUrl: p.imageUrl || '',
      };
      await productService.update(p.id, payload);
      showToast('Produs actualizat.', 'success');
      cancelEdit();
      invalidateListCache(LIST_CACHE_NS);
      load();
    } catch (err) {
      showToast(err.response?.data?.message || 'Actualizarea a eșuat.', 'error');
    } finally {
      setEditBusy(false);
    }
  };

  // ---- Activate / deactivate (feature #5) — hides a product from the storefront
  // without deleting it; every toggle is written to the audit log. ----
  const toggleActive = async (p) => {
    setActiveBusyId(p.id);
    try {
      if (p.active) {
        await productService.deactivate(p.id);
        showToast(`${p.name} a fost dezactivat.`, 'success');
      } else {
        await productService.activate(p.id);
        showToast(`${p.name} a fost activat.`, 'success');
      }
      invalidateListCache(LIST_CACHE_NS);
      load();
    } catch (err) {
      showToast(err.response?.data?.message || 'Schimbarea stării produsului a eșuat.', 'error');
    } finally {
      setActiveBusyId(null);
    }
  };

  // Batch counterpart of toggleActive — activates or deactivates every currently
  // selected product in one request instead of clicking "Activează" per row.
  const bulkSetActive = async (active) => {
    const ids = selectedProducts.map((p) => p.id);
    if (ids.length === 0) return;
    setBulkActiveBusy(true);
    try {
      const result = active
        ? await productService.bulkActivate(ids)
        : await productService.bulkDeactivate(ids);
      const count = result?.updated ?? ids.length;
      showToast(
        count === 0
          ? active
            ? 'Produsele selectate erau deja active.'
            : 'Produsele selectate erau deja inactive.'
          : active
          ? `${count} ${count === 1 ? 'produs activat' : 'produse activate'}.`
          : `${count} ${count === 1 ? 'produs dezactivat' : 'produse dezactivate'}.`,
        'success'
      );
      setSelectedIds(new Set());
      invalidateListCache(LIST_CACHE_NS);
      load();
    } catch (err) {
      showToast(
        err.response?.data?.message ||
          (active ? 'Activarea în masă a eșuat.' : 'Dezactivarea în masă a eșuat.'),
        'error'
      );
    } finally {
      setBulkActiveBusy(false);
    }
  };

  // ---- Feature #10 — "VÂNDUT" sale cart: click 💵 on any number of rows to
  // build up a multi-product sale ("3 of this, 1 of that"), then finalize the
  // whole cart as ONE order (see OrderService.sellBatch on the backend). ----

  /** Adds a row to the cart, or bumps its quantity by 1 if it's already there. */
  const addToSaleCart = (p) => {
    setSaleCart((prev) => {
      const existing = prev.find((l) => l.productId === p.id);
      if (existing) {
        if (existing.quantity >= p.stockQuantity) {
          showToast(`Doar ${p.stockQuantity} buc. în stoc din ${p.name}.`, 'error');
          return prev;
        }
        return prev.map((l) => (l.productId === p.id ? { ...l, quantity: l.quantity + 1 } : l));
      }
      showToast(`${p.name} adăugat în vânzare.`, 'success');
      return [
        ...prev,
        {
          productId: p.id,
          name: p.name,
          imageUrl: p.imageUrl,
          stockQuantity: p.stockQuantity,
          quantity: 1,
          unitPrice: p.price != null ? String(p.price) : '0',
        },
      ];
    });
  };

  const removeFromSaleCart = (productId) => {
    setSaleCart((prev) => prev.filter((l) => l.productId !== productId));
  };

  const updateSaleCartLine = (productId, field, value) => {
    setSaleCartError(null);
    setSaleCart((prev) => prev.map((l) => (l.productId === productId ? { ...l, [field]: value } : l)));
  };

  const clearSaleCart = () => {
    setSaleCart([]);
    setSaleCartError(null);
  };

  const saleCartCount = saleCart.reduce((sum, l) => sum + (Number(l.quantity) || 0), 0);
  const saleCartTotal = saleCart.reduce((sum, l) => {
    const qty = Number(l.quantity);
    const price = Number(l.unitPrice);
    return sum + (Number.isFinite(qty) ? qty : 0) * (Number.isFinite(price) ? price : 0);
  }, 0);

  const confirmSaleCart = async () => {
    if (saleCart.length === 0) return;
    const lines = saleCart.map((l) => ({
      productId: l.productId,
      quantity: Number(l.quantity),
      unitPrice: Number(l.unitPrice),
    }));
    const invalidLine = saleCart.find((l) => {
      const qty = Number(l.quantity);
      const price = Number(l.unitPrice);
      return !Number.isFinite(qty) || qty < 1 || !Number.isFinite(price) || price <= 0;
    });
    if (invalidLine) {
      setSaleCartError(`Cantitate/preț invalid pentru "${invalidLine.name}".`);
      return;
    }
    const overStock = saleCart.find((l) => Number(l.quantity) > l.stockQuantity);
    if (overStock) {
      setSaleCartError(`Stoc insuficient pentru "${overStock.name}" — doar ${overStock.stockQuantity} buc.`);
      return;
    }
    setSaleCartBusy(true);
    setSaleCartError(null);
    try {
      await orderService.adminSale(lines);
      invalidateListCache(LIST_CACHE_NS);
      showToast('Vânzare înregistrată cu succes!', 'success');
      clearSaleCart();
      setSaleCartOpen(false);
      load();
    } catch (err) {
      setSaleCartError(err.response?.data?.message || 'Vânzarea a eșuat.');
    } finally {
      setSaleCartBusy(false);
    }
  };

  // ---- Quick preview (feature: previzualizare produs) ----
  const openPreview = (p) => {
    setPreviewProduct(p);
    setPreviewLoading(true);
    setPreviewHistory([]);
    setPreviewHistoryLoading(true);
    adminService
      .getAdminProduct(p.id)
      .then((detail) => setPreviewProduct(detail))
      .catch(() => {})
      .finally(() => setPreviewLoading(false));
    // Recent audit trail for this product — price/stock changes, image edits,
    // activate/deactivate. Best-effort: the preview still works if this fails.
    adminService
      .listAuditLogs({ entityType: 'Product', entityId: p.id, size: 8 })
      .then((data) => setPreviewHistory(data.content || []))
      .catch(() => setPreviewHistory([]))
      .finally(() => setPreviewHistoryLoading(false));
  };

  const closePreview = () => {
    setPreviewProduct(null);
    setPreviewLoading(false);
    setPreviewHistory([]);
    setPreviewHistoryLoading(false);
  };

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setImageFile(null);
    setImages([]);
    setImgError(null);
    setConfirmDeleteImageId(null);
    setError(null);
    setModalOpen(true);
  };

  const openEdit = (p) => {
    setEditing(p);
    setForm({
      name: p.name,
      description: p.description || '',
      price: p.price,
      purchasePrice: p.purchasePrice ?? '',
      stockQuantity: p.stockQuantity,
      category: p.category || '',
      subcategory: p.subcategory || '',
      brand: p.brand || '',
      sku: p.sku || '',
      imageUrl: p.imageUrl || '',
    });
    setImageFile(null);
    setImages(p.images || []);
    setImgError(null);
    setConfirmDeleteImageId(null);
    setError(null);
    setModalOpen(true);
    // Fetch full admin detail for images + the exact purchase price.
    adminService
      .getAdminProduct(p.id)
      .then((detail) => {
        setImages(detail.images || []);
        setForm((f) => ({ ...f, purchasePrice: detail.purchasePrice ?? '' }));
      })
      .catch(() => {});
  };

  // ---- Image gallery handlers (feature #5) ----
  const validateFiles = (fileList) => {
    const files = Array.from(fileList || []);
    const valid = [];
    for (const f of files) {
      if (!ALLOWED_TYPES.includes(f.type)) {
        setImgError(`Format neacceptat: ${f.name}. Doar JPG, PNG, WebP.`);
        continue;
      }
      if (f.size > MAX_BYTES) {
        setImgError(`${f.name} depășește 5 MB.`);
        continue;
      }
      valid.push(f);
    }
    return valid;
  };

  const handleImageFiles = async (fileList) => {
    if (!editing) return;
    setImgError(null);
    const valid = validateFiles(fileList);
    if (valid.length === 0) return;
    setImgBusy(true);
    try {
      const detail = await productService.uploadProductImages(editing.id, valid);
      if (detail) syncGallery(detail);
    } catch (err) {
      setImgError(err.response?.data?.message || 'Încărcarea imaginilor a eșuat.');
    } finally {
      setImgBusy(false);
    }
  };

  // Delete needs a second click to confirm (mirrors the double-confirm pattern
  // used for deleting products, scaled down since a single image is low-stakes
  // but still irreversible on Cloudinary).
  const askDeleteImage = (imageId) => {
    setImgError(null);
    setConfirmDeleteImageId((cur) => (cur === imageId ? cur : imageId));
  };

  const cancelDeleteImage = () => setConfirmDeleteImageId(null);

  const handleDeleteImage = async (imageId) => {
    if (!editing) return;
    setImgBusy(true);
    setImgError(null);
    try {
      const detail = await productService.deleteProductImage(editing.id, imageId);
      syncGallery(detail);
    } catch (err) {
      setImgError(err.response?.data?.message || 'Ștergerea imaginii a eșuat.');
    } finally {
      setImgBusy(false);
      setConfirmDeleteImageId(null);
    }
  };

  // ---- Reorder gallery (drag & drop, Task 4) ----
  const handleImageDragStart = (imageId) => {
    setDragImageId(imageId);
  };

  const handleImageDragOver = (e, imageId) => {
    e.preventDefault();
    if (imageId !== dragImageId) setDragOverImageId(imageId);
  };

  const handleImageDragEnd = () => {
    setDragImageId(null);
    setDragOverImageId(null);
  };

  const handleImageDrop = async (e, targetImageId) => {
    e.preventDefault();
    setDragOverImageId(null);
    if (!editing || dragImageId == null || dragImageId === targetImageId) {
      setDragImageId(null);
      return;
    }
    const fromIndex = images.findIndex((i) => i.id === dragImageId);
    const toIndex = images.findIndex((i) => i.id === targetImageId);
    setDragImageId(null);
    if (fromIndex === -1 || toIndex === -1) return;

    const reordered = [...images];
    const [moved] = reordered.splice(fromIndex, 1);
    reordered.splice(toIndex, 0, moved);
    const previous = images;
    setImages(reordered); // optimistic — snappy drag & drop feel
    try {
      const detail = await productService.reorderProductImages(
        editing.id,
        reordered.map((i) => i.id)
      );
      syncGallery(detail);
    } catch (err) {
      setImages(previous); // revert on failure
      setImgError(err.response?.data?.message || 'Reordonarea imaginilor a eșuat.');
    }
  };

  const handleSetPrimary = async (imageId) => {
    if (!editing) return;
    setImgBusy(true);
    setImgError(null);
    try {
      const detail = await productService.setPrimaryImage(editing.id, imageId);
      syncGallery(detail);
    } catch (err) {
      setImgError(err.response?.data?.message || 'Setarea imaginii principale a eșuat.');
    } finally {
      setImgBusy(false);
    }
  };

  // Keep the gallery state and the cover-URL field in sync with the server,
  // so a later "Salvează" doesn't overwrite a freshly-set primary image.
  const syncGallery = (detail) => {
    setImages(detail.images || []);
    setForm((f) => ({ ...f, imageUrl: detail.imageUrl || '' }));
  };

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSaving(true);
    try {
      const payload = {
        ...form,
        price: Number(form.price),
        stockQuantity: Number(form.stockQuantity),
        purchasePrice: form.purchasePrice === '' ? null : Number(form.purchasePrice),
      };
      let saved;
      if (editing) {
        saved = await productService.update(editing.id, payload);
      } else {
        saved = await productService.create(payload);
      }
      // New product with a chosen file → upload it to Cloudinary as first image.
      if (imageFile && !editing) {
        await productService.uploadProductImages(saved.id, [imageFile]);
      }
      setModalOpen(false);
      showToast(editing ? 'Produs actualizat.' : 'Produs creat.', 'success');
      invalidateListCache(LIST_CACHE_NS);
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Salvarea a eșuat.');
    } finally {
      setSaving(false);
    }
  };

  /**
   * Downloads the stock list as .xlsx. The current search term is sent along,
   * so what lands in the file is what the operator is looking at — filter the
   * table first and the export narrows with it.
   */
  const doExport = async () => {
    setExporting(true);
    try {
      const blob = await adminService.exportProducts({ search: search || undefined });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'produse.xlsx';
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      showToast('Export finalizat.', 'success');
    } catch (err) {
      showToast(err.response?.data?.message || 'Exportul a eșuat.', 'error');
    } finally {
      setExporting(false);
    }
  };

  // ---- Deletion (single or batch) — always behind two confirmations ----

  /** Opens the confirmation flow for one product or for the whole selection. */
  const askDelete = (items) => {
    if (!items || items.length === 0) return;
    setPending(items);
    setDeleteWord('');
    setDeleteError(null);
    setDeleteStep(1);
  };

  const closeDelete = () => {
    setDeleteStep(0);
    setPending([]);
    setDeleteWord('');
    setDeleteError(null);
  };

  /**
   * Runs only after both confirmation steps have been cleared. A single item
   * still goes through the dedicated endpoint; anything larger uses the batch
   * endpoint so the whole set is removed in one transaction. Any product that
   * comes back deactivated instead of deleted (it has order/purchase history)
   * is immediately offered through the separate, more severe force-delete
   * flow below — never executed automatically, only opened for review.
   */
  const confirmDelete = async () => {
    if (deleteWord.trim().toUpperCase() !== DELETE_KEYWORD) {
      setDeleteError(`Scrie exact ${DELETE_KEYWORD} pentru a confirma.`);
      return;
    }
    setDeleteBusy(true);
    setDeleteError(null);
    try {
      const ids = pending.map((p) => p.id);
      let toastMessage;
      let deactivatedCandidates = [];

      // A produs cu comenzi sau achiziții înregistrate nu poate fi șters
      // definitiv fără să strice istoricul facturilor — backend-ul îl
      // dezactivează în schimb și ne spune exact ce s-a întâmplat, ca să nu
      // afișăm un mesaj generic care ascunde diferența dintre „șters” și
      // „dezactivat”. Produsele dezactivate astfel sunt oferite mai jos
      // pentru eliminare definitivă, opțional și ireversibil.
      if (ids.length === 1) {
        const result = await productService.remove(ids[0]);
        toastMessage = result.message;
        if (result.data?.deleted === false) {
          deactivatedCandidates = pending;
        }
      } else {
        const result = await productService.bulkRemove(ids);
        const parts = [];
        if (result.deleted > 0) {
          parts.push(result.deleted === 1 ? '1 produs șters' : `${result.deleted} produse șterse`);
        }
        if (result.deactivated?.length > 0) {
          parts.push(
            result.deactivated.length === 1
              ? '1 produs dezactivat (are comenzi/achiziții înregistrate)'
              : `${result.deactivated.length} produse dezactivate (au comenzi/achiziții înregistrate)`
          );
          deactivatedCandidates = pending.filter((p) => result.deactivated.includes(p.id));
        }
        toastMessage = parts.length > 0 ? `${parts.join(', ')}.` : 'Niciun produs nu a fost modificat.';
      }
      setSelectedIds((prev) => {
        const next = new Set(prev);
        ids.forEach((id) => next.delete(id));
        return next;
      });
      closeDelete();
      showToast(toastMessage, 'success');
      invalidateListCache(LIST_CACHE_NS);
      // Stepping back a page keeps the operator on a populated page when the
      // last rows of the final page were just removed. A product that was
      // deactivated instead of deleted still occupies its row, so this only
      // steps back when the whole selection actually left the table.
      if (products.length === ids.length && page > 0) {
        setPage((p) => p - 1);
      } else {
        load();
      }
      if (deactivatedCandidates.length > 0) {
        setForceCandidates(deactivatedCandidates);
        setForceWord('');
        setForceError(null);
        setForceStep(1);
      }
    } catch (err) {
      setDeleteError(err.response?.data?.message || 'Ștergerea a eșuat.');
    } finally {
      setDeleteBusy(false);
    }
  };

  // ---- Force-delete (permanently removes history too) — opt-in override ----

  const closeForceDelete = () => {
    setForceStep(0);
    setForceCandidates([]);
    setForceWord('');
    setForceError(null);
  };

  /**
   * Runs after the operator types FORCE_DELETE_KEYWORD to confirm removing
   * `forceCandidates` permanently, including their order/purchase history
   * rows. Unlike confirmDelete, there is no fallback here: every candidate
   * offered at this step already has confirmed sales history, so the
   * backend always hard-deletes it — this endpoint never deactivates.
   */
  const confirmForceDelete = async () => {
    if (forceWord.trim().toUpperCase() !== FORCE_DELETE_KEYWORD) {
      setForceError(`Scrie exact ${FORCE_DELETE_KEYWORD} pentru a confirma.`);
      return;
    }
    setForceBusy(true);
    setForceError(null);
    try {
      const ids = forceCandidates.map((p) => p.id);
      let toastMessage;
      if (ids.length === 1) {
        const result = await productService.forceRemove(ids[0]);
        toastMessage = result.message;
      } else {
        const result = await productService.bulkForceRemove(ids);
        const lineParts = [];
        if (result.orderItemsRemoved > 0) {
          lineParts.push(
            result.orderItemsRemoved === 1 ? '1 linie de comandă' : `${result.orderItemsRemoved} linii de comandă`
          );
        }
        if (result.purchaseItemsRemoved > 0) {
          lineParts.push(
            result.purchaseItemsRemoved === 1
              ? '1 linie de achiziție'
              : `${result.purchaseItemsRemoved} linii de achiziție`
          );
        }
        toastMessage =
          `${result.deleted} ${result.deleted === 1 ? 'produs șters definitiv' : 'produse șterse definitiv'}` +
          (lineParts.length > 0 ? `, împreună cu ${lineParts.join(' și ')} eliminate ireversibil.` : '.');
      }
      setSelectedIds((prev) => {
        const next = new Set(prev);
        ids.forEach((id) => next.delete(id));
        return next;
      });
      closeForceDelete();
      showToast(toastMessage, 'success');
      invalidateListCache(LIST_CACHE_NS);
      if (products.length === ids.length && page > 0) {
        setPage((p) => p - 1);
      } else {
        load();
      }
    } catch (err) {
      setForceError(err.response?.data?.message || 'Ștergerea definitivă a eșuat.');
    } finally {
      setForceBusy(false);
    }
  };

  // ---- Import ----
  const openImport = () => {
    setImportFile(null);
    setImportReport(null);
    setImportError(null);
    setImportDone(null);
    setSyncMode(false);
    setRestockMode(false);
    setImportOpen(true);
  };

  const runImport = async (dryRun) => {
    if (!importFile) {
      setImportError('Alege întâi un fișier .xlsx.');
      return;
    }
    setImportBusy(true);
    setImportError(null);
    try {
      const report = syncMode
        ? await productService.syncPurchasePrices(importFile, dryRun)
        : await productService.importProducts(importFile, dryRun, restockMode);
      if (dryRun) {
        setImportReport(report);
      } else {
        setImportDone(report);
        setImportReport(report);
        showToast('Import finalizat.', 'success');
        invalidateListCache(LIST_CACHE_NS);
        load();
      }
    } catch (err) {
      setImportError(err.response?.data?.message || 'Importul a eșuat.');
    } finally {
      setImportBusy(false);
    }
  };

  return (
    <div>
      <AdminNav />

      <SectionHeader
        eyebrow="Catalog"
        title="Management produse"
        subtitle={
          totalElements > 0
            ? `${totalElements} ${totalElements === 1 ? 'produs' : 'produse'} în inventar.`
            : 'Inventarul, prețurile și galeriile de imagini.'
        }
        as="h1"
        action={
          <div className="flex flex-wrap gap-2">
            <NeonButton
              variant="ghost"
              onClick={doExport}
              disabled={exporting}
              charging={exporting}
              icon={<GeoIcon name="document" className="h-4 w-4" accent="currentColor" />}
            >
              {exporting ? 'Se exportă…' : 'Export Excel'}
            </NeonButton>
            <NeonButton
              variant="ghost"
              onClick={openImport}
              icon={<GeoIcon name="layers" className="h-4 w-4" accent="currentColor" />}
            >
              Import Excel
            </NeonButton>
            <NeonButton
              onClick={openCreate}
              icon={<GeoIcon name="sparkle" className="h-4 w-4" accent="currentColor" />}
            >
              Produs nou
            </NeonButton>
          </div>
        }
      />

      {/* XXII — control strip: search stays permanently visible because it is
          used constantly; everything else folds into the slide-in panel. */}
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <div className="min-w-[15rem] flex-1 sm:max-w-sm">
          <HoloInput
            label="Caută produse"
            placeholder="Nume, brand sau SKU…"
            icon={<GeoIcon name="search" className="h-4 w-4" accent="currentColor" />}
            value={search}
            onChange={(e) => {
              setPage(0);
              setSearch(e.target.value);
            }}
          />
        </div>

        <NeonButton
          variant={filtersOpen ? 'primary' : 'ghost'}
          onClick={() => setFiltersOpen((open) => !open)}
          icon={<GeoIcon name="layers" className="h-4 w-4" accent="currentColor" />}
          aria-expanded={filtersOpen}
          aria-controls="prod-filter-panel"
        >
          Filtre{quickFilter ? ' · 1' : ''}
        </NeonButton>

        {/* Surface switch — table for bulk work, grid for the 3D catalogue. */}
        <div
          role="group"
          aria-label="Mod de afișare"
          className="flex items-center gap-1 rounded-xl border border-[rgba(255,255,255,0.12)] bg-[rgba(255,255,255,0.04)] p-1"
        >
          {[
            { mode: 'table', icon: 'layers', label: 'Tabel' },
            { mode: 'grid', icon: 'grid', label: 'Carduri' },
          ].map((v) => (
            <button
              key={v.mode}
              type="button"
              onClick={() => changeViewMode(v.mode)}
              aria-pressed={viewMode === v.mode}
              title={`Afișare ${v.label.toLowerCase()}`}
              className={`flex h-9 items-center gap-1.5 rounded-lg px-3 text-xs font-semibold transition-all duration-xx ease-xx ${
                viewMode === v.mode
                  ? 'border border-[rgba(34,232,245,0.5)] bg-[rgba(34,232,245,0.14)] text-[color:var(--xx-ink)] shadow-[0_0_24px_-10px_rgba(34,232,245,0.9)]'
                  : 'border border-transparent text-[color:var(--xx-ink-muted)] hover:text-[color:var(--xx-ink)]'
              }`}
            >
              <GeoIcon name={v.icon} className="h-4 w-4" accent="currentColor" />
              <span className="hidden sm:inline">{v.label}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Slide-in filter panel (TASK 6 — materialize) */}
      {filtersOpen && (
        <div
          id="prod-filter-panel"
          className="mb-4 animate-xx-materialize rounded-[1rem] border border-[rgba(122,60,255,0.32)] bg-[rgba(122,60,255,0.07)] p-4"
        >
          <div className="flex flex-wrap items-end gap-6">
            <fieldset className="min-w-[16rem]">
              <legend className="mb-2 text-[0.65rem] font-semibold uppercase tracking-[0.16em] xx-ink-dim">
                Filtre rapide
              </legend>
              <div className="flex flex-wrap gap-2">
                {QUICK_FILTERS.map((f) => (
                  <button
                    key={f.key ?? 'all'}
                    type="button"
                    onClick={() => chooseQuickFilter(f.key)}
                    aria-pressed={quickFilter === f.key}
                    className={`flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-semibold transition-all duration-xx ease-xx ${
                      quickFilter === f.key
                        ? 'border-[rgba(34,232,245,0.55)] bg-[rgba(34,232,245,0.14)] text-[color:var(--xx-ink)] shadow-[0_0_26px_-10px_rgba(34,232,245,0.9)]'
                        : 'border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] hover:border-[rgba(122,60,255,0.5)] hover:text-[color:var(--xx-ink)]'
                    }`}
                  >
                    <GeoIcon name={f.icon} className="h-3.5 w-3.5" accent="currentColor" />
                    {f.label}
                  </button>
                ))}
              </div>
            </fieldset>

            <div className="w-32">
              <HoloInput
                as="select"
                label="Pe pagină"
                value={pageSize}
                onChange={(e) => changePageSize(Number(e.target.value))}
              >
                {PAGE_SIZES.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </HoloInput>
            </div>
          </div>
        </div>
      )}

      {/* Batch action bar — only present while something is ticked */}
      {selectedIds.size > 0 && (
        <div className="mb-3 flex flex-wrap items-center gap-3 rounded-[1rem] border border-[rgba(34,232,245,0.35)] bg-[rgba(34,232,245,0.08)] px-4 py-2.5 text-sm">
          <NeonBadge tone="aqua" pulse>
            {selectedIds.size} {selectedIds.size === 1 ? 'produs selectat' : 'produse selectate'}
          </NeonBadge>
          <button
            type="button"
            className="text-xs font-semibold xx-ink-muted transition-colors duration-xx hover:text-[color:var(--xx-ink)]"
            onClick={() => setSelectedIds(new Set())}
          >
            Deselectează tot
          </button>
          <div className="ml-auto flex flex-wrap gap-2">
            <NeonButton
              size="sm"
              disabled={bulkActiveBusy}
              charging={bulkActiveBusy}
              onClick={() => bulkSetActive(true)}
              icon={<GeoIcon name="check" className="h-4 w-4" accent="currentColor" />}
            >
              {bulkActiveBusy ? 'Se activează…' : 'Activează selectate'}
            </NeonButton>
            <NeonButton
              size="sm"
              variant="secondary"
              disabled={bulkActiveBusy}
              onClick={() => bulkSetActive(false)}
              icon={<GeoIcon name="clock" className="h-4 w-4" accent="currentColor" />}
            >
              {bulkActiveBusy ? 'Se dezactivează…' : 'Dezactivează selectate'}
            </NeonButton>
            <NeonButton
              size="sm"
              variant="danger"
              onClick={() => askDelete(selectedProducts)}
              icon={<GeoIcon name="trash" className="h-4 w-4" accent="currentColor" />}
            >
              Șterge selectate
            </NeonButton>
          </div>
        </div>
      )}

      {loading ? (
        <HoloLoader label="Se încarcă produsele" />
      ) : products.length === 0 ? (
        <div className="card card-static p-10 text-center">
          <p className="text-sm xx-ink-muted">
            {search || quickFilter
              ? 'Niciun produs nu corespunde filtrelor curente.'
              : 'Catalogul este gol. Adaugă primul produs.'}
          </p>
        </div>
      ) : viewMode === 'grid' ? (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4">
          {products.map((p) => (
            <ProductTile
              key={p.id}
              p={p}
              selected={selectedIds.has(p.id)}
              inCart={saleCart.find((l) => l.productId === p.id)?.quantity || 0}
              activeBusy={activeBusyId === p.id}
              onToggle={() => toggleOne(p.id)}
              onPreview={() => openPreview(p)}
              onEdit={() => openEdit(p)}
              onSell={() => addToSaleCart(p)}
              onToggleActive={() => toggleActive(p)}
              onDelete={() => askDelete([p])}
            />
          ))}
        </div>
      ) : (
        <div className="card overflow-x-auto">
          <table className="min-w-full divide-y divide-[rgba(255,255,255,0.08)] text-sm">
            <thead className="text-left">
              <tr className="bg-[rgba(255,255,255,0.03)]">
                <th className="w-10 px-4 py-3">
                  <input
                    ref={selectAllRef}
                    type="checkbox"
                    className="h-4 w-4 cursor-pointer rounded accent-[#22e8f5]"
                    checked={allOnPageSelected}
                    onChange={toggleAllOnPage}
                    aria-label="Selectează toate produsele de pe această pagină"
                  />
                </th>
                <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Produs</th>
                <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Categorie</th>
                <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">
                  Preț vânzare
                </th>
                <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Achiziție</th>
                <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Profit</th>
                <th className="px-4 py-3 text-xs font-semibold uppercase tracking-[0.14em]">Stoc</th>
                <th className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-[0.14em]">
                  Acțiuni
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[rgba(255,255,255,0.07)]">
              {products.map((p) => (
                <tr
                  key={p.id}
                  className={
                    selectedIds.has(p.id)
                      ? 'bg-[rgba(34,232,245,0.08)] shadow-[inset_2px_0_0_0_rgba(34,232,245,0.8)]'
                      : ''
                  }
                >
                  <td className="px-4 py-3">
                    <input
                      type="checkbox"
                      className="h-4 w-4 cursor-pointer rounded accent-[#22e8f5]"
                      checked={selectedIds.has(p.id)}
                      onChange={() => toggleOne(p.id)}
                      aria-label={`Selectează ${p.name}`}
                    />
                  </td>
                  <td className="px-4 py-3">
                    <button
                      type="button"
                      onClick={() => openPreview(p)}
                      className="group flex items-center gap-3 text-left"
                      title="Previzualizează"
                    >
                      <img
                        src={resolveImage(p.imageUrl)}
                        alt={p.name}
                        loading="lazy"
                        className="h-10 w-10 rounded-lg border border-[rgba(255,255,255,0.12)] object-cover transition-all duration-xx ease-xx group-hover:border-[rgba(34,232,245,0.5)] group-hover:shadow-[0_0_22px_-6px_rgba(34,232,245,0.8)]"
                      />
                      <div>
                        <p className="flex items-center gap-1.5 font-medium text-[color:var(--xx-ink)] transition-colors duration-xx group-hover:text-[color:var(--xx-cyan)]">
                          {p.name}
                          {!p.active && (
                            <span className="rounded-full border border-[rgba(255,255,255,0.14)] bg-[rgba(255,255,255,0.06)] px-2 py-0.5 text-[0.65rem] font-semibold text-[#a8b0d4]">
                              Inactiv
                            </span>
                          )}
                        </p>
                        <p className="text-xs xx-ink-dim">{p.brand}</p>
                      </div>
                    </button>
                  </td>
                  <td className="px-4 py-3 text-xs xx-ink-muted">
                    {p.category}
                    {p.subcategory ? <span className="xx-ink-dim"> · {p.subcategory}</span> : null}
                  </td>
                  <td className="px-4 py-3 font-medium">
                    {editingCell?.id === p.id && editingCell.field === 'price' ? (
                      <input
                        type="number"
                        step="0.01"
                        className="input w-28 py-1"
                        autoFocus
                        aria-label={`Preț de vânzare — ${p.name}`}
                        value={editValue}
                        disabled={editBusy}
                        onChange={(e) => setEditValue(e.target.value)}
                        onBlur={() => saveEdit(p)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') saveEdit(p);
                          if (e.key === 'Escape') cancelEdit();
                        }}
                      />
                    ) : (
                      <button
                        type="button"
                        onClick={() => startEdit(p, 'price')}
                        className="rounded-lg border border-transparent px-2 py-1 text-[color:var(--xx-ink)] transition-all duration-xx ease-xx hover:border-[rgba(34,232,245,0.45)] hover:bg-[rgba(34,232,245,0.1)]"
                        title="Editează prețul"
                      >
                        {formatPrice(p.price)}
                      </button>
                    )}
                  </td>
                  <td className="px-4 py-3 text-xs xx-ink-muted">
                    {p.purchasePrice != null ? formatPrice(p.purchasePrice) : '—'}
                  </td>
                  <td className="px-4 py-3">
                    {p.profit != null ? (
                      <span className="font-semibold text-[#7ee9bd]">
                        {formatPrice(p.profit)}
                        {p.marginPercent != null && (
                          <span className="ml-1 text-xs font-normal xx-ink-dim">
                            · {p.marginPercent}%
                          </span>
                        )}
                      </span>
                    ) : (
                      <span className="xx-ink-dim">—</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    {editingCell?.id === p.id && editingCell.field === 'stockQuantity' ? (
                      <input
                        type="number"
                        className="input w-20 py-1"
                        autoFocus
                        aria-label={`Stoc — ${p.name}`}
                        value={editValue}
                        disabled={editBusy}
                        onChange={(e) => setEditValue(e.target.value)}
                        onBlur={() => saveEdit(p)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') saveEdit(p);
                          if (e.key === 'Escape') cancelEdit();
                        }}
                      />
                    ) : (
                      <button
                        type="button"
                        onClick={() => startEdit(p, 'stockQuantity')}
                        title="Editează stocul"
                        aria-label={`Stoc ${p.stockQuantity} — editează`}
                      >
                        <span
                          className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${
                            p.stockQuantity > 0
                              ? 'border border-[rgba(31,172,121,0.42)] bg-[rgba(31,172,121,0.16)] text-[#93e9c4]'
                              : 'border border-[rgba(184,47,60,0.42)] bg-[rgba(184,47,60,0.16)] text-[#ffb3bd]'
                          }`}
                        >
                          <span aria-hidden="true">{p.stockQuantity > 0 ? '✓' : '✕'}</span>
                          {p.stockQuantity}
                        </span>
                      </button>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-1.5">
                      <button
                        type="button"
                        onClick={() => addToSaleCart(p)}
                        disabled={p.stockQuantity <= 0}
                        title={
                          p.stockQuantity <= 0
                            ? 'Stoc epuizat'
                            : 'Adaugă în vânzare — poți adăuga mai multe produse înainte de a finaliza'
                        }
                        aria-label={`Adaugă ${p.name} în vânzare`}
                        className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-[rgba(31,172,121,0.45)] bg-[rgba(31,172,121,0.14)] px-2.5 text-xs font-semibold text-[#93e9c4] transition-all duration-xx ease-xx hover:border-[rgba(31,172,121,0.75)] hover:shadow-[0_0_24px_-8px_rgba(31,172,121,0.95)] disabled:cursor-not-allowed disabled:border-[rgba(255,255,255,0.1)] disabled:bg-transparent disabled:text-[color:var(--xx-ink-dim)] disabled:shadow-none"
                      >
                        <GeoIcon name="coins" className="h-4 w-4" accent="currentColor" />
                        <span className="hidden xl:inline">Vândut</span>
                        {saleCart.some((l) => l.productId === p.id) && (
                          <span className="rounded-full bg-[rgba(255,255,255,0.18)] px-1.5 text-[0.65rem] font-bold">
                            {saleCart.find((l) => l.productId === p.id).quantity}
                          </span>
                        )}
                      </button>
                      <button
                        type="button"
                        onClick={() => openPreview(p)}
                        title="Previzualizează produsul"
                        aria-label={`Previzualizează ${p.name}`}
                        className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(34,232,245,0.5)] hover:text-[color:var(--xx-cyan)]"
                      >
                        <GeoIcon name="zoom" className="h-4 w-4" accent="currentColor" />
                      </button>
                      <button
                        type="button"
                        onClick={() => openEdit(p)}
                        title="Editează produsul"
                        aria-label={`Editează ${p.name}`}
                        className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(46,123,255,0.5)] hover:text-[#7fb0ff]"
                      >
                        <GeoIcon name="gear" className="h-4 w-4" accent="currentColor" />
                      </button>
                      <button
                        type="button"
                        onClick={() => toggleActive(p)}
                        disabled={activeBusyId === p.id}
                        title={p.active ? 'Dezactivează produsul' : 'Activează produsul'}
                        aria-label={`${p.active ? 'Dezactivează' : 'Activează'} ${p.name}`}
                        className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(122,60,255,0.55)] hover:text-[#c4a8ff] disabled:opacity-40"
                      >
                        <GeoIcon
                          name={p.active ? 'clock' : 'bolt'}
                          className="h-4 w-4"
                          accent="currentColor"
                        />
                      </button>
                      <button
                        type="button"
                        onClick={() => askDelete([p])}
                        title="Șterge produsul"
                        aria-label={`Șterge ${p.name}`}
                        className="grid h-8 w-8 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(255,84,112,0.55)] hover:text-[color:var(--xx-red)]"
                      >
                        <GeoIcon name="trash" className="h-4 w-4" accent="currentColor" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pagination page={page} totalPages={totalPages} onChange={setPage} />

      {/* Feature #10 — "VÂNDUT" sale-cart floating bar. Stays visible (fixed to the
          bottom of the viewport) while the operator searches/paginates to add more
          products, so building "3 of this, 1 of that" doesn't lose progress. */}
      {saleCart.length > 0 && (
        <div className="fixed inset-x-0 bottom-0 z-40 animate-xx-materialize border-t border-[rgba(31,172,121,0.4)] bg-[rgba(9,11,28,0.86)] px-4 py-3 backdrop-blur-xl shadow-[0_-18px_50px_-20px_rgba(31,172,121,0.55)]">
          <div className="mx-auto flex max-w-[1680px] flex-wrap items-center gap-3">
            <span className="flex items-center gap-2 text-sm font-medium text-[color:var(--xx-ink)]">
              <GeoIcon name="cart" className="h-5 w-5" accent="#3ddc9a" />
              {saleCartCount} {saleCartCount === 1 ? 'produs' : 'produse'} · {saleCart.length}{' '}
              {saleCart.length === 1 ? 'articol' : 'articole'} distincte
            </span>
            <span
              className="text-lg font-bold text-[color:var(--xx-ink)]"
              style={{ textShadow: '0 0 26px rgba(31,172,121,0.5)' }}
            >
              {formatPrice(saleCartTotal)}
            </span>
            <button
              type="button"
              className="text-xs font-semibold xx-ink-muted transition-colors duration-xx hover:text-[color:var(--xx-red)]"
              onClick={clearSaleCart}
            >
              Golește coșul
            </button>
            <NeonButton
              className="ml-auto"
              onClick={() => setSaleCartOpen(true)}
              iconRight={<GeoIcon name="arrow" className="h-4 w-4" accent="currentColor" />}
            >
              Finalizează vânzarea
            </NeonButton>
          </div>
        </div>
      )}

      {/* Sale-cart review/finalize modal — every distinct product added, with an
          editable quantity and price per line (feature #10, multi-product). */}
      <Modal
        open={saleCartOpen}
        title={`VÂNDUT — ${saleCart.length} ${saleCart.length === 1 ? 'produs' : 'produse'}`}
        onClose={() => !saleCartBusy && setSaleCartOpen(false)}
        maxWidth="max-w-lg"
      >
        <div className="space-y-4">
          {saleCart.length === 0 ? (
            <p className="py-6 text-center text-sm xx-ink-muted">
              Coșul e gol. Închide fereastra și adaugă produse cu butonul „Vândut” din tabel.
            </p>
          ) : (
            <div className="max-h-[50vh] space-y-3 overflow-y-auto pr-1">
              {saleCart.map((line) => {
                const lineQty = Number(line.quantity);
                const lineOverStock = Number.isFinite(lineQty) && lineQty > line.stockQuantity;
                return (
                  <div
                    key={line.productId}
                    className="flex items-center gap-3 rounded-xl border border-[rgba(255,255,255,0.1)] bg-[rgba(255,255,255,0.04)] p-2.5"
                  >
                    <img
                      src={resolveImage(line.imageUrl)}
                      alt={line.name}
                      className="h-12 w-12 shrink-0 rounded-lg border border-[rgba(255,255,255,0.12)] object-cover"
                    />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium text-[color:var(--xx-ink)]">{line.name}</p>
                      <p className="text-xs xx-ink-dim">Stoc: {line.stockQuantity} buc.</p>
                    </div>
                    <input
                      type="number"
                      min="1"
                      step="1"
                      aria-label={`Cantitate — ${line.name}`}
                      aria-invalid={lineOverStock || undefined}
                      className={`input w-16 py-1 text-center ${
                        lineOverStock
                          ? 'border-[rgba(255,84,112,0.65)] focus:border-[rgba(255,84,112,0.9)]'
                          : ''
                      }`}
                      value={line.quantity}
                      disabled={saleCartBusy}
                      onChange={(e) => updateSaleCartLine(line.productId, 'quantity', e.target.value)}
                    />
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      aria-label={`Preț per bucată — ${line.name}`}
                      className="input w-24 py-1"
                      value={line.unitPrice}
                      disabled={saleCartBusy}
                      onChange={(e) => updateSaleCartLine(line.productId, 'unitPrice', e.target.value)}
                    />
                    <span className="w-20 shrink-0 text-right text-sm font-semibold text-[color:var(--xx-ink)]">
                      {formatPrice((Number(line.quantity) || 0) * (Number(line.unitPrice) || 0))}
                    </span>
                    <button
                      type="button"
                      onClick={() => removeFromSaleCart(line.productId)}
                      disabled={saleCartBusy}
                      className="grid h-8 w-8 shrink-0 place-items-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[color:var(--xx-ink-muted)] transition-all duration-xx ease-xx hover:border-[rgba(255,84,112,0.55)] hover:text-[color:var(--xx-red)] disabled:opacity-40"
                      title="Elimină din vânzare"
                      aria-label={`Elimină ${line.name} din vânzare`}
                    >
                      <GeoIcon name="close" className="h-4 w-4" accent="currentColor" />
                    </button>
                  </div>
                );
              })}
            </div>
          )}

          {saleCartError && (
            <p
              role="alert"
              className="rounded-xl border border-[rgba(255,84,112,0.45)] bg-[rgba(255,84,112,0.12)] px-3 py-2 text-sm font-medium text-[#ffc2cc]"
            >
              {saleCartError}
            </p>
          )}

          <div className="flex items-center justify-between rounded-xl border border-[rgba(34,232,245,0.3)] bg-[rgba(34,232,245,0.08)] px-4 py-3">
            <span className="text-sm font-medium xx-ink-muted">Total ({saleCartCount} buc.)</span>
            <span
              className="text-lg font-bold text-[color:var(--xx-ink)]"
              style={{ textShadow: '0 0 26px rgba(34,232,245,0.45)' }}
            >
              {formatPrice(saleCartTotal)}
            </span>
          </div>

          <div className="flex justify-end gap-2 pt-1">
            <NeonButton variant="ghost" onClick={() => setSaleCartOpen(false)} disabled={saleCartBusy}>
              Continuă cumpărăturile
            </NeonButton>
            <NeonButton
              onClick={confirmSaleCart}
              disabled={saleCartBusy || saleCart.length === 0}
              charging={saleCartBusy}
              icon={<GeoIcon name="check" className="h-4 w-4" accent="currentColor" />}
            >
              {saleCartBusy ? 'Se înregistrează…' : 'Confirmă vânzarea'}
            </NeonButton>
          </div>
        </div>
      </Modal>

      {/* Delete confirmation — step 1: review what is about to be removed */}
      <Modal
        open={deleteStep === 1}
        title={pending.length === 1 ? 'Confirmă ștergerea' : `Confirmă ștergerea a ${pending.length} produse`}
        onClose={closeDelete}
        maxWidth="max-w-lg"
      >
        <p className="text-sm xx-ink-muted">
          Următoarele produse vor fi șterse definitiv, împreună cu imaginile lor. Operațiunea nu poate
          fi anulată.
        </p>
        <ul className="mt-3 max-h-60 space-y-1 overflow-y-auto rounded-xl border border-[rgba(255,84,112,0.3)] bg-[rgba(255,84,112,0.07)] p-3 text-sm text-[color:var(--xx-ink)]">
          {pending.map((p) => (
            <li key={p.id} className="flex items-center gap-2 truncate">
              <span aria-hidden="true" className="text-[color:var(--xx-red)]">
                ▪
              </span>
              {p.name}
            </li>
          ))}
        </ul>
        <div className="mt-5 flex justify-end gap-2">
          <NeonButton variant="ghost" onClick={closeDelete}>
            Anulează
          </NeonButton>
          <NeonButton
            variant="danger"
            onClick={() => setDeleteStep(2)}
            iconRight={<GeoIcon name="arrow" className="h-4 w-4" accent="currentColor" />}
          >
            Continuă
          </NeonButton>
        </div>
      </Modal>

      {/* Delete confirmation — step 2: type the keyword */}
      <Modal
        open={deleteStep === 2}
        title="Ultima confirmare"
        onClose={closeDelete}
        maxWidth="max-w-lg"
      >
        <div className="flex items-start gap-3 rounded-xl border border-[rgba(255,84,112,0.45)] bg-[rgba(255,84,112,0.1)] px-4 py-3 text-sm text-[#ffc2cc]">
          <GeoIcon name="alert" className="mt-0.5 h-5 w-5 shrink-0" accent="currentColor" />
          <span>
            Ești pe cale să ștergi definitiv{' '}
            <strong className="text-[color:var(--xx-ink)]">
              {pending.length} {pending.length === 1 ? 'produs' : 'produse'}
            </strong>
            . Pentru a confirma, scrie{' '}
            <strong className="font-mono text-[color:var(--xx-ink)]">{DELETE_KEYWORD}</strong> în câmpul
            de mai jos.
          </span>
        </div>
        {deleteError && (
          <div
            role="alert"
            className="mt-3 rounded-xl border border-[rgba(255,84,112,0.5)] bg-[rgba(255,84,112,0.16)] px-4 py-2 text-sm text-[#ffc2cc]"
          >
            {deleteError}
          </div>
        )}
        <div className="mt-3">
          <HoloInput
            label={`Scrie ${DELETE_KEYWORD} pentru a confirma`}
            value={deleteWord}
            onChange={(e) => setDeleteWord(e.target.value)}
            placeholder={DELETE_KEYWORD}
            autoComplete="off"
            status={
              deleteWord.trim() === ''
                ? null
                : deleteWord.trim().toUpperCase() === DELETE_KEYWORD
                ? 'valid'
                : 'invalid'
            }
          />
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <NeonButton variant="ghost" onClick={closeDelete}>
            Anulează
          </NeonButton>
          <NeonButton
            variant="danger"
            disabled={deleteBusy || deleteWord.trim().toUpperCase() !== DELETE_KEYWORD}
            charging={deleteBusy}
            onClick={confirmDelete}
            icon={<GeoIcon name="trash" className="h-4 w-4" accent="currentColor" />}
          >
            {deleteBusy ? 'Se șterge…' : 'Șterge definitiv'}
          </NeonButton>
        </div>
      </Modal>

      {/*
        Force-delete offer — shown automatically right after a normal delete
        leaves one or more products deactivated instead of removed (they have
        order/purchase history). Distinct keyword, distinct wording, distinct
        color emphasis from the normal delete modal above: this one also
        erases historical order/purchase line items and cannot be reached by
        muscle memory alone.
      */}
      <Modal
        open={forceStep === 1}
        title="Eliminare definitivă, inclusiv istoricul"
        onClose={closeForceDelete}
        maxWidth="max-w-lg"
      >
        <p className="text-sm xx-ink-muted">
          {forceCandidates.length === 1
            ? 'Următorul produs are comenzi sau achiziții înregistrate, așa că a fost dezactivat în loc să fie șters:'
            : `Următoarele ${forceCandidates.length} produse au comenzi sau achiziții înregistrate, așa că au fost dezactivate în loc să fie șterse:`}
        </p>
        <ul className="mt-3 max-h-40 space-y-1 overflow-y-auto rounded-xl border border-[rgba(255,84,112,0.3)] bg-[rgba(255,84,112,0.07)] p-3 text-sm text-[color:var(--xx-ink)]">
          {forceCandidates.map((p) => (
            <li key={p.id} className="flex items-center gap-2 truncate">
              <span aria-hidden="true" className="text-[color:var(--xx-red)]">
                ▪
              </span>
              {p.name}
            </li>
          ))}
        </ul>
        <div className="mt-3 flex items-start gap-3 rounded-xl border border-[rgba(255,84,112,0.45)] bg-[rgba(255,84,112,0.1)] px-4 py-3 text-sm text-[#ffc2cc]">
          <GeoIcon name="alert" className="mt-0.5 h-5 w-5 shrink-0" accent="currentColor" />
          <span>
            Poți să le elimini definitiv, împreună cu liniile de comandă și de achiziție care le
            referențiază. Comenzile și achizițiile afectate rămân, dar cu totalul recalculat fără acest
            produs — facturile care conțineau acest produs nu mai reflectă exact ce s-a vândut atunci.{' '}
            <strong className="text-[color:var(--xx-ink)]">Operațiunea nu poate fi anulată.</strong> Pentru
            a confirma, scrie <strong className="font-mono text-[color:var(--xx-ink)]">{FORCE_DELETE_KEYWORD}</strong>{' '}
            în câmpul de mai jos.
          </span>
        </div>
        {forceError && (
          <div
            role="alert"
            className="mt-3 rounded-xl border border-[rgba(255,84,112,0.5)] bg-[rgba(255,84,112,0.16)] px-4 py-2 text-sm text-[#ffc2cc]"
          >
            {forceError}
          </div>
        )}
        <div className="mt-3">
          <HoloInput
            label={`Scrie ${FORCE_DELETE_KEYWORD} pentru a confirma`}
            value={forceWord}
            onChange={(e) => setForceWord(e.target.value)}
            placeholder={FORCE_DELETE_KEYWORD}
            autoComplete="off"
            status={
              forceWord.trim() === ''
                ? null
                : forceWord.trim().toUpperCase() === FORCE_DELETE_KEYWORD
                ? 'valid'
                : 'invalid'
            }
          />
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <NeonButton variant="ghost" onClick={closeForceDelete}>
            Lasă dezactivat
          </NeonButton>
          <NeonButton
            variant="danger"
            disabled={forceBusy || forceWord.trim().toUpperCase() !== FORCE_DELETE_KEYWORD}
            charging={forceBusy}
            onClick={confirmForceDelete}
            icon={<GeoIcon name="trash" className="h-4 w-4" accent="currentColor" />}
          >
            {forceBusy ? 'Se elimină definitiv…' : 'Elimină definitiv, inclusiv istoricul'}
          </NeonButton>
        </div>
      </Modal>

      {/* Create / edit modal */}
      <Modal
        open={modalOpen}
        title={editing ? 'Editează produs' : 'Produs nou'}
        onClose={() => setModalOpen(false)}
        maxWidth="max-w-2xl"
      >
        {error && (
          <div
            role="alert"
            className="mb-4 rounded-xl border border-[rgba(255,84,112,0.45)] bg-[rgba(255,84,112,0.12)] px-4 py-2 text-sm text-[#ffc2cc]"
          >
            {error}
          </div>
        )}
        <form onSubmit={handleSubmit} className="space-y-3">
          <HoloInput label="Nume" name="name" value={form.name} onChange={handleChange} required />

          <HoloInput
            as="textarea"
            label="Descriere"
            name="description"
            className="min-h-[80px]"
            value={form.description}
            onChange={handleChange}
          />

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <HoloInput
              type="number"
              step="0.01"
              label="Preț vânzare (RON)"
              name="price"
              value={form.price}
              onChange={handleChange}
              required
            />
            <HoloInput
              type="number"
              step="0.01"
              label="Preț achiziție (RON)"
              hint="Vizibil doar administratorilor."
              name="purchasePrice"
              value={form.purchasePrice}
              onChange={handleChange}
            />
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <HoloInput
              type="number"
              label="Stoc"
              name="stockQuantity"
              value={form.stockQuantity}
              onChange={handleChange}
              required
            />
            <HoloInput label="Cod / SKU" name="sku" value={form.sku} onChange={handleChange} />
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <HoloInput label="Categorie" name="category" value={form.category} onChange={handleChange} />
            <HoloInput
              label="Subcategorie"
              name="subcategory"
              value={form.subcategory}
              onChange={handleChange}
            />
            <HoloInput label="Brand" name="brand" value={form.brand} onChange={handleChange} />
          </div>

          <HoloInput
            label="URL imagine"
            hint="Opțional — o galerie încărcată local are prioritate."
            name="imageUrl"
            value={form.imageUrl}
            onChange={handleChange}
          />

          {editing ? (
            <div>
              <p className="mb-1 text-xs font-semibold uppercase tracking-[0.16em] xx-ink-dim">
                Imagini produs
              </p>
              {imgError && (
                <div
                  role="alert"
                  className="mb-2 rounded-lg border border-[rgba(255,84,112,0.45)] bg-[rgba(255,84,112,0.12)] px-3 py-1.5 text-xs text-[#ffc2cc]"
                >
                  {imgError}
                </div>
              )}
              <div
                onDragOver={(e) => {
                  e.preventDefault();
                  setDragOver(true);
                }}
                onDragLeave={() => setDragOver(false)}
                onDrop={(e) => {
                  e.preventDefault();
                  setDragOver(false);
                  handleImageFiles(e.dataTransfer.files);
                }}
                className={`flex flex-col items-center justify-center rounded-xl border-2 border-dashed px-4 py-6 text-center text-sm transition-all duration-xx ease-xx ${
                  dragOver
                    ? 'border-[rgba(34,232,245,0.7)] bg-[rgba(34,232,245,0.1)] shadow-[0_0_44px_-14px_rgba(34,232,245,0.95)]'
                    : 'border-[rgba(255,255,255,0.16)]'
                }`}
              >
                <GeoIcon name="layers" className="mb-1 h-6 w-6" accent="var(--xx-cyan)" />
                <p className="xx-ink-muted">Trage imaginile aici sau</p>
                <label className="mt-1 cursor-pointer font-semibold text-[color:var(--xx-cyan)] underline-offset-4 hover:underline">
                  alege fișiere
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    multiple
                    className="hidden"
                    onChange={(e) => {
                      handleImageFiles(e.target.files);
                      e.target.value = '';
                    }}
                  />
                </label>
                <p className="mt-1 text-xs xx-ink-dim">JPG, PNG sau WebP · max 5 MB</p>
                {imgBusy && <HoloLoader inline size="sm" label="Se procesează" className="mt-2" />}
              </div>

              {images.length > 0 && (
                <>
                  <p className="mt-3 text-xs xx-ink-dim">
                    Trage o imagine pentru a schimba ordinea din galerie.
                  </p>
                  <div className="mt-1 grid grid-cols-3 gap-3 sm:grid-cols-4">
                    {images.map((img) => (
                      <div
                        key={img.id}
                        draggable
                        onDragStart={() => handleImageDragStart(img.id)}
                        onDragOver={(e) => handleImageDragOver(e, img.id)}
                        onDragLeave={() =>
                          setDragOverImageId((cur) => (cur === img.id ? null : cur))
                        }
                        onDrop={(e) => handleImageDrop(e, img.id)}
                        onDragEnd={handleImageDragEnd}
                        className={`group relative cursor-grab overflow-hidden rounded-xl border transition-all duration-xx ease-xx active:cursor-grabbing ${
                          dragOverImageId === img.id
                            ? 'border-[rgba(34,232,245,0.75)] shadow-[0_0_40px_-12px_rgba(34,232,245,0.95)]'
                            : 'border-[rgba(255,255,255,0.12)]'
                        } ${dragImageId === img.id ? 'opacity-40' : ''}`}
                      >
                        <img
                          src={img.thumbnailUrl || img.url}
                          alt=""
                          loading="lazy"
                          className="h-24 w-full object-cover"
                          draggable={false}
                        />
                        {img.primary && (
                          <span className="absolute left-1 top-1 rounded-md border border-[rgba(34,232,245,0.5)] bg-[rgba(9,11,28,0.8)] px-1.5 py-0.5 text-[10px] font-semibold text-[color:var(--xx-cyan)] backdrop-blur-sm">
                            Principală
                          </span>
                        )}
                        {img.width && img.height && (
                          <span className="absolute bottom-0 right-0 rounded-tl-md bg-[rgba(3,4,12,0.8)] px-1 py-0.5 text-[9px] text-[#a8b0d4]">
                            {img.width}×{img.height}
                            {img.format ? ` · ${img.format.toUpperCase()}` : ''}
                          </span>
                        )}
                        <div className="absolute inset-x-0 top-0 flex items-center gap-1 bg-[rgba(3,4,12,0.72)] p-1 opacity-0 backdrop-blur-sm transition-opacity duration-xx ease-xx group-hover:opacity-100 focus-within:opacity-100">
                          {!img.primary && (
                            <button
                              type="button"
                              onClick={() => handleSetPrimary(img.id)}
                              disabled={imgBusy}
                              aria-label="Setează ca imagine principală"
                              className="rounded-md border border-[rgba(34,232,245,0.45)] bg-[rgba(34,232,245,0.14)] px-1.5 py-0.5 text-[10px] font-semibold text-[color:var(--xx-cyan)] transition-all duration-xx ease-xx hover:border-[rgba(34,232,245,0.8)] disabled:opacity-40"
                            >
                              ★ Principală
                            </button>
                          )}
                          {confirmDeleteImageId === img.id ? (
                            <span className="ml-auto flex items-center gap-1">
                              <button
                                type="button"
                                onClick={() => handleDeleteImage(img.id)}
                                disabled={imgBusy}
                                className="rounded-md border border-[rgba(255,84,112,0.6)] bg-[rgba(255,84,112,0.22)] px-1.5 py-0.5 text-[10px] font-semibold text-[#ffc2cc] transition-all duration-xx ease-xx hover:border-[rgba(255,84,112,0.9)] disabled:opacity-40"
                              >
                                Sigur?
                              </button>
                              <button
                                type="button"
                                onClick={cancelDeleteImage}
                                disabled={imgBusy}
                                className="rounded-md border border-[rgba(255,255,255,0.2)] px-1.5 py-0.5 text-[10px] font-medium text-[#a8b0d4] transition-all duration-xx ease-xx hover:text-[color:var(--xx-ink)] disabled:opacity-40"
                              >
                                Anulează
                              </button>
                            </span>
                          ) : (
                            <button
                              type="button"
                              onClick={() => askDeleteImage(img.id)}
                              disabled={imgBusy}
                              aria-label="Șterge imaginea"
                              className="ml-auto grid h-5 w-5 place-items-center rounded-md border border-[rgba(255,84,112,0.5)] bg-[rgba(255,84,112,0.18)] text-[color:var(--xx-red)] transition-all duration-xx ease-xx hover:border-[rgba(255,84,112,0.9)] disabled:opacity-40"
                            >
                              <GeoIcon name="close" className="h-3 w-3" accent="currentColor" />
                            </button>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </>
              )}
            </div>
          ) : (
            <HoloInput
              type="file"
              accept="image/jpeg,image/png,image/webp"
              label="Imagine principală (opțional)"
              hint="Poți adăuga mai multe imagini după ce salvezi produsul."
              onChange={(e) => setImageFile(e.target.files?.[0] || null)}
            />
          )}
          <div className="flex justify-end gap-2 pt-2">
            <NeonButton type="button" variant="ghost" onClick={() => setModalOpen(false)}>
              Anulează
            </NeonButton>
            <NeonButton type="submit" disabled={saving} charging={saving}>
              {saving ? 'Se salvează…' : 'Salvează'}
            </NeonButton>
          </div>
        </form>
      </Modal>

      {/* Import modal */}
      <Modal
        open={importOpen}
        title="Import produse din Excel"
        onClose={() => setImportOpen(false)}
        maxWidth="max-w-2xl"
      >
        {importError && (
          <div
            role="alert"
            className="mb-4 rounded-xl border border-[rgba(255,84,112,0.45)] bg-[rgba(255,84,112,0.12)] px-4 py-2 text-sm text-[#ffc2cc]"
          >
            {importError}
          </div>
        )}

        <p className="mb-3 text-sm xx-ink-muted">
          {syncMode
            ? 'Mod "doar prețuri achiziție": actualizez DOAR prețul de achiziție al produselor existente, potrivind după nume. Nu creez, nu șterg și nu modific stoc, preț de vânzare sau categorii.'
            : restockMode
            ? 'Mod "intrare marfă": pentru produsele care există deja, adaug cantitatea din Excel la stocul curent și recalculez prețul de achiziție ca medie ponderată după cantitate. Produsele noi sunt adăugate normal. Prețul de vânzare și categoriile produselor existente rămân neschimbate.'
            : 'Încarcă fișierul .xlsx completat după șablon. Îl verific întâi (fără a scrie nimic) și îți arăt exact ce e valid și ce trebuie corectat. Abia după confirmare import produsele.'}
        </p>

        <label className="mb-2 flex cursor-pointer items-start gap-2.5 rounded-xl border border-[rgba(255,255,255,0.12)] bg-[rgba(255,255,255,0.04)] px-3 py-2.5 text-sm xx-ink-muted transition-all duration-xx ease-xx hover:border-[rgba(34,232,245,0.4)]">
          <input
            type="checkbox"
            className="mt-0.5 h-4 w-4 cursor-pointer rounded accent-[#22e8f5]"
            checked={syncMode}
            onChange={(e) => {
              setSyncMode(e.target.checked);
              if (e.target.checked) setRestockMode(false);
              setImportReport(null);
              setImportDone(null);
              setImportError(null);
            }}
          />
          <span>
            <span className="font-semibold text-[color:var(--xx-ink)]">Doar prețuri de achiziție</span> — completează prețul de
            achiziție lipsă din baza de date, fără a atinge stocul, prețul de vânzare sau categoriile.
          </span>
        </label>

        <label className="mb-3 flex cursor-pointer items-start gap-2.5 rounded-xl border border-[rgba(255,255,255,0.12)] bg-[rgba(255,255,255,0.04)] px-3 py-2.5 text-sm xx-ink-muted transition-all duration-xx ease-xx hover:border-[rgba(34,232,245,0.4)]">
          <input
            type="checkbox"
            className="mt-0.5 h-4 w-4 cursor-pointer rounded accent-[#22e8f5]"
            checked={restockMode}
            onChange={(e) => {
              setRestockMode(e.target.checked);
              if (e.target.checked) setSyncMode(false);
              setImportReport(null);
              setImportDone(null);
              setImportError(null);
            }}
          />
          <span>
            <span className="font-semibold text-[color:var(--xx-ink)]">Mod intrare marfă</span> — la produsele existente adaugă
            cantitatea la stoc și recalculează prețul de achiziție ca medie ponderată (CMP); produsele
            noi sunt adăugate normal.
          </span>
        </label>

        <HoloInput
          type="file"
          accept=".xlsx,.xls"
          label="Fișier Excel"
          hint="Format .xlsx sau .xls, completat după șablon."
          onChange={(e) => {
            setImportFile(e.target.files?.[0] || null);
            setImportReport(null);
            setImportDone(null);
            setImportError(null);
          }}
        />

        {importReport && (
          <div className="mt-4 space-y-3">
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <Stat label="Rânduri" value={importReport.totalRows} />
              <Stat
                label={syncMode ? 'Cu preț achiziție' : 'Valide'}
                value={importReport.validCount}
                tone="green"
              />
              <Stat label="Cu erori" value={importReport.errors?.length || 0} tone="red" />
              <Stat
                label={syncMode ? 'Se vor actualiza' : restockMode ? 'La stoc (există)' : 'Avertismente'}
                value={
                  syncMode || restockMode
                    ? importReport.updatedCount
                    : importReport.warnings?.length || 0
                }
                tone="amber"
              />
            </div>

            {importDone && (
              <div
                role="status"
                className="flex items-start gap-2 rounded-xl border border-[rgba(31,172,121,0.45)] bg-[rgba(31,172,121,0.12)] px-4 py-2.5 text-sm text-[#93e9c4]"
              >
                <GeoIcon name="check" className="mt-0.5 h-4 w-4 shrink-0" accent="currentColor" />
                {syncMode
                  ? `Sincronizare finalizată: ${importDone.updatedCount} produse au primit prețul de achiziție.`
                  : restockMode
                  ? `Intrare marfă finalizată: ${importDone.updatedCount} produse actualizate la stoc (medie ponderată), ${importDone.createdCount} produse noi.`
                  : `Import finalizat: ${importDone.createdCount} adăugate, ${importDone.updatedCount} actualizate.`}
              </div>
            )}

            {importReport.errors?.length > 0 && (
              <div className="max-h-48 overflow-y-auto rounded-xl border border-[rgba(255,84,112,0.4)] bg-[rgba(255,84,112,0.08)] p-3 text-sm">
                <p className="mb-1 flex items-center gap-2 font-semibold text-[#ffc2cc]">
                  <GeoIcon name="alert" className="h-4 w-4" accent="currentColor" />
                  Rânduri cu probleme (vor fi sărite):
                </p>
                <ul className="list-disc space-y-1 pl-5 text-[#ffc2cc]">
                  {importReport.errors.map((e) => (
                    <li key={e.row}>
                      Rând {e.row}: {e.message}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {importReport.warnings?.length > 0 && (
              <div className="max-h-40 overflow-y-auto rounded-xl border border-[rgba(176,140,9,0.42)] bg-[rgba(176,140,9,0.1)] p-3 text-sm">
                <p className="mb-1 flex items-center gap-2 font-semibold text-[#f0d089]">
                  <GeoIcon name="clock" className="h-4 w-4" accent="currentColor" />
                  Avertismente:
                </p>
                <ul className="list-disc space-y-1 pl-5 text-[#f0d089]">
                  {importReport.warnings.map((w, i) => (
                    <li key={i}>{w}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}

        <div className="mt-5 flex flex-wrap justify-end gap-2">
          <NeonButton variant="ghost" onClick={() => setImportOpen(false)}>
            Închide
          </NeonButton>
          <NeonButton
            variant="secondary"
            disabled={importBusy || !importFile}
            charging={importBusy}
            onClick={() => runImport(true)}
            icon={<GeoIcon name="shield" className="h-4 w-4" accent="currentColor" />}
          >
            {importBusy ? 'Se verifică…' : 'Verifică fișierul'}
          </NeonButton>
          <NeonButton
            disabled={importBusy || !importReport || importReport.validCount === 0 || !!importDone}
            onClick={() => runImport(false)}
            icon={<GeoIcon name="layers" className="h-4 w-4" accent="currentColor" />}
          >
            {importDone
              ? syncMode
                ? 'Sincronizat ✓'
                : restockMode
                ? 'Recepționat ✓'
                : 'Importat ✓'
              : syncMode
              ? `Sincronizează ${importReport ? importReport.updatedCount : ''} prețuri`
              : restockMode
              ? `Înregistrează intrarea (${importReport ? importReport.validCount : ''})`
              : `Importă ${importReport ? importReport.validCount : ''} produse`}
          </NeonButton>
        </div>
      </Modal>

      {/* Quick preview (feature: previzualizare produs) */}
      <Modal
        open={!!previewProduct}
        title={previewProduct?.name || 'Previzualizare produs'}
        onClose={closePreview}
        maxWidth="max-w-2xl"
      >
        {previewProduct && (
          <div className="space-y-4">
            <div className="flex items-start gap-4">
              <img
                src={resolveImage(previewProduct.imageUrl)}
                alt={previewProduct.name}
                loading="lazy"
                className="h-28 w-28 flex-shrink-0 rounded-xl border border-[rgba(255,255,255,0.14)] object-cover shadow-[0_0_44px_-16px_rgba(34,232,245,0.8)]"
              />
              <div className="min-w-0 flex-1">
                <p className="text-lg font-semibold text-[color:var(--xx-ink)]">{previewProduct.name}</p>
                <p className="text-sm xx-ink-muted">
                  {previewProduct.category}
                  {previewProduct.subcategory ? ` · ${previewProduct.subcategory}` : ''}
                  {previewProduct.brand ? ` · ${previewProduct.brand}` : ''}
                </p>
                {previewProduct.sku && (
                  <p className="mt-1 font-mono text-xs xx-ink-dim">SKU: {previewProduct.sku}</p>
                )}
                <div className="mt-2 flex flex-wrap items-center gap-3">
                  <span
                    className="text-xl font-bold text-[color:var(--xx-ink)]"
                    style={{ textShadow: '0 0 26px rgba(34,232,245,0.4)' }}
                  >
                    {formatPrice(previewProduct.price)}
                  </span>
                  {previewProduct.purchasePrice != null && (
                    <span className="text-sm xx-ink-muted">
                      Achiziție: {formatPrice(previewProduct.purchasePrice)}
                    </span>
                  )}
                  {previewProduct.profit != null && (
                    <span className="text-sm font-semibold text-[#7ee9bd]">
                      Profit: {formatPrice(previewProduct.profit)}
                      {previewProduct.marginPercent != null && ` · ${previewProduct.marginPercent}%`}
                    </span>
                  )}
                  <NeonBadge tone={previewProduct.stockQuantity > 0 ? 'good' : 'critical'}>
                    Stoc: {previewProduct.stockQuantity}
                  </NeonBadge>
                </div>
              </div>
            </div>

            {previewProduct.description && (
              <p className="whitespace-pre-line text-sm xx-ink-muted">{previewProduct.description}</p>
            )}

            {previewLoading ? (
              <HoloLoader label="Se încarcă galeria" />
            ) : (
              previewProduct.images?.length > 0 && (
                <div className="grid grid-cols-4 gap-2 sm:grid-cols-6">
                  {previewProduct.images.map((img) => (
                    <img
                      key={img.id}
                      src={img.thumbnailUrl || img.url}
                      alt=""
                      loading="lazy"
                      className="h-16 w-full rounded-lg border border-[rgba(255,255,255,0.1)] object-cover"
                    />
                  ))}
                </div>
              )
            )}

            {/* Istoric (feature #5) — preț, stoc, imagini, activare/dezactivare */}
            <div>
              <p className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.16em] xx-ink-muted">
                <GeoIcon name="clock" className="h-4 w-4" />
                Istoric recent
              </p>
              {previewHistoryLoading ? (
                <HoloLoader inline size="sm" label="Se încarcă istoricul" />
              ) : previewHistory.length === 0 ? (
                <p className="text-xs xx-ink-muted">Nicio activitate înregistrată pentru acest produs.</p>
              ) : (
                <ul className="max-h-40 space-y-1.5 overflow-y-auto rounded-[0.9rem] border border-[rgba(255,255,255,0.1)] bg-[rgba(255,255,255,0.04)] p-3 text-xs">
                  {previewHistory.map((h) => (
                    <li
                      key={h.id}
                      className="flex items-start justify-between gap-3 border-b border-[rgba(255,255,255,0.06)] pb-1.5 last:border-0 last:pb-0"
                    >
                      <span className="xx-ink-muted">
                        <span className="font-semibold text-[#c9d4ff]">
                          {ACTION_LABELS[h.action] || h.action}
                        </span>
                        {h.details ? ` — ${h.details}` : ''}
                      </span>
                      <span className="shrink-0 font-mono xx-ink-muted">{formatDate(h.createdAt)}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <div className="flex justify-end gap-2 pt-2">
              <NeonButton variant="ghost" size="sm" onClick={closePreview}>
                Închide
              </NeonButton>
              <NeonButton
                size="sm"
                icon={<GeoIcon name="gear" className="h-4 w-4" />}
                onClick={() => {
                  const p = previewProduct;
                  closePreview();
                  openEdit(p);
                }}
              >
                Editează
              </NeonButton>
            </div>
          </div>
        )}
      </Modal>

      <ToastHost />
    </div>
  );
}

/**
 * XXII — raportul de import, redus la patru cifre.
 *
 * Tonul nu este singurul purtător al sensului: fiecare cifră stă sub eticheta ei
 * scrisă, deci raportul rămâne lizibil și pentru un utilizator care nu distinge
 * verdele de roșu. Culoarea doar accelerează scanarea, nu o condiționează.
 */
function Stat({ label, value, tone = 'slate' }) {
  const tones = {
    slate: { ink: '#e8ecff', edge: 'rgba(255,255,255,0.12)', glow: 'rgba(255,255,255,0.18)' },
    green: { ink: '#7ee9bd', edge: 'rgba(31,172,121,0.38)', glow: 'rgba(31,172,121,0.5)' },
    red: { ink: '#ff8fa8', edge: 'rgba(255,90,122,0.38)', glow: 'rgba(255,90,122,0.5)' },
    amber: { ink: '#ffd27a', edge: 'rgba(255,186,80,0.38)', glow: 'rgba(255,186,80,0.5)' },
  };
  const t = tones[tone] || tones.slate;

  return (
    <div
      className="rounded-[0.9rem] border bg-[rgba(255,255,255,0.04)] p-3 text-center"
      style={{ borderColor: t.edge }}
    >
      <p
        className="text-2xl font-bold"
        style={{ color: t.ink, textShadow: `0 0 22px ${t.glow}` }}
      >
        {value}
      </p>
      <p className="mt-0.5 text-[0.68rem] font-semibold uppercase tracking-[0.14em] xx-ink-muted">
        {label}
      </p>
    </div>
  );
}

/**
 * XXII — TASK 6 (product management cu carduri 3D și hover tilt).
 *
 * Varianta „grid” a listei de produse. Tabelul rămâne suprafața pentru munca în
 * masă — editare inline, selecție multiplă, scanare densă — iar acest card este
 * suprafața pentru munca vizuală: verifici cum arată catalogul fotografic,
 * observi imediat produsele fără imagine și produsele epuizate.
 *
 * Cardul respectă aceleași reguli ca rândul de tabel:
 *   - starea de selecție este redată de bordura cyan ȘI de checkbox, niciodată
 *     doar de culoare;
 *   - stocul poartă simbolul ✓ / ✕ lângă cifră, deci starea se citește fără
 *     percepția culorii;
 *   - fiecare buton fără text are `title` și `aria-label`.
 *
 * Înclinarea 3D este delegată lui `TiltCard`, care ține perspectiva pe elementul
 * exterior — zona de click nu se rotește, deci cursorul nu „cade” de pe card în
 * timpul hover-ului.
 */
function ProductTile({
  p,
  selected,
  inCart,
  activeBusy,
  onToggle,
  onPreview,
  onEdit,
  onSell,
  onToggleActive,
  onDelete,
}) {
  const outOfStock = p.stockQuantity <= 0;

  return (
    <TiltCard
      max={4}
      scale={1.015}
      className="h-full"
      innerClassName={`group flex h-full flex-col overflow-hidden rounded-[1.15rem] border bg-[rgba(255,255,255,0.04)] backdrop-blur-xl transition-colors duration-200 ${
        selected
          ? 'border-[rgba(34,232,245,0.65)] bg-[rgba(34,232,245,0.07)]'
          : 'border-[rgba(255,255,255,0.1)] hover:border-[rgba(122,60,255,0.45)]'
      }`}
    >
      {/* Imaginea — suprafața pe care operatorul o evaluează în acest mod. */}
      <div className="relative aspect-[4/3] w-full overflow-hidden bg-[rgba(255,255,255,0.03)]">
        <img
          src={resolveImage(p.imageUrl)}
          alt={p.name}
          loading="lazy"
          className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-[1.06]"
        />

        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-x-0 bottom-0 h-2/3 bg-gradient-to-t from-[rgba(9,11,28,0.92)] via-[rgba(9,11,28,0.35)] to-transparent"
        />

        <label className="absolute left-3 top-3 flex cursor-pointer items-center rounded-lg bg-[rgba(9,11,28,0.72)] p-1.5 backdrop-blur-md">
          <input
            type="checkbox"
            checked={selected}
            onChange={onToggle}
            className="h-4 w-4 cursor-pointer accent-[#22e8f5]"
            aria-label={`Selectează ${p.name}`}
          />
        </label>

        <div className="absolute right-3 top-3 flex flex-col items-end gap-1.5">
          {!p.active && <NeonBadge tone="warning">Inactiv</NeonBadge>}
          {inCart > 0 && <NeonBadge tone="good" pulse>{`În vânzare: ${inCart}`}</NeonBadge>}
        </div>

        <div className="absolute inset-x-3 bottom-3 flex items-end justify-between gap-2">
          <span
            className="text-lg font-bold text-[#e8ecff]"
            style={{ textShadow: '0 0 24px rgba(34,232,245,0.45)' }}
          >
            {formatPrice(p.price)}
          </span>
          <span
            className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[0.68rem] font-semibold backdrop-blur-md ${
              outOfStock
                ? 'border-[rgba(255,90,122,0.45)] bg-[rgba(255,90,122,0.16)] text-[#ff8fa8]'
                : 'border-[rgba(31,172,121,0.45)] bg-[rgba(31,172,121,0.16)] text-[#7ee9bd]'
            }`}
          >
            <span aria-hidden="true">{outOfStock ? '✕' : '✓'}</span>
            {p.stockQuantity}
          </span>
        </div>
      </div>

      {/* Identitatea produsului. */}
      <div className="flex flex-1 flex-col gap-1 px-4 pt-3">
        <p className="line-clamp-2 text-sm font-semibold text-[#e8ecff]">{p.name}</p>
        {p.brand ? <p className="text-xs xx-ink-dim">{p.brand}</p> : null}
        {p.sku ? <p className="font-mono text-[0.68rem] xx-ink-muted">{p.sku}</p> : null}
      </div>

      {/* Acțiunile — aceleași cinci ca în tabel, în aceeași ordine. */}
      <div className="mt-3 flex items-center gap-1.5 border-t border-[rgba(255,255,255,0.08)] px-3 py-2.5">
        <button
          type="button"
          onClick={onSell}
          disabled={outOfStock}
          title={outOfStock ? 'Produs fără stoc' : 'Adaugă în vânzare'}
          aria-label={`Adaugă ${p.name} în vânzare`}
          className={`inline-flex flex-1 items-center justify-center gap-1.5 rounded-lg border px-2 py-1.5 text-xs font-semibold transition-colors duration-200 ${
            outOfStock
              ? 'cursor-not-allowed border-[rgba(255,255,255,0.08)] text-[rgba(232,236,255,0.35)]'
              : 'border-[rgba(31,172,121,0.45)] bg-[rgba(31,172,121,0.12)] text-[#7ee9bd] hover:bg-[rgba(31,172,121,0.22)]'
          }`}
        >
          <GeoIcon name="coins" className="h-4 w-4" />
          Vinde
        </button>

        <button
          type="button"
          onClick={onPreview}
          title="Previzualizează"
          aria-label={`Previzualizează ${p.name}`}
          className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[#c9d4ff] transition-colors duration-200 hover:border-[rgba(34,232,245,0.5)] hover:text-[#22e8f5]"
        >
          <GeoIcon name="zoom" className="h-4 w-4" />
        </button>

        <button
          type="button"
          onClick={onEdit}
          title="Editează"
          aria-label={`Editează ${p.name}`}
          className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[#c9d4ff] transition-colors duration-200 hover:border-[rgba(122,60,255,0.55)] hover:text-[#b795ff]"
        >
          <GeoIcon name="gear" className="h-4 w-4" />
        </button>

        <button
          type="button"
          onClick={onToggleActive}
          disabled={activeBusy}
          title={p.active ? 'Dezactivează produsul' : 'Activează produsul'}
          aria-label={`${p.active ? 'Dezactivează' : 'Activează'} ${p.name}`}
          className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[#c9d4ff] transition-colors duration-200 hover:border-[rgba(255,186,80,0.5)] hover:text-[#ffd27a] disabled:opacity-45"
        >
          <GeoIcon name={p.active ? 'clock' : 'bolt'} className="h-4 w-4" />
        </button>

        <button
          type="button"
          onClick={onDelete}
          title="Șterge produsul"
          aria-label={`Șterge ${p.name}`}
          className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-[rgba(255,255,255,0.12)] text-[#c9d4ff] transition-colors duration-200 hover:border-[rgba(255,90,122,0.5)] hover:text-[#ff8fa8]"
        >
          <GeoIcon name="trash" className="h-4 w-4" />
        </button>
      </div>
    </TiltCard>
  );
}
