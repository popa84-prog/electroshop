package com.electroshop.config;

import com.electroshop.service.AiTextGenerator;
import com.electroshop.service.TemplateAiTextGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the text generator the AI assistant panel writes through.
 *
 * <h2>Why this configuration class exists at all</h2>
 *
 * <p>{@link TemplateAiTextGenerator} is the adapter that ships: it composes a product
 * description out of the attributes the catalogue already records, and it cannot
 * fabricate a specification because it is never given one. It is deliberately the
 * fallback rather than the fixture — the moment this project gains a real language-model
 * provider, that provider should take over without anybody editing this file's
 * neighbours.</p>
 *
 * <p>{@link ConditionalOnMissingBean} expresses exactly that: define any other
 * {@link AiTextGenerator} bean anywhere in the application and this one steps aside.
 * The annotation was originally written on the adapter itself, next to a
 * {@code @Service}. That combination is the reason the context refused to start with
 * <em>"No qualifying bean of type AiTextGenerator available"</em>: on a component-scanned
 * class the condition is evaluated during scanning, before the registry it is asking
 * about has been populated, and Spring's own documentation limits the annotation to
 * auto-configuration classes for precisely this reason. The result was not "the adapter
 * wins by default" but "no adapter is registered at all", and
 * {@code AiAssistantController} could not be constructed.</p>
 *
 * <p>On a {@code @Bean} method inside a {@code @Configuration} class the same annotation
 * is evaluated after component scanning has finished, which is the ordering it was
 * designed for. The intent is unchanged; only the placement is now one Spring supports.</p>
 */
@Configuration
public class AiTextGeneratorConfig {

    /**
     * The default adapter, registered only when the application defines no other.
     *
     * @return a generator that restates catalogue facts and never invents specifications
     */
    @Bean
    @ConditionalOnMissingBean(AiTextGenerator.class)
    public AiTextGenerator templateAiTextGenerator() {
        return new TemplateAiTextGenerator();
    }
}
