import { useEffect, useState } from 'react';
import productService from '../../api/productService';
import adminService from '../../api/adminService';
import AdminNav from '../../components/AdminNav';
import Modal from '../../components/Modal';
import Pagination from '../../components/Pagination';
import Spinner from '../../components/Spinner';
import { formatPrice, resolveImage } from '../../utils/format';

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
  const [totalPages, setTotalPages] = useState(0);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [imageFile, setImageFile] = useState(null);

  // Image gallery manager (feature #5)
  const [images, setImages] = useState([]);
  const [imgBusy, setImgBusy] = useState(false);
  const [imgError, setImgError] = useState(null);
  const [dragOver, setDragOver] = useState(false);

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

  const load = () => {
    setLoading(true);
    // Admin endpoint: includes purchasePrice + profit (only visible to admins).
    adminService
      .listAdminProducts({ page, size: 10, search })
      .then((data) => {
        setProducts(data.content);
        setTotalPages(data.totalPages);
      })
      .catch(() => setProducts([]))
      .finally(() => setLoading(false));
  };

  useEffect(load, [page, search]);

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setImageFile(null);
    setImages([]);
    setImgError(null);
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
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Salvarea a eșuat.');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (p) => {
    if (!window.confirm(`Ștergi produsul "${p.name}"?`)) return;
    try {
      await productService.remove(p.id);
      load();
    } catch (err) {
      alert(err.response?.data?.message || 'Ștergerea a eșuat.');
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
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-bold text-slate-800">Management produse</h1>
        <div className="flex gap-2">
          <button className="btn-secondary" onClick={openImport}>
            ⬆ Import Excel
          </button>
          <button className="btn-primary" onClick={openCreate}>
            + Produs nou
          </button>
        </div>
      </div>

      <input
        className="input mb-4 sm:w-72"
        placeholder="Caută produse..."
        value={search}
        onChange={(e) => {
          setPage(0);
          setSearch(e.target.value);
        }}
      />

      {loading ? (
        <Spinner />
      ) : (
        <div className="card overflow-x-auto">
          <table className="min-w-full divide-y divide-slate-200 text-sm">
            <thead className="bg-slate-50 text-left text-slate-500">
              <tr>
                <th className="px-4 py-3">Produs</th>
                <th className="px-4 py-3">Categorie</th>
                <th className="px-4 py-3">Preț vânzare</th>
                <th className="px-4 py-3">Achiziție</th>
                <th className="px-4 py-3">Profit</th>
                <th className="px-4 py-3">Stoc</th>
                <th className="px-4 py-3 text-right">Acțiuni</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {products.map((p) => (
                <tr key={p.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-3">
                      <img
                        src={resolveImage(p.imageUrl)}
                        alt={p.name}
                        className="h-10 w-10 rounded object-cover"
                      />
                      <div>
                        <p className="font-medium text-slate-800">{p.name}</p>
                        <p className="text-xs text-slate-500">{p.brand}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-slate-600">
                    {p.category}
                    {p.subcategory ? <span className="text-slate-400"> · {p.subcategory}</span> : null}
                  </td>
                  <td className="px-4 py-3 font-medium">{formatPrice(p.price)}</td>
                  <td className="px-4 py-3 text-graphite-600">
                    {p.purchasePrice != null ? formatPrice(p.purchasePrice) : '—'}
                  </td>
                  <td className="px-4 py-3">
                    {p.profit != null ? (
                      <span className="font-medium text-brand-700">
                        {formatPrice(p.profit)}
                        {p.marginPercent != null && (
                          <span className="ml-1 text-xs text-graphite-400">· {p.marginPercent}%</span>
                        )}
                      </span>
                    ) : (
                      <span className="text-graphite-400">—</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={`badge ${
                        p.stockQuantity > 0 ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                      }`}
                    >
                      {p.stockQuantity}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button onClick={() => openEdit(p)} className="mr-2 text-brand-600 hover:underline">
                      Editează
                    </button>
                    <button onClick={() => handleDelete(p)} className="text-red-600 hover:underline">
                      Șterge
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pagination page={page} totalPages={totalPages} onChange={setPage} />

      {/* Create / edit modal */}
      <Modal
        open={modalOpen}
        title={editing ? 'Editează produs' : 'Produs nou'}
        onClose={() => setModalOpen(false)}
        maxWidth="max-w-2xl"
      >
        {error && (
          <div className="mb-4 rounded-lg bg-red-50 px-4 py-2 text-sm text-red-700">{error}</div>
        )}
        <form onSubmit={handleSubmit} className="space-y-3">
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-600">Nume</label>
            <input name="name" className="input" value={form.name} onChange={handleChange} required />
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-600">Descriere</label>
            <textarea
              name="description"
              className="input min-h-[80px]"
              value={form.description}
              onChange={handleChange}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">Preț vânzare (RON)</label>
              <input
                type="number"
                step="0.01"
                name="price"
                className="input"
                value={form.price}
                onChange={handleChange}
                required
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">
                Preț achiziție (RON) · doar admin
              </label>
              <input
                type="number"
                step="0.01"
                name="purchasePrice"
                className="input"
                value={form.purchasePrice}
                onChange={handleChange}
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">Stoc</label>
              <input
                type="number"
                name="stockQuantity"
                className="input"
                value={form.stockQuantity}
                onChange={handleChange}
                required
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">Cod / SKU</label>
              <input name="sku" className="input" value={form.sku} onChange={handleChange} />
            </div>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">Categorie</label>
              <input name="category" className="input" value={form.category} onChange={handleChange} />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">Subcategorie</label>
              <input
                name="subcategory"
                className="input"
                value={form.subcategory}
                onChange={handleChange}
              />
            </div>
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">Brand</label>
              <input name="brand" className="input" value={form.brand} onChange={handleChange} />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-slate-600">
              URL imagine (opțional)
            </label>
            <input name="imageUrl" className="input" value={form.imageUrl} onChange={handleChange} />
          </div>

          {editing ? (
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">Imagini produs</label>
              {imgError && (
                <div className="mb-2 rounded bg-red-50 px-3 py-1.5 text-xs text-red-700">{imgError}</div>
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
                className={`flex flex-col items-center justify-center rounded-lg border-2 border-dashed px-4 py-6 text-center text-sm transition ${
                  dragOver ? 'border-brand-500 bg-brand-50' : 'border-slate-300'
                }`}
              >
                <p className="text-slate-600">Trage imaginile aici sau</p>
                <label className="mt-1 cursor-pointer font-medium text-brand-600 hover:underline">
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
                <p className="mt-1 text-xs text-slate-400">JPG, PNG sau WebP · max 5 MB</p>
                {imgBusy && <p className="mt-2 text-xs text-brand-600">Se procesează...</p>}
              </div>

              {images.length > 0 && (
                <div className="mt-3 grid grid-cols-3 gap-3 sm:grid-cols-4">
                  {images.map((img) => (
                    <div
                      key={img.id}
                      className="group relative overflow-hidden rounded-lg border border-slate-200"
                    >
                      <img src={img.url} alt="" className="h-24 w-full object-cover" />
                      {img.primary && (
                        <span className="absolute left-1 top-1 rounded bg-brand-600 px-1.5 py-0.5 text-[10px] font-semibold text-white">
                          Principală
                        </span>
                      )}
                      <div className="absolute inset-x-0 bottom-0 flex items-center gap-1 bg-black/50 p-1 opacity-0 transition group-hover:opacity-100">
                        {!img.primary && (
                          <button
                            type="button"
                            onClick={() => handleSetPrimary(img.id)}
                            disabled={imgBusy}
                            className="rounded bg-white/90 px-1.5 py-0.5 text-[10px] font-medium text-slate-700 hover:bg-white"
                          >
                            ★ Principală
                          </button>
                        )}
                        <button
                          type="button"
                          onClick={() => handleDeleteImage(img.id)}
                          disabled={imgBusy}
                          className="ml-auto rounded bg-red-600 px-1.5 py-0.5 text-[10px] font-medium text-white hover:bg-red-700"
                        >
                          ✕
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          ) : (
            <div>
              <label className="mb-1 block text-sm font-medium text-slate-600">
                Imagine principală (opțional)
              </label>
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                className="input"
                onChange={(e) => setImageFile(e.target.files?.[0] || null)}
              />
              <p className="mt-1 text-xs text-slate-400">
                Poți adăuga mai multe imagini după ce salvezi produsul.
              </p>
            </div>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <button type="button" className="btn-secondary" onClick={() => setModalOpen(false)}>
              Anulează
            </button>
            <button type="submit" className="btn-primary" disabled={saving}>
              {saving ? 'Se salvează...' : 'Salvează'}
            </button>
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
          <div className="mb-4 rounded-lg bg-red-50 px-4 py-2 text-sm text-red-700">{importError}</div>
        )}

        <p className="mb-3 text-sm text-slate-600">
          {syncMode
            ? 'Mod "doar prețuri achiziție": actualizez DOAR prețul de achiziție al produselor existente, potrivind după nume. Nu creez, nu șterg și nu modific stoc, preț de vânzare sau categorii.'
            : restockMode
            ? 'Mod "intrare marfă": pentru produsele care există deja, adaug cantitatea din Excel la stocul curent și recalculez prețul de achiziție ca medie ponderată după cantitate. Produsele noi sunt adăugate normal. Prețul de vânzare și categoriile produselor existente rămân neschimbate.'
            : 'Încarcă fișierul .xlsx completat după șablon. Îl verific întâi (fără a scrie nimic) și îți arăt exact ce e valid și ce trebuie corectat. Abia după confirmare import produsele.'}
        </p>

        <label className="mb-2 flex cursor-pointer items-start gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
          <input
            type="checkbox"
            className="mt-0.5"
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
            <span className="font-medium">Doar prețuri de achiziție</span> — completează prețul de
            achiziție lipsă din baza de date, fără a atinge stocul, prețul de vânzare sau categoriile.
          </span>
        </label>

        <label className="mb-3 flex cursor-pointer items-start gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
          <input
            type="checkbox"
            className="mt-0.5"
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
            <span className="font-medium">Mod intrare marfă</span> — la produsele existente adaugă
            cantitatea la stoc și recalculează prețul de achiziție ca medie ponderată (CMP); produsele
            noi sunt adăugate normal.
          </span>
        </label>

        <input
          type="file"
          accept=".xlsx,.xls"
          className="input"
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
              <div className="rounded-lg bg-green-50 px-4 py-2 text-sm text-green-700">
                {syncMode
                  ? `Sincronizare finalizată: ${importDone.updatedCount} produse au primit prețul de achiziție.`
                  : restockMode
                  ? `Intrare marfă finalizată: ${importDone.updatedCount} produse actualizate la stoc (medie ponderată), ${importDone.createdCount} produse noi.`
                  : `Import finalizat: ${importDone.createdCount} adăugate, ${importDone.updatedCount} actualizate.`}
              </div>
            )}

            {importReport.errors?.length > 0 && (
              <div className="max-h-48 overflow-y-auto rounded-lg border border-red-100 bg-red-50 p-3 text-sm">
                <p className="mb-1 font-semibold text-red-700">Rânduri cu probleme (vor fi sărite):</p>
                <ul className="list-disc space-y-1 pl-5 text-red-700">
                  {importReport.errors.map((e) => (
                    <li key={e.row}>
                      Rând {e.row}: {e.message}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {importReport.warnings?.length > 0 && (
              <div className="max-h-40 overflow-y-auto rounded-lg border border-amber-100 bg-amber-50 p-3 text-sm">
                <p className="mb-1 font-semibold text-amber-700">Avertismente:</p>
                <ul className="list-disc space-y-1 pl-5 text-amber-700">
                  {importReport.warnings.map((w, i) => (
                    <li key={i}>{w}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}

        <div className="mt-5 flex justify-end gap-2">
          <button type="button" className="btn-secondary" onClick={() => setImportOpen(false)}>
            Închide
          </button>
          <button
            type="button"
            className="btn-secondary"
            disabled={importBusy || !importFile}
            onClick={() => runImport(true)}
          >
            {importBusy ? 'Se verifică...' : 'Verifică fișierul'}
          </button>
          <button
            type="button"
            className="btn-primary"
            disabled={importBusy || !importReport || importReport.validCount === 0 || !!importDone}
            onClick={() => runImport(false)}
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
          </button>
        </div>
      </Modal>
    </div>
  );
}

function Stat({ label, value, tone = 'slate' }) {
  const tones = {
    slate: 'text-slate-900',
    green: 'text-green-700',
    red: 'text-red-700',
    amber: 'text-amber-700',
  };
  return (
    <div className="rounded-lg border border-slate-200 p-3 text-center">
      <p className={`text-2xl font-bold ${tones[tone]}`}>{value}</p>
      <p className="text-xs text-slate-500">{label}</p>
    </div>
  );
}
