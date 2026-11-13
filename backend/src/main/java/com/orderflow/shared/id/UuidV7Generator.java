package com.orderflow.shared.id;

import com.github.f4b6a3.uuid.UuidCreator;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.UUID;

/**
 * Utilitário central para geração de UUIDv7 (RFC 9562 — ordenável por tempo)
 * e conversão determinística entre {@link UUID} e {@code BINARY(16)} do MySQL/InnoDB.
 */
public final class UuidV7Generator {

    private UuidV7Generator() {
    }

    /**
     * Gera um novo UUIDv7 monotônico ordenável por timestamp.
     */
    public static UUID nextId() {
        return UuidCreator.getTimeOrderedEpoch();
    }

    /**
     * Converte um {@link UUID} para um array de 16 bytes em big-endian (BINARY(16)).
     */
    public static byte[] toBytes(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    /**
     * Reconstrói um {@link UUID} a partir de um array de 16 bytes (BINARY(16)).
     */
    public static UUID fromBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (bytes.length != 16) {
            throw new IllegalArgumentException("BINARY(16) inválido: esperado 16 bytes, recebido " + bytes.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long mostSigBits = buffer.getLong();
        long leastSigBits = buffer.getLong();
        return new UUID(mostSigBits, leastSigBits);
    }

    public static UUID parseRequired(String rawUuid, String fieldName) {
        Objects.requireNonNull(rawUuid, fieldName + " não pode ser nulo");
        return UUID.fromString(rawUuid.trim());
    }
}
