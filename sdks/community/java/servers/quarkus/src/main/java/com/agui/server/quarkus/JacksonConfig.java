package com.agui.server.quarkus;

import com.agui.core.message.BaseMessage;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped // Replaced @Singleton
public class JacksonConfig implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper objectMapper) {
        configure(objectMapper);
    }

    /**
     * CDI Observer method.
     * This forces Quarkus to eagerly invoke this configuration when the
     * system-wide ObjectMapper is created, preventing lazy-loading bypass.
     */
    public void observeObjectMapper(@Observes ObjectMapper objectMapper) {
        configure(objectMapper);
    }

    private void configure(ObjectMapper objectMapper) {
        // Enforce case-insensitive subtype matching (e.g., "user" matches "USER")
        objectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES);

        // Tells Jackson: Apply BaseMessageMixin rules to BaseMessage target
        objectMapper.addMixIn(BaseMessage.class, BaseMessageMixin.class);
    }
}