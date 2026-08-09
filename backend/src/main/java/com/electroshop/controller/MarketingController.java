package com.electroshop.controller;

import com.electroshop.dto.ApiResponse;
import com.electroshop.dto.MarketingPerformanceDto;
import com.electroshop.service.MarketingPerformanceService;
import com.electroshop.service.MetricRange;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Campaign performance, and the public endpoint that collects the data behind it.
 *
 * <p>Task 17. Full paths are {@code /api/marketing/performance} and
 * {@code /api/marketing/track}.</p>
 *
 * <h2>Why tracking is unauthenticated, and what protects it</h2>
 *
 * <p>{@code /track} is called by the storefront for every visitor, most of whom are not
 * logged in. Requiring authentication would restrict campaign measurement to customers
 * with accounts, which is the smaller and less interesting half of the traffic.</p>
 *
 * <p>So the endpoint is open, and it is built to be safe while open. It accepts three
 * event types and nothing else. It writes only to a table that holds counts. The session
 * hash is validated to a short alphanumeric token and is never linked to an account, so
 * the endpoint cannot be used to store content or to correlate a person. Impressions are
 * de-duplicated per session, so repeated calls cannot inflate a figure. And the response
 * carries no body: an endpoint that told the caller whether an offer id exists would be
 * a way to enumerate them.</p>
 *
 * <p>What remains is that a determined caller can add clicks to a campaign. That is
 * inherent to any client-side analytics and is why these numbers inform decisions rather
 * than settle them; the conversion figures, which are the ones that matter commercially,
 * are anchored to real orders.</p>
 */
@RestController
@RequestMapping("/marketing")
public class MarketingController {

    private final MarketingPerformanceService marketingPerformanceService;

    public MarketingController(MarketingPerformanceService marketingPerformanceService) {
        this.marketingPerformanceService = marketingPerformanceService;
    }

    /**
     * Impressions, clicks, conversions, CTR and cost per acquisition.
     *
     * <p>{@code GET /api/marketing/performance?range=30d}</p>
     */
    @GetMapping("/performance")
    @PreAuthorize("@permissionService.has('MARKETING_VIEW')")
    public ResponseEntity<ApiResponse<MarketingPerformanceDto>> performance(
            @RequestParam(name = "range", required = false) String range) {
        MetricRange resolved = MetricRange.parse(range, MetricRange.D30);
        return ResponseEntity.ok(ApiResponse.ok(
                marketingPerformanceService.performance(resolved)));
    }

    /**
     * Records one interaction with an offer.
     *
     * <p>{@code POST /api/marketing/track}</p>
     *
     * <p>Always answers 204, whatever happened. A caller learning that an offer id was
     * rejected learns that the id does not exist, which turns this into an enumeration
     * tool; and a storefront that had to handle an error from an analytics beacon would
     * be a storefront where a failed beacon breaks a page.</p>
     */
    @PostMapping("/track")
    public ResponseEntity<Void> track(@RequestBody TrackRequest request) {
        if (request != null && request.type() != null) {
            switch (request.type().toUpperCase(java.util.Locale.ROOT)) {
                case "IMPRESSION" ->
                        marketingPerformanceService.trackImpression(request.offerId(), request.session());
                case "CLICK" ->
                        marketingPerformanceService.trackClick(request.offerId(), request.session());
                default -> {
                    // Conversions are recorded server-side when an order is placed, never
                    // from the browser. Accepting one here would let any caller attribute
                    // arbitrary revenue to any campaign.
                }
            }
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * What the storefront beacon sends.
     *
     * @param offerId which offer
     * @param type    {@code IMPRESSION} or {@code CLICK}
     * @param session an opaque per-session token, validated by the service
     */
    public record TrackRequest(Long offerId, String type, String session) {}
}
