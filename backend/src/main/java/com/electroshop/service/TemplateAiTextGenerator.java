package com.electroshop.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * The description generator that ships: composition from the product's own attributes.
 *
 * <p>The adapter behind {@link AiTextGenerator} when no language-model provider is
 * configured, which is the current state of this project. It writes a short description
 * from what the catalogue actually records — name, brand, category, price — and says so
 * underneath.</p>
 *
 * <p><b>Why this is the right default rather than a placeholder.</b> A model given only
 * a product name and asked for marketing copy will supply specifications: a battery
 * life, a warranty term, a material. None of those are in the request because none of
 * them are in the database, so all of them would be invented, and they would be
 * invented in fluent, confident Romanian that reads exactly like the true parts. A
 * description that fabricates a specification is a description that produces a return
 * and, depending on the claim, a consumer-protection problem. This generator can only
 * restate facts it was given, which makes it strictly worse prose and strictly safer
 * output.</p>
 *
 * <p>{@link ConditionalOnMissingBean} means adding a real provider is a matter of
 * defining one bean: this adapter steps aside automatically rather than needing to be
 * removed.</p>
 */
@Service
@ConditionalOnMissingBean(AiTextGenerator.class)
public class TemplateAiTextGenerator implements AiTextGenerator {

    private static final String NOTE =
            "Text compus automat din atributele înregistrate ale produsului (denumire, "
                    + "marcă, categorie, preț). Nu conține specificații care nu se află în "
                    + "catalog — verificați și completați înainte de publicare.";

    @Override
    public Result describe(Request request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            // Nothing to work from. An empty result rather than a generic paragraph:
            // a description that would be identical for every product is not a
            // description, and shipping one invites it being published unread.
            return new Result("", sourceName(), "LOW",
                    "Produsul nu are denumire, deci nu se poate compune nicio descriere.");
        }

        List<String> sentences = new ArrayList<>();
        int knownAttributes = 0;

        String name = request.name().trim();
        String brand = blankToNull(request.brand());
        String category = blankToNull(request.category());
        String subcategory = blankToNull(request.subcategory());

        if (brand != null && category != null) {
            sentences.add(String.format("%s este un produs %s din categoria %s.",
                    name, brand, category.toLowerCase(java.util.Locale.ROOT)));
            knownAttributes += 2;
        } else if (brand != null) {
            sentences.add(String.format("%s este un produs marca %s.", name, brand));
            knownAttributes++;
        } else if (category != null) {
            sentences.add(String.format("%s face parte din categoria %s.",
                    name, category.toLowerCase(java.util.Locale.ROOT)));
            knownAttributes++;
        } else {
            sentences.add(name + ".");
        }

        if (subcategory != null) {
            sentences.add(String.format("Se încadrează în subcategoria %s.",
                    subcategory.toLowerCase(java.util.Locale.ROOT)));
            knownAttributes++;
        }

        if (request.price() != null) {
            sentences.add(String.format("Preț: %s %s.",
                    request.price().stripTrailingZeros().toPlainString(),
                    MetricsService.CURRENCY));
            knownAttributes++;
        }

        if (blankToNull(request.sku()) != null) {
            sentences.add(String.format("Cod produs: %s.", request.sku().trim()));
            knownAttributes++;
        }

        // The closing line is the only sentence not derived from a stored value, and it
        // makes no claim about the product — it describes the shop's own terms, which
        // are the same for everything in the catalogue.
        sentences.add("Produsul beneficiază de garanție conform politicii magazinului "
                + "și poate fi returnat în termenul legal.");

        String confidence = knownAttributes >= 4 ? "HIGH"
                : knownAttributes >= 2 ? "MEDIUM"
                : "LOW";

        return new Result(String.join(" ", sentences), sourceName(), confidence, NOTE);
    }

    @Override
    public String sourceName() {
        return "TEMPLATE";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
