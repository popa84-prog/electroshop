package com.electroshop.service;

import com.electroshop.dto.AdminProductDto;
import com.electroshop.dto.CategoryStatDto;
import com.electroshop.dto.ProductDto;
import com.electroshop.dto.ProductRequest;
import com.electroshop.exception.ResourceNotFoundException;
import com.electroshop.model.Order;
import com.electroshop.model.OrderItem;
import com.electroshop.model.Product;
import com.electroshop.model.ProductImage;
import com.electroshop.model.Purchase;
import com.electroshop.model.PurchaseItem;
import com.electroshop.repository.OrderItemRepository;
import com.electroshop.repository.OrderRepository;
import com.electroshop.repository.ProductRepository;
import com.electroshop.repository.PurchaseItemRepository;
import com.electroshop.repository.PurchaseRepository;
import com.electroshop.security.PermissionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class ProductService {

    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024; // 5 MB

    /** Upper bound on how many rows a single spreadsheet export may contain. */
    private static final int MAX_EXPORT_ROWS = 20_000;

    /** Stock at or below this (but above zero) counts as "Stoc redus" for the quick filter. */
    private static final int LOW_STOCK_THRESHOLD = 5;

    private final ProductRepository productRepository;
    private final AuditService auditService;
    private final CloudinaryService cloudinaryService;
    private final ProductExportService productExportService;
    private final PermissionService permissionService;
    private final NotificationService notificationService;
    private final OrderItemRepository orderItemRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final OrderRepository orderRepository;
    private final PurchaseRepository purchaseRepository;

    public ProductService(ProductRepository productRepository, AuditService auditService,
                          CloudinaryService cloudinaryService,
                          ProductExportService productExportService,
                          PermissionService permissionService,
                          NotificationService notificationService,
                          OrderItemRepository orderItemRepository,
                          PurchaseItemRepository purchaseItemRepository,
                          OrderRepository orderRepository,
                          PurchaseRepository purchaseRepository) {
        this.productRepository = productRepository;
        this.auditService = auditService;
        this.cloudinaryService = cloudinaryService;
        this.productExportService = productExportService;
        this.permissionService = permissionService;
        this.notificationService = notificationService;
        this.orderItemRepository = orderItemRepository;
        this.purchaseItemRepository = purchaseItemRepository;
        this.orderRepository = orderRepository;
        this.purchaseRepository = purchaseRepository;
    }

    /**
     * Whether this product has ever been sold or received into stock. A row
     * with sales or goods-in history can never be hard-deleted — the database
     * enforces this already via {@code order_items}/{@code purchase_items}'
     * foreign keys — so this check runs *before* attempting a delete, turning
     * what would otherwise be a raw {@code DataIntegrityViolationException}
     * into a deliberate "deactivate instead" decision.
     */
    private boolean hasSalesHistory(Long productId) {
        return orderItemRepository.existsByProductId(productId)
                || purchaseItemRepository.existsByProductId(productId);
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> list(String search, String category, String subcategory, String brand,
                                 BigDecimal minPrice, BigDecimal maxPrice, boolean inStock,
                                 Pageable pageable) {
        return productRepository.search(
                blankToNull(search), blankToNull(category), blankToNull(subcategory),
                blankToNull(brand), minPrice, maxPrice, inStock, false, null, false, true, pageable
        ).map(ProductDto::from);
    }

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

    @Transactional(readOnly = true)
    public ProductDto getById(Long id) {
        return ProductDto.detail(findEntity(id));
    }

    // ---- Admin-only views (expose purchase price + profit) ----

    /**
     * Admin table listing. {@code quickFilter} short-circuits the free-text/category
     * search with one of the preset operator shortcuts (feature #3 — filtre rapide):
     * "low_stock" (1..5 units), "out_of_stock" (0 units) or "no_image" (missing cover).
     * Any other value (including {@code null}/blank) leaves those shortcuts off.
     */
    @Transactional(readOnly = true)
    public Page<AdminProductDto> adminList(String search, String category, boolean inStock,
                                           String quickFilter, Pageable pageable) {
        boolean outOfStock = "out_of_stock".equals(quickFilter);
        boolean noImage = "no_image".equals(quickFilter);
        Integer lowStockAtMost = "low_stock".equals(quickFilter) ? LOW_STOCK_THRESHOLD : null;
        return productRepository.search(
                blankToNull(search), blankToNull(category), null, null, null, null,
                inStock, outOfStock, lowStockAtMost, noImage, false, pageable
        ).map(AdminProductDto::from);
    }

    /**
     * The catalogue as a spreadsheet: product, acquisition price, selling price
     * and stock on hand, sorted by name so the file reads like a shelf list.
     * <p>
     * Honours the same search box as the table, so the operator can export just
     * the rows currently being looked at. The row count is capped rather than
     * unbounded: an export is a report, not a database dump, and the whole sheet
     * has to be held in memory while it is built.
     *
     * @param search optional free-text filter, exactly as on the admin table
     * @param format "csv" for comma-separated output, anything else for .xlsx
     */
    @Transactional(readOnly = true)
    public byte[] exportProducts(String search, String format) {
        List<AdminProductDto> rows = productRepository.search(
                blankToNull(search), null, null, null, null, null, false, false, null, false, false,
                PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by("name").ascending())
        ).map(AdminProductDto::from).getContent();

        return "csv".equalsIgnoreCase(format)
                ? productExportService.toCsv(rows)
                : productExportService.toExcel(rows);
    }

    @Transactional(readOnly = true)
    public AdminProductDto adminGet(Long id) {
        return AdminProductDto.detail(findEntity(id));
    }

    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return productRepository.findAllCategories();
    }

    @Transactional(readOnly = true)
    public List<String> getBrands() {
        return productRepository.findAllBrands();
    }

    /**
     * The most populated categories, largest first, for the storefront tiles.
     * <p>
     * Categories whose name is purely numeric (for example "0") are rejected:
     * those are artefacts of malformed spreadsheet imports, not real
     * categories, and must never be advertised on the home page. Because such
     * rows can occupy the top of the ranking, the query is asked for a wider
     * window than requested and the result is trimmed after filtering.
     *
     * @param limit how many categories to return; clamped to 1..12
     */
    @Transactional(readOnly = true)
    public List<CategoryStatDto> getTopCategories(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 12));
        int window = Math.min(safeLimit * 3 + 5, 60);
        List<CategoryStatDto> result = new ArrayList<>();
        for (Object[] row : productRepository.findCategoryCounts(PageRequest.of(0, window))) {
            String name = row[0] == null ? null : row[0].toString().trim();
            if (name == null || name.isEmpty() || isNumeric(name)) {
                continue;
            }
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            result.add(new CategoryStatDto(name, count));
            if (result.size() == safeLimit) {
                break;
            }
        }
        return result;
    }

    /** True when every character is a digit, so the value is not a real category name. */
    private boolean isNumeric(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    @Transactional(readOnly = true)
    public Map<String, List<String>> getCategoryTree() {
        Map<String, List<String>> tree = new LinkedHashMap<>();
        for (Object[] pair : productRepository.findCategorySubcategoryPairs()) {
            String cat = (String) pair[0];
            String sub = (String) pair[1];
            List<String> subs = tree.computeIfAbsent(cat, k -> new ArrayList<>());
            if (sub != null && !sub.isBlank() && !subs.contains(sub)) {
                subs.add(sub);
            }
        }
        return tree;
    }

    public ProductDto create(ProductRequest req) {
        // Note: unlike update(), creation does not gate PRODUCTS_PRICE — price is
        // @NotNull/required on ProductRequest (a brand-new product needs an initial
        // price to be usable at all), so there is no "old price" being protected
        // here. The PRODUCTS_PRICE gate applies to *changing* an existing price
        // (see update() below), which is where an Editor account's guardrail matters.
        Product p = new Product();
        apply(p, req);
        Product saved = productRepository.save(p);
        auditService.log("PRODUCT_CREATED", "Product", saved.getId(),
                saved.getName() + " · stoc " + saved.getStockQuantity());
        return ProductDto.from(saved);
    }

    public ProductDto update(Long id, ProductRequest req) {
        Product p = findEntity(id);
        Integer oldStock = p.getStockQuantity();
        BigDecimal oldPrice = p.getPrice();

        // Feature #6 (granular permissions): editing a product's price specifically
        // needs PRODUCTS_PRICE — Editor accounts can update everything else about a
        // product (name, description, images, category...) but not the price.
        boolean requestChangesPrice = oldPrice == null ? req.price() != null
                : req.price() == null || oldPrice.compareTo(req.price()) != 0;
        if (requestChangesPrice && !permissionService.has("PRODUCTS_PRICE")) {
            throw new AccessDeniedException("Nu ai permisiunea de a modifica prețul acestui produs.");
        }

        apply(p, req);
        Product saved = productRepository.save(p);

        String details = saved.getName();
        boolean stockChanged = oldStock != null && !oldStock.equals(saved.getStockQuantity());
        boolean priceChanged = oldPrice != null && saved.getPrice() != null
                && oldPrice.compareTo(saved.getPrice()) != 0;
        if (stockChanged) {
            details += " · stoc " + oldStock + " → " + saved.getStockQuantity();
        }
        if (priceChanged) {
            details += " · preț " + oldPrice + " → " + saved.getPrice() + " RON";
        }
        auditService.log("PRODUCT_UPDATED", "Product", saved.getId(), details);

        // Dedicated, itemized entries — feature #5 (istoric prețuri / istoric stoc),
        // queried separately from the generic PRODUCT_UPDATED feed above so the
        // per-product history popup can show only price/stock changes.
        if (priceChanged) {
            auditService.log("PRODUCT_PRICE_CHANGED", "Product", saved.getId(),
                    oldPrice + " RON → " + saved.getPrice() + " RON");
        }
        if (stockChanged) {
            auditService.log("PRODUCT_STOCK_CHANGED", "Product", saved.getId(),
                    oldStock + " → " + saved.getStockQuantity() + " bucăți");
            // Feature #8 — instant "stoc redus" notification on the crossing (the periodic
            // sweep in NotificationService.reconcile() also catches products already low
            // before this edit, e.g. right after this feature ships).
            boolean crossedIntoLowStock = saved.getStockQuantity() > 0 && saved.getStockQuantity() < LOW_STOCK_THRESHOLD
                    && (oldStock == null || oldStock >= LOW_STOCK_THRESHOLD);
            if (crossedIntoLowStock) {
                notificationService.notifyLowStock(saved);
            }
        }
        return ProductDto.from(saved);
    }

    /** Activates or deactivates a product (feature #5 — hides it from the public storefront without deleting it). */
    public AdminProductDto setActive(Long id, boolean active) {
        Product p = findEntity(id);
        boolean changed = p.isActive() != active;
        p.setActive(active);
        Product saved = productRepository.save(p);
        if (changed) {
            auditService.log(active ? "PRODUCT_ACTIVATED" : "PRODUCT_DEACTIVATED",
                    "Product", saved.getId(), saved.getName());
            if (!active) {
                // Feature #8 — "produs inactiv" notification, feeds the admin notification center.
                notificationService.notifyProductDeactivated(saved);
            }
        }
        return AdminProductDto.from(saved);
    }

    public ProductDto updateImage(Long id, String imageUrl) {
        Product p = findEntity(id);
        p.setImageUrl(imageUrl);
        Product saved = productRepository.save(p);
        auditService.log("PRODUCT_IMAGE_UPDATED", "Product", saved.getId(), saved.getName());
        return ProductDto.from(saved);
    }

    /**
     * Removes a product outright — unless it has order or purchase history, in
     * which case a hard delete would corrupt every past invoice and
     * goods-in entry that references it (and the database would refuse it
     * anyway). In that case the product is deactivated instead
     * ({@link #setActive}'s existing "hide without deleting"), which is what
     * the operator actually wants: the item gone from the active catalogue,
     * with its accounting trail intact.
     *
     * @return {@code true} if the row was actually deleted, {@code false} if
     *         it was deactivated instead because of existing sales history
     */
    public boolean delete(Long id) {
        Product p = findEntity(id);
        String name = p.getName();
        if (hasSalesHistory(id)) {
            setActive(id, false);
            auditService.log("PRODUCT_DEACTIVATED_INSTEAD_OF_DELETE", "Product", id,
                    name + " — are comenzi sau achiziții înregistrate; dezactivat în loc de șters");
            return false;
        }
        // Remove hosted assets first, then the row (cascade drops the image rows).
        for (ProductImage img : p.getImages()) {
            cloudinaryService.delete(img.getPublicId());
        }
        productRepository.delete(p);
        auditService.log("PRODUCT_DELETED", "Product", id, name);
        return true;
    }

    /**
     * Deletes several products in one transaction and reports the outcome.
     * <p>
     * Identifiers are de-duplicated first. An id that no longer exists is not an
     * error for the batch as a whole — it is reported as "skipped" so the client
     * can refresh a stale table without the whole operation failing. Every
     * successful removal is written to the audit log individually, exactly as a
     * single delete would be, and a summary entry records the batch itself.
     * <p>
     * Same rule as {@link #delete(Long)}: a product with order or purchase
     * history is deactivated instead of hard-deleted, one row at a time — that
     * check runs *before* the delete for each id specifically so that one
     * referenced product does not throw a
     * {@code DataIntegrityViolationException} mid-loop and roll the whole
     * (class-level {@code @Transactional}) batch back, silently undoing every
     * deletion that already succeeded. Deactivated ids are reported separately
     * so the operator sees exactly what happened to each product, instead of
     * either a raw SQL error or a count that quietly includes rows that were
     * never actually removed.
     *
     * @param ids the products to remove
     * @return how many rows were deleted, which were deactivated instead
     *         because of sales history, and which ids were not found
     */
    public BulkDeleteResult deleteBulk(List<Long> ids) {
        List<Long> unique = new ArrayList<>(new LinkedHashSet<>(ids));
        List<Long> notFound = new ArrayList<>();
        List<Long> deactivated = new ArrayList<>();
        int deleted = 0;
        for (Long id : unique) {
            Product p = productRepository.findById(id).orElse(null);
            if (p == null) {
                notFound.add(id);
                continue;
            }
            String name = p.getName();
            if (hasSalesHistory(id)) {
                setActive(id, false);
                deactivated.add(id);
                auditService.log("PRODUCT_DEACTIVATED_INSTEAD_OF_DELETE", "Product", id,
                        name + " — are comenzi sau achiziții înregistrate; dezactivat în loc de șters");
                continue;
            }
            for (ProductImage img : p.getImages()) {
                cloudinaryService.delete(img.getPublicId());
            }
            productRepository.delete(p);
            auditService.log("PRODUCT_DELETED", "Product", id, name);
            deleted++;
        }
        auditService.log("PRODUCTS_BULK_DELETED", "Product", null,
                deleted + " produse șterse, " + deactivated.size()
                        + " dezactivate (aveau comenzi/achiziții) în masă");
        return new BulkDeleteResult(deleted, notFound, deactivated);
    }

    /**
     * Outcome of a batch delete.
     *
     * @param deleted     number of rows actually removed
     * @param notFound    identifiers that no longer existed when the batch ran
     * @param deactivated identifiers that had order or purchase history and were
     *                    deactivated instead of removed, so the operator knows
     *                    which ones are still in the database (inactive) rather
     *                    than gone
     */
    public record BulkDeleteResult(int deleted, List<Long> notFound, List<Long> deactivated) {
    }

    /**
     * Permanently removes a product together with every order/purchase line
     * item that ever referenced it — the explicit, irreversible override of
     * the safety net in {@link #delete(Long)}. Requesting this means
     * accepting that historical invoices and goods-in records will show
     * fewer items than they did at the time of sale/intake; every affected
     * order's and purchase's {@code totalAmount} is recalculated from its
     * remaining lines so the stored total never silently drifts from what
     * the line items actually sum to.
     * <p>
     * Each {@link OrderItem}/{@link PurchaseItem} is removed from its
     * owning {@link Order}'s/{@link Purchase}'s item list rather than
     * deleted directly through its own repository — both parent
     * associations are mapped with {@code orphanRemoval = true}, so removing
     * the child from the parent's collection and saving the parent is what
     * makes Hibernate issue the row deletion, exactly like
     * {@link #deleteImage(Long, Long)} already does for a product's own
     * image gallery.
     * <p>
     * Gated behind {@code PRODUCTS_FORCE_DELETE} at the controller layer —
     * a permission distinct from and stronger than {@code PRODUCTS_DELETE} —
     * because unlike every other write in this service, this one cannot be
     * undone by re-editing or re-importing data: the historical rows are
     * physically gone.
     *
     * @return how many order lines and how many purchase lines were removed
     *         along with the product, so the caller can report exactly what
     *         was lost
     */
    public ForceDeleteOutcome forceDeleteWithHistory(Long id) {
        Product p = findEntity(id);
        String name = p.getName();

        List<OrderItem> orderItems = orderItemRepository.findByProductId(id);
        Set<Order> affectedOrders = new LinkedHashSet<>();
        for (OrderItem item : orderItems) {
            Order order = item.getOrder();
            order.getItems().remove(item);
            affectedOrders.add(order);
        }
        for (Order order : affectedOrders) {
            order.recalculateTotal();
            orderRepository.save(order);
        }

        List<PurchaseItem> purchaseItems = purchaseItemRepository.findByProductId(id);
        Set<Purchase> affectedPurchases = new LinkedHashSet<>();
        for (PurchaseItem item : purchaseItems) {
            Purchase purchase = item.getPurchase();
            purchase.getItems().remove(item);
            affectedPurchases.add(purchase);
        }
        for (Purchase purchase : affectedPurchases) {
            purchase.recalculateTotal();
            purchaseRepository.save(purchase);
        }

        for (ProductImage img : p.getImages()) {
            cloudinaryService.delete(img.getPublicId());
        }
        productRepository.delete(p);

        auditService.log("PRODUCT_FORCE_DELETED_WITH_HISTORY", "Product", id,
                name + " — șters definitiv împreună cu istoricul: " + orderItems.size()
                        + " linie(i) de comandă (" + affectedOrders.size() + " comandă/comenzi recalculate) și "
                        + purchaseItems.size() + " linie(i) de achiziție (" + affectedPurchases.size()
                        + " achiziție/achiziții recalculate) eliminate ireversibil.");

        return new ForceDeleteOutcome(orderItems.size(), purchaseItems.size());
    }

    /**
     * Outcome of a single force-delete: how many historical line items were
     * removed along with the product, so the confirmation message can state
     * exactly what was lost.
     *
     * @param orderItemsRemoved    order lines removed
     * @param purchaseItemsRemoved purchase lines removed
     */
    public record ForceDeleteOutcome(int orderItemsRemoved, int purchaseItemsRemoved) {
    }

    /**
     * Force-deletes several products at once — offered only for the subset
     * of a previous {@link #deleteBulk} response that came back deactivated
     * because of sales history, when the operator explicitly chooses to
     * remove them anyway. Mirrors {@link #forceDeleteWithHistory(Long)} per
     * id; an id that no longer exists is skipped rather than failing the
     * whole batch, exactly like {@link #deleteBulk}.
     *
     * @param ids the products to force-delete
     * @return how many were removed, which ids were skipped, and the total
     *         historical line items removed across the whole batch
     */
    public BulkForceDeleteResult forceDeleteBulk(List<Long> ids) {
        List<Long> unique = new ArrayList<>(new LinkedHashSet<>(ids));
        List<Long> notFound = new ArrayList<>();
        int deleted = 0;
        int totalOrderItemsRemoved = 0;
        int totalPurchaseItemsRemoved = 0;
        for (Long id : unique) {
            if (!productRepository.existsById(id)) {
                notFound.add(id);
                continue;
            }
            ForceDeleteOutcome outcome = forceDeleteWithHistory(id);
            totalOrderItemsRemoved += outcome.orderItemsRemoved();
            totalPurchaseItemsRemoved += outcome.purchaseItemsRemoved();
            deleted++;
        }
        auditService.log("PRODUCTS_BULK_FORCE_DELETED_WITH_HISTORY", "Product", null,
                deleted + " produse șterse definitiv în masă, împreună cu " + totalOrderItemsRemoved
                        + " linii de comandă și " + totalPurchaseItemsRemoved
                        + " linii de achiziție eliminate ireversibil.");
        return new BulkForceDeleteResult(deleted, notFound, totalOrderItemsRemoved, totalPurchaseItemsRemoved);
    }

    /**
     * Outcome of a batch force-delete.
     *
     * @param deleted              number of products actually removed
     * @param notFound             identifiers that no longer existed when the batch ran
     * @param orderItemsRemoved    total order lines removed across every product in the batch
     * @param purchaseItemsRemoved total purchase lines removed across every product in the batch
     */
    public record BulkForceDeleteResult(int deleted, List<Long> notFound, int orderItemsRemoved,
                                         int purchaseItemsRemoved) {
    }

    /**
     * Activates or deactivates several products in one transaction (batch-selection
     * toolbar in the admin products table). Mirrors {@link #deleteBulk}: ids are
     * de-duplicated first, and an id that no longer exists is reported as "skipped"
     * rather than failing the whole batch. Only products whose state actually
     * changes get an individual audit entry and (when deactivating) a notification,
     * exactly like the single-product {@link #setActive} — a product already in the
     * requested state is left untouched and does not spam the audit log.
     *
     * @param ids    the products to update
     * @param active {@code true} to activate, {@code false} to deactivate
     * @return how many rows were actually changed and which ids were not found
     */
    public BulkActivateResult setActiveBulk(List<Long> ids, boolean active) {
        List<Long> unique = new ArrayList<>(new LinkedHashSet<>(ids));
        List<Long> notFound = new ArrayList<>();
        int updated = 0;
        for (Long id : unique) {
            Product p = productRepository.findById(id).orElse(null);
            if (p == null) {
                notFound.add(id);
                continue;
            }
            if (p.isActive() == active) {
                continue;
            }
            p.setActive(active);
            Product saved = productRepository.save(p);
            auditService.log(active ? "PRODUCT_ACTIVATED" : "PRODUCT_DEACTIVATED",
                    "Product", saved.getId(), saved.getName());
            if (!active) {
                notificationService.notifyProductDeactivated(saved);
            }
            updated++;
        }
        auditService.log(active ? "PRODUCTS_BULK_ACTIVATED" : "PRODUCTS_BULK_DEACTIVATED", "Product", null,
                updated + (active ? " produse activate în masă" : " produse dezactivate în masă"));
        return new BulkActivateResult(updated, notFound);
    }

    /**
     * Outcome of a batch activate/deactivate.
     *
     * @param updated  number of rows whose active state actually changed
     * @param notFound identifiers that no longer existed when the batch ran
     */
    public record BulkActivateResult(int updated, List<Long> notFound) {
    }

    // ==============================================================
    //  Product image gallery (Cloudinary-hosted) — feature #5
    // ==============================================================

    /** Uploads one or more images to Cloudinary and attaches them to the product. */
    public ProductDto addImages(Long id, MultipartFile[] files) {
        Product p = findEntity(id);
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("Nu ai selectat nicio imagine.");
        }
        int nextPos = p.getImages().stream().mapToInt(ProductImage::getPosition).max().orElse(-1) + 1;
        int added = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            validateImage(file);
            CloudinaryService.UploadResult res =
                    cloudinaryService.upload(file, "electroshop/products/" + id);
            ProductImage img = new ProductImage(p, res.url(), res.publicId(), nextPos++);
            img.setWidth(res.width());
            img.setHeight(res.height());
            img.setFormat(res.format());
            img.setBytes(res.bytes());
            // First image on a product with no cover becomes the primary/cover.
            if (p.getImages().isEmpty() && !hasPrimary(p)) {
                img.setPrimary(true);
                p.setImageUrl(res.url());
            }
            p.getImages().add(img);
            added++;
        }
        if (added == 0) {
            throw new IllegalArgumentException("Fișierele trimise sunt goale.");
        }
        Product saved = productRepository.save(p);
        auditService.log("PRODUCT_IMAGE_ADDED", "Product", saved.getId(),
                saved.getName() + " · " + added + " imagine(i)");
        return ProductDto.detail(saved);
    }

    /** Deletes one image (from Cloudinary + DB), promoting a new cover if needed. */
    public ProductDto deleteImage(Long id, Long imageId) {
        Product p = findEntity(id);
        ProductImage target = p.getImages().stream()
                .filter(i -> i.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("ProductImage", imageId));
        boolean wasPrimary = target.isPrimary();
        cloudinaryService.delete(target.getPublicId());
        p.getImages().remove(target); // orphanRemoval deletes the row
        if (wasPrimary) {
            ProductImage next = p.getImages().stream().findFirst().orElse(null);
            if (next != null) {
                next.setPrimary(true);
                p.setImageUrl(next.getUrl());
            } else {
                p.setImageUrl(null);
            }
        }
        Product saved = productRepository.save(p);
        auditService.log("PRODUCT_IMAGE_DELETED", "Product", saved.getId(), saved.getName());
        return ProductDto.detail(saved);
    }

    /** Marks one image as the primary/cover. */
    public ProductDto setPrimaryImage(Long id, Long imageId) {
        Product p = findEntity(id);
        ProductImage target = p.getImages().stream()
                .filter(i -> i.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("ProductImage", imageId));
        for (ProductImage img : p.getImages()) {
            img.setPrimary(img == target);
        }
        p.setImageUrl(target.getUrl());
        Product saved = productRepository.save(p);
        auditService.log("PRODUCT_IMAGE_PRIMARY", "Product", saved.getId(), saved.getName());
        return ProductDto.detail(saved);
    }

    /**
     * Reorders the product's image gallery (drag & drop in the admin UI). The
     * caller must supply the full, ordered list of image ids belonging to the
     * product — a partial list is rejected so the gallery can never end up with
     * duplicate or missing positions.
     */
    public ProductDto reorderImages(Long id, List<Long> orderedImageIds) {
        Product p = findEntity(id);
        if (orderedImageIds == null || orderedImageIds.isEmpty()) {
            throw new IllegalArgumentException("Lista de imagini pentru reordonare este goală.");
        }
        Set<Long> current = p.getImages().stream().map(ProductImage::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> requested = new LinkedHashSet<>(orderedImageIds);
        if (requested.size() != orderedImageIds.size() || !requested.equals(current)) {
            throw new IllegalArgumentException(
                    "Lista de reordonare trebuie să conțină exact imaginile existente ale produsului, fără duplicate.");
        }
        Map<Long, ProductImage> byId = new LinkedHashMap<>();
        for (ProductImage img : p.getImages()) {
            byId.put(img.getId(), img);
        }
        int pos = 0;
        for (Long imageId : orderedImageIds) {
            byId.get(imageId).setPosition(pos++);
        }
        Product saved = productRepository.save(p);
        auditService.log("PRODUCT_IMAGE_REORDERED", "Product", saved.getId(), saved.getName());
        return ProductDto.detail(saved);
    }

    private boolean hasPrimary(Product p) {
        return p.getImages().stream().anyMatch(ProductImage::isPrimary);
    }

    private void validateImage(MultipartFile file) {
        String type = file.getContentType();
        if (type == null || !ALLOWED_IMAGE_TYPES.contains(type.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Format neacceptat: " + type + ". Sunt permise doar JPG, PNG și WebP.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Imaginea depășește limita de 5 MB.");
        }
    }

    public Product findEntity(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }

    private void apply(Product p, ProductRequest req) {
        p.setName(req.name());
        p.setDescription(req.description());
        p.setPrice(req.price());
        p.setStockQuantity(req.stockQuantity());
        p.setCategory(req.category());
        p.setSubcategory(req.subcategory());
        p.setBrand(req.brand());
        p.setPurchasePrice(req.purchasePrice());
        p.setSku(req.sku());
        if (req.imageUrl() != null) {
            p.setImageUrl(req.imageUrl());
        }
    }
}
