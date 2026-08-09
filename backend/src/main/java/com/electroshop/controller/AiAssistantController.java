package com.electroshop.controller;

import com.electroshop.dto.AiInsightsDto;
import com.electroshop.dto.ApiResponse;
import com.electroshop.model.Product;
import com.electroshop.repository.ProductRepository;
import com.electroshop.service.AiInsightService;
import com.electroshop.service.AiTextGenerator;
import com.electroshop.service.MetricRange;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * The administrator assistant panel.
 *
 * <p>Task 7. Full paths are {@code /api/admin/ai/insights} and
 * {@code /api/admin/ai/describe}.</p>
 *
 * <p>Both endpoints report which engine produced their output. That label is not
 * decoration: a suggestion derived from the store's own figures and a sentence written by
 * a language model have different failure modes, and an operator deciding whether to act
 * on one needs to know which they are reading. Today the answer is the rules engine and
 * the attribute-based generator, and the response says so.</p>
 */
@RestController
@RequestMapping("/admin/ai")
public class AiAssistantController {

    private final AiInsightService aiInsightService;
    private final AiTextGenerator textGenerator;
    private final ProductRepository productRepository;

    public AiAssistantController(AiInsightService aiInsightService,
                                 AiTextGenerator textGenerator,
                                 ProductRepository productRepository) {
        this.aiInsightService = aiInsightService;
        this.textGenerator = textGenerator;
        this.productRepository = productRepository;
    }

    /**
     * Automated suggestions and order-pattern analysis.
     *
     * <p>{@code GET /api/admin/ai/insights?range=30d}</p>
     *
     * <p>Requires {@code METRICS_VIEW}: the suggestions quote purchase prices, margins
     * and per-product profit, which is the same material the metrics endpoints protect.
     * Putting them behind a weaker permission would route around that decision.</p>
     */
    @GetMapping("/insights")
    @PreAuthorize("@permissionService.has('METRICS_VIEW')")
    public ResponseEntity<ApiResponse<AiInsightsDto>> insights(
            @RequestParam(name = "range", required = false) String range) {
        MetricRange resolved = MetricRange.parse(range, MetricRange.D30);
        return ResponseEntity.ok(ApiResponse.ok(aiInsightService.insights(resolved)));
    }

    /**
     * Composes a description for a product.
     *
     * <p>{@code POST /api/admin/ai/describe}</p>
     *
     * <p>Accepts either an existing product's id or a set of attributes for one not yet
     * saved, because the requirement is about new products and a new product has no id
     * until it is created. When an id is supplied the attributes are read from the
     * database rather than from the request: a caller-supplied brand would let the
     * generated text describe a product the catalogue does not contain.</p>
     *
     * <p>The result is never written anywhere. It comes back for a person to read, edit
     * and save deliberately — a generator that published straight to the catalogue would
     * put unreviewed text in front of customers.</p>
     */
    @PostMapping("/describe")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<AiTextGenerator.Result>> describe(
            @RequestBody DescribeRequest request) {

        AiTextGenerator.Request generatorRequest = request != null && request.productId() != null
                ? fromProduct(request.productId())
                : fromRequest(request);

        return ResponseEntity.ok(ApiResponse.ok(textGenerator.describe(generatorRequest)));
    }

    private AiTextGenerator.Request fromProduct(Long productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return new AiTextGenerator.Request(null, null, null, null, null, null, null);
        }
        return new AiTextGenerator.Request(
                product.getName(),
                product.getBrand(),
                product.getCategory(),
                product.getSubcategory(),
                product.getPrice(),
                product.getSku(),
                product.getDescription());
    }

    private static AiTextGenerator.Request fromRequest(DescribeRequest request) {
        if (request == null) {
            return new AiTextGenerator.Request(null, null, null, null, null, null, null);
        }
        return new AiTextGenerator.Request(
                request.name(),
                request.brand(),
                request.category(),
                request.subcategory(),
                request.price(),
                request.sku(),
                request.existing());
    }

    /**
     * What the description endpoint accepts.
     *
     * <p>{@code productId} takes precedence over every other field. The rest exist for a
     * product being created, which has no id yet.</p>
     */
    public record DescribeRequest(
            Long productId,
            String name,
            String brand,
            String category,
            String subcategory,
            BigDecimal price,
            String sku,
            String existing
    ) {}
}
