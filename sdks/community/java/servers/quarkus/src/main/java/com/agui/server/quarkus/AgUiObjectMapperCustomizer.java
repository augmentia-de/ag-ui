package com.agui.server.quarkus;

import com.agui.json.ObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

@Singleton
public class AgUiObjectMapperCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper objectMapper) {
        ObjectMapperFactory.addMixins(objectMapper);
    }
}
