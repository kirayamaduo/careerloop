package com.group1.career.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.group1.career.service.BodyLanguageService;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Streaming JSON limits apply before a giant string is materialized in heap.
 * The body-language frame is the largest legitimate JSON string accepted by
 * this API; files use multipart endpoints with their own limits.
 */
@Configuration
public class HttpJsonLimitsConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer requestJsonStreamConstraintsCustomizer() {
        return builder -> builder.postConfigurer(mapper ->
                mapper.getFactory().setStreamReadConstraints(
                        StreamReadConstraints.builder()
                                .maxStringLength(BodyLanguageService.MAX_BASE64_CHARS)
                                .build()));
    }
}
