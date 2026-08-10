package com.electroshop.service;

/**
 * The port through which product descriptions are generated.
 *
 * <p>Task 7 asks for automatic product descriptions. The project has no language-model
 * provider configured, no API key, and no budget line for one, and inventing all three
 * would mean shipping code that cannot run and secrets that do not exist.</p>
 *
 * <p>So generation is expressed as a port in the Ports and Adapters sense. The
 * application depends on this interface; {@link TemplateAiTextGenerator} is the adapter
 * that ships, and it composes a description from the product's own recorded attributes.
 * The day a provider is configured, a second adapter implements this interface and
 * becomes the active bean — no controller, no service and no frontend component
 * changes.</p>
 *
 * <p>{@link #sourceName()} exists so the distinction survives that swap. A description
 * assembled from stored attributes and one written by a model are different objects
 * with different failure modes, and the panel labels which it is rather than letting
 * them blend.</p>
 */
public interface AiTextGenerator {

    /**
     * Composes a description for a product.
     *
     * @param request everything known about the product
     * @return the description, never null; an implementation with nothing to work from
     *         returns an empty result rather than a plausible-sounding invention
     */
    Result describe(Request request);

    /**
     * Which engine produced the text, for display beside it.
     *
     * @return a short stable identifier such as {@code TEMPLATE}
     */
    String sourceName();

    /**
     * What the generator is told about a product.
     *
     * <p>Only fields the catalogue actually stores. A request carrying attributes the
     * database does not have would force every caller to supply them from somewhere,
     * and "somewhere" would end up being a guess.</p>
     *
     * @param name        product name
     * @param brand       brand as recorded, may be null
     * @param category    category as recorded, may be null
     * @param subcategory subcategory as recorded, may be null
     * @param price       selling price, may be null
     * @param sku         stock keeping unit, may be null
     * @param existing    the current description, so a generator can improve rather than
     *                    replace; may be null
     */
    record Request(
            String name,
            String brand,
            String category,
            String subcategory,
            java.math.BigDecimal price,
            String sku,
            String existing
    ) {}

    /**
     * What came back.
     *
     * @param text       the description
     * @param source     which engine produced it
     * @param confidence {@code HIGH}, {@code MEDIUM} or {@code LOW} — how much the
     *                   generator had to work with, so a description assembled from a
     *                   name alone is not presented with the same authority as one built
     *                   from a full attribute set
     * @param note       a plain-Romanian sentence about how it was produced, shown under
     *                   the text; never empty, because a generated description that does
     *                   not say it was generated is one somebody will publish believing
     *                   a person wrote it
     */
    record Result(
            String text,
            String source,
            String confidence,
            String note
    ) {}
}
