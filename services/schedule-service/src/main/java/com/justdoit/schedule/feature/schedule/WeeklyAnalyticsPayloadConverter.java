package com.justdoit.schedule.feature.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mantém o snapshot extensível sem espalhar colunas específicas de gráfico. */
@Converter
public class WeeklyAnalyticsPayloadConverter implements AttributeConverter<WeeklyAnalyticsPayload, String> {

    private static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    @Override
    public String convertToDatabaseColumn(WeeklyAnalyticsPayload payload) {
        if (payload == null) return null;
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível serializar o snapshot semanal", e);
        }
    }

    @Override
    public WeeklyAnalyticsPayload convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, WeeklyAnalyticsPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível ler o snapshot semanal", e);
        }
    }
}
