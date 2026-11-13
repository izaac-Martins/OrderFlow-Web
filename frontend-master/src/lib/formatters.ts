// ═══════════════════════════════════════════════════════════════════════════
// Utilitários Monetários (Centavos Inteiros BIGINT), Arredondamento HALF_EVEN e UUIDv7
// ═══════════════════════════════════════════════════════════════════════════

const brlFormatter = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
});

/**
 * Formata um valor inteiro em centavos para moeda BRL (ex: 1450 -> "R$ 14,50").
 */
export function formatCentavos(centavos: number | null | undefined): string {
  if (centavos === null || centavos === undefined || Number.isNaN(centavos)) {
    return 'R$ 0,00';
  }
  return brlFormatter.format(centavos / 100);
}

/**
 * Converte centavos inteiros para string editável em reais (ex: 1450 -> "14,50").
 */
export function centavosToInputReais(centavos: number): string {
  return (centavos / 100).toFixed(2).replace('.', ',');
}

/**
 * Converte texto digitado em reais ("14,50" ou "14.50") para centavos inteiros (1450).
 */
export function parseReaisToCentavos(raw: string): number {
  const cleaned = raw.replace(/[^\d,.-]/g, '').replace(',', '.');
  const parsed = Number.parseFloat(cleaned);
  if (Number.isNaN(parsed)) {
    return 0;
  }
  return Math.round(parsed * 100);
}

/**
 * Regra §2.1 do Planejamento Arquitetural:
 * Cálculo de modificador PERCENTUAL (em basis points, ex.: 1500 = 15%) sobre o preço base
 * aplicando arredondamento HALF_EVEN (arredondamento bancário) no centavo.
 */
export function calcPercentualHalfEven(
  baseCentavos: number,
  basisPoints: number
): number {
  const numerator = baseCentavos * basisPoints;
  const divisor = 10_000;
  const quotient = Math.trunc(numerator / divisor);
  const remainder = Math.abs(numerator % divisor);
  const half = divisor / 2;
  const sign = numerator >= 0 ? 1 : -1;

  if (remainder > half) {
    return quotient + sign;
  }
  if (remainder < half) {
    return quotient;
  }
  // Exatamente 0.5: arredonda para o par mais próximo (HALF_EVEN)
  return Math.abs(quotient) % 2 === 0 ? quotient : quotient + sign;
}

/**
 * Gera um identificador UUIDv7 ordenável por tempo (RFC 9562) para cabeçalhos Idempotency-Key.
 */
export function generateUuidV7(): string {
  const now = BigInt(Date.now());
  const bytes = new Uint8Array(16);
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    crypto.getRandomValues(bytes);
  } else {
    for (let i = 0; i < 16; i += 1) {
      bytes[i] = Math.floor(Math.random() * 256);
    }
  }

  // 48-bit timestamp em milissegundos
  bytes[0] = Number((now >> 40n) & 0xffn);
  bytes[1] = Number((now >> 32n) & 0xffn);
  bytes[2] = Number((now >> 24n) & 0xffn);
  bytes[3] = Number((now >> 16n) & 0xffn);
  bytes[4] = Number((now >> 8n) & 0xffn);
  bytes[5] = Number(now & 0xffn);

  // Versão 7 (0111)
  bytes[6] = (bytes[6] & 0x0f) | 0x70;
  // Variante RFC 4122 / 9562 (10xx)
  bytes[8] = (bytes[8] & 0x3f) | 0x80;

  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`;
}

/**
 * Formata timestamp ISO 8601 UTC para horário local America/Sao_Paulo.
 */
export function formatDateTimeBr(iso?: string | null): string {
  if (!iso) return '—';
  try {
    return new Intl.DateTimeFormat('pt-BR', {
      dateStyle: 'short',
      timeStyle: 'medium',
      timeZone: 'America/Sao_Paulo',
    }).format(new Date(iso));
  } catch {
    return iso;
  }
}
