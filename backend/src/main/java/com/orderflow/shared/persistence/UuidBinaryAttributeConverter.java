package com.orderflow.shared.persistence;

import com.orderflow.shared.id.UuidV7Generator;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.UUID;

/**
 * Conversor JPA automático que garante persistência de {@link UUID} como {@code BINARY(16)}
 * em todas as entidades do monólito.
 */
@Converter(autoApply = true)
public class UuidBinaryAttributeConverter implements AttributeConverter<UUID, byte[]> {

    @Override
    public byte[] convertToDatabaseColumn(UUID attribute) {
        return UuidV7Generator.toBytes(attribute);
    }

    @Override
    public UUID convertToEntityAttribute(byte[] dbData) {
        return UuidV7Generator.fromBytes(dbData);
    }
}
