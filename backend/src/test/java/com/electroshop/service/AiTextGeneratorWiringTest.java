package com.electroshop.service;

import com.electroshop.config.AiTextGeneratorConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the one wiring mistake that took the whole application down.
 *
 * <p>{@link TemplateAiTextGenerator} once carried {@code @Service} together with
 * {@code @ConditionalOnMissingBean(AiTextGenerator.class)}. That combination is not
 * supported: on a component-scanned class the condition is evaluated during scanning,
 * before the bean registry it interrogates has been filled, so instead of "register this
 * adapter unless a better one exists" it produced "register nothing at all". The context
 * then failed with <em>No qualifying bean of type AiTextGenerator available</em> while
 * constructing {@code AiAssistantController}, the health check never passed, and Railway
 * rolled the deployment back.</p>
 *
 * <p>A compiler cannot catch this — both annotations are legal in isolation — and no
 * unit test of the generator's output would notice, because the generator itself was
 * always correct. The defect lived entirely in where the annotation was written. These
 * assertions encode the placement rule so the mistake cannot be reintroduced quietly.</p>
 */
class AiTextGeneratorWiringTest {

    @Test
    void theAdapterCarriesNoStereotypeOfItsOwn() {
        // A stereotype here would register the adapter twice: once by component scanning
        // and once by the configuration below, which is an ambiguity the container
        // reports only at startup.
        assertFalse(hasAnnotationNamed(TemplateAiTextGenerator.class, "Service"),
                "TemplateAiTextGenerator nu trebuie adnotat @Service: este inregistrat de AiTextGeneratorConfig");
        assertFalse(hasAnnotationNamed(TemplateAiTextGenerator.class, "Component"),
                "TemplateAiTextGenerator nu trebuie adnotat @Component");
    }

    @Test
    void theConditionIsNotWrittenOnTheScannedClass() {
        // This is the exact shape of the outage.
        assertFalse(TemplateAiTextGenerator.class.isAnnotationPresent(ConditionalOnMissingBean.class),
                "@ConditionalOnMissingBean pe o clasa scanata se evalueaza inainte de popularea registrului");
    }

    @Test
    void theConfigurationClassRegistersTheAdapter() {
        assertTrue(AiTextGeneratorConfig.class.isAnnotationPresent(Configuration.class),
                "AiTextGeneratorConfig trebuie sa fie @Configuration");

        Method factory = null;
        for (Method m : AiTextGeneratorConfig.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Bean.class) && AiTextGenerator.class.isAssignableFrom(m.getReturnType())) {
                factory = m;
                break;
            }
        }

        assertNotNull(factory, "lipseste metoda @Bean care furnizeaza un AiTextGenerator");
        assertTrue(Modifier.isPublic(factory.getModifiers()), "metoda @Bean trebuie sa fie publica");
        assertTrue(factory.isAnnotationPresent(ConditionalOnMissingBean.class),
                "metoda @Bean trebuie sa poarte @ConditionalOnMissingBean, ca un furnizor real sa o poata inlocui");
    }

    @Test
    void theFactoryProducesAWorkingGenerator() {
        // The registration is only useful if what it returns actually generates text.
        AiTextGenerator generator = new AiTextGeneratorConfig().templateAiTextGenerator();

        assertNotNull(generator);
        assertTrue(generator instanceof TemplateAiTextGenerator);
    }

    private static boolean hasAnnotationNamed(Class<?> type, String simpleName) {
        for (java.lang.annotation.Annotation a : type.getAnnotations()) {
            if (a.annotationType().getSimpleName().equals(simpleName)) {
                return true;
            }
        }
        return false;
    }
}
