// ═══════════════════════════════════════════════════════════════════════════
// Cliente HTTP Axios + Interceptors (JWT, X-Loja-Id, Idempotency-Key, RFC 7807)
// + Camada de Simulação Local Resiliente (quando o backend não está rodando na porta 8080)
// ═══════════════════════════════════════════════════════════════════════════

import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';
import { generateUuidV7 } from './formatters';
import { OrderFlowApiError, parseApiError } from './errors';
import { handleLocalSimulationRequest } from './localSimulation';

const STORAGE_ACCESS_TOKEN = 'orderflow:access_token';
const STORAGE_REFRESH_TOKEN = 'orderflow:refresh_token';
const STORAGE_LOJA_ID = 'orderflow:loja_id';
const STORAGE_SIMULATION_MODE = 'orderflow:simulation_mode';

export function getStoredAccessToken(): string | null {
  return localStorage.getItem(STORAGE_ACCESS_TOKEN);
}

export function getStoredRefreshToken(): string | null {
  return localStorage.getItem(STORAGE_REFRESH_TOKEN);
}

export function getStoredLojaId(): string {
  return localStorage.getItem(STORAGE_LOJA_ID) || '019325f0-8a00-7000-8000-000000000001';
}

export function isSimulationModeEnabled(): boolean {
  const val = localStorage.getItem(STORAGE_SIMULATION_MODE);
  return val === null ? true : val === 'true';
}

export function setSimulationModeEnabled(enabled: boolean): void {
  localStorage.setItem(STORAGE_SIMULATION_MODE, String(enabled));
}

export function persistAuthTokens(
  accessToken: string,
  refreshToken: string,
  lojaId: string
): void {
  localStorage.setItem(STORAGE_ACCESS_TOKEN, accessToken);
  localStorage.setItem(STORAGE_REFRESH_TOKEN, refreshToken);
  localStorage.setItem(STORAGE_LOJA_ID, lojaId);
}

export function clearAuthTokens(): void {
  localStorage.removeItem(STORAGE_ACCESS_TOKEN);
  localStorage.removeItem(STORAGE_REFRESH_TOKEN);
}

export const httpClient: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 12000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json, application/problem+json',
  },
});

// Interceptor de Requisição: injeta Authorization Bearer, X-Loja-Id e Idempotency-Key (UUIDv7)
httpClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getStoredAccessToken();
  const lojaId = getStoredLojaId();

  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  if (lojaId) {
    config.headers.set('X-Loja-Id', lojaId);
  }

  const method = config.method?.toUpperCase();
  if (
    (method === 'POST' || method === 'PUT' || method === 'PATCH') &&
    !config.headers.get('Idempotency-Key')
  ) {
    config.headers.set('Idempotency-Key', generateUuidV7());
  }

  return config;
});

// Interceptor de Resposta: normaliza RFC 7807 ProblemDetail ou delega para simulação local caso o backend esteja offline
httpClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error?.config as InternalAxiosRequestConfig | undefined;

    // Se o modo de simulação estiver ativo ou se o servidor local estiver inacessível (ECONNREFUSED / Network Error / 502 / 504)
    const isNetworkOffline =
      !error.response ||
      error.code === 'ERR_NETWORK' ||
      error.response?.status === 502 ||
      error.response?.status === 504;

    if (config && (isSimulationModeEnabled() || isNetworkOffline)) {
      try {
        const simulatedData = await handleLocalSimulationRequest(config);
        return {
          data: simulatedData,
          status: config.method?.toUpperCase() === 'POST' ? 201 : 200,
          statusText: 'OK (Simulated)',
          headers: {},
          config,
        };
      } catch (simErr) {
        throw parseApiError(simErr);
      }
    }

    throw parseApiError(error);
  }
);

/**
 * Helper tipado que executa a chamada via httpClient (ou simulação local quando habilitada).
 */
export async function apiRequest<T>(config: {
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  url: string;
  data?: unknown;
  params?: Record<string, unknown>;
  headers?: Record<string, string>;
}): Promise<T> {
  if (isSimulationModeEnabled()) {
    try {
      const fakeConfig = {
        method: config.method.toLowerCase(),
        url: config.url,
        data: config.data,
        params: config.params,
        headers: config.headers,
      } as unknown as InternalAxiosRequestConfig;
      return (await handleLocalSimulationRequest(fakeConfig)) as T;
    } catch (simErr) {
      throw parseApiError(simErr);
    }
  }

  try {
    const res = await httpClient.request<T>(config);
    return res.data;
  } catch (err) {
    if (err instanceof OrderFlowApiError) {
      throw err;
    }
    throw parseApiError(err);
  }
}
