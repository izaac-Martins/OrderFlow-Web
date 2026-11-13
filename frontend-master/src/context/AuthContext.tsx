import React, { createContext, useContext, useEffect, useState } from 'react';
import type {
  LoginRequest,
  TokenResponse,
  UserRole,
  UsuarioAutenticado,
} from '../types/api';
import {
  apiRequest,
  clearAuthTokens,
  isSimulationModeEnabled,
  persistAuthTokens,
  setSimulationModeEnabled,
} from '../lib/http';
import { stompManager } from '../lib/stompClient';

const STORAGE_USER_KEY = 'orderflow:current_user';

interface AuthContextValue {
  user: UsuarioAutenticado | null;
  isAuthenticated: boolean;
  simulationMode: boolean;
  login: (req: LoginRequest) => Promise<UsuarioAutenticado>;
  logout: () => void;
  switchRoleForDemo: (role: UserRole) => void;
  toggleSimulationMode: (enabled: boolean) => void;
  hasAnyRole: (roles: UserRole[]) => boolean;
  isManagerOrAdmin: boolean;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

const DEFAULT_LOJA_ID = '019325f0-8a00-7000-8000-000000000001';

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const [user, setUser] = useState<UsuarioAutenticado | null>(() => {
    try {
      const saved = localStorage.getItem(STORAGE_USER_KEY);
      return saved ? (JSON.parse(saved) as UsuarioAutenticado) : null;
    } catch {
      return null;
    }
  });

  const [simulationMode, setSimulationModeState] = useState<boolean>(() =>
    isSimulationModeEnabled()
  );

  useEffect(() => {
    if (user) {
      stompManager.connect();
    } else {
      stompManager.disconnect();
    }
  }, [user, simulationMode]);

  const login = async (req: LoginRequest): Promise<UsuarioAutenticado> => {
    const resp = await apiRequest<TokenResponse>({
      method: 'POST',
      url: '/auth/login',
      data: req,
    });
    persistAuthTokens(
      resp.accessToken,
      resp.refreshToken,
      resp.usuario.lojaId || DEFAULT_LOJA_ID
    );
    localStorage.setItem(STORAGE_USER_KEY, JSON.stringify(resp.usuario));
    setUser(resp.usuario);
    return resp.usuario;
  };

  const logout = () => {
    clearAuthTokens();
    localStorage.removeItem(STORAGE_USER_KEY);
    stompManager.disconnect();
    setUser(null);
  };

  const switchRoleForDemo = (role: UserRole) => {
    if (!user) return;
    const roleNames: Record<UserRole, string> = {
      ADMIN: 'Helena Costa (Admin Master)',
      GERENTE: 'Roberto Almeida (Gerente de Loja)',
      OPERADOR_CAIXA: 'Lucas Martins (Operador de Caixa)',
      ATENDENTE: 'Marina Souza (Atendente / Balcão)',
      COZINHA: 'Barista KDS (Cozinha)',
    };
    const updated: UsuarioAutenticado = {
      ...user,
      role,
      nome: roleNames[role],
      possuiPinAlcada: role === 'ADMIN' || role === 'GERENTE',
    };
    localStorage.setItem(STORAGE_USER_KEY, JSON.stringify(updated));
    setUser(updated);
  };

  const toggleSimulationMode = (enabled: boolean) => {
    setSimulationModeEnabled(enabled);
    setSimulationModeState(enabled);
  };

  const hasAnyRole = (roles: UserRole[]): boolean => {
    if (!user) return false;
    if (roles.includes(user.role)) return true;
    // Equivalência operacional entre ATENDENTE e OPERADOR_CAIXA quando aplicável
    if (
      user.role === 'OPERADOR_CAIXA' &&
      roles.includes('ATENDENTE')
    ) {
      return true;
    }
    if (
      user.role === 'ATENDENTE' &&
      roles.includes('OPERADOR_CAIXA')
    ) {
      return true;
    }
    return false;
  };

  const isManagerOrAdmin =
    user?.role === 'ADMIN' || user?.role === 'GERENTE';

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: Boolean(user),
        simulationMode,
        login,
        logout,
        switchRoleForDemo,
        toggleSimulationMode,
        hasAnyRole,
        isManagerOrAdmin,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth deve ser utilizado dentro de um AuthProvider');
  }
  return ctx;
}
