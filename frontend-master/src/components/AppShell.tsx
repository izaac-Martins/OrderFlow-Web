import React, { useEffect, useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import {
  Coffee,
  ShoppingBag,
  ClipboardList,
  Wallet,
  Layers,
  PackageCheck,
  Wifi,
  WifiOff,
  LogOut,
  Bell,
  Server,
  UserCheck,
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { stompManager } from '../lib/stompClient';
import type {
  CatalogoEventoStomp,
  PedidoProntoEventoDto,
  UserRole,
} from '../types/api';

const ROLES: UserRole[] = [
  'GERENTE',
  'ADMIN',
  'OPERADOR_CAIXA',
  'ATENDENTE',
  'COZINHA',
];

export const AppShell: React.FC = () => {
  const {
    user,
    logout,
    switchRoleForDemo,
    simulationMode,
    toggleSimulationMode,
  } = useAuth();
  const [wsConnected, setWsConnected] = useState(true);
  const [liveToast, setLiveToast] = useState<string | null>(null);

  useEffect(() => {
    const unsubStatus = stompManager.onConnectionChange(setWsConnected);
    const lojaId = user?.lojaId || '019325f0-8a00-7000-8000-000000000001';

    const unsubDisp = stompManager.subscribe<CatalogoEventoStomp>(
      `/topic/lojas/${lojaId}/disponibilidade`,
      (ev) => {
        const qtd = ev.disponibilidade?.length || 0;
        setLiveToast(
          `STOMP Tempo Real: Disponibilidade atualizada (versão #${ev.versao}, ${qtd} item(ns) afetado(s)).`
        );
      }
    );

    const unsubProntos = stompManager.subscribe<PedidoProntoEventoDto>(
      `/topic/lojas/${lojaId}/pedidos/prontos`,
      (ev) => {
        setLiveToast(
          `STOMP KDS: Pedido #${ev.numeroDiario} (${ev.identificadorCliente || 'Balcão'}) está PRONTO para entrega!`
        );
      }
    );

    return () => {
      unsubStatus();
      unsubDisp();
      unsubProntos();
    };
  }, [user?.lojaId]);

  useEffect(() => {
    if (!liveToast) return;
    const timer = setTimeout(() => setLiveToast(null), 6000);
    return () => clearTimeout(timer);
  }, [liveToast]);

  const navItems = [
    {
      to: '/pdv',
      label: 'Balcão / PDV',
      icon: ShoppingBag,
      roles: ['ADMIN', 'GERENTE', 'OPERADOR_CAIXA', 'ATENDENTE'],
    },
    {
      to: '/pedidos',
      label: 'Monitor de Pedidos',
      icon: ClipboardList,
      roles: ['ADMIN', 'GERENTE', 'OPERADOR_CAIXA', 'ATENDENTE', 'COZINHA'],
    },
    {
      to: '/caixa',
      label: 'Operação de Caixa',
      icon: Wallet,
      roles: ['ADMIN', 'GERENTE', 'OPERADOR_CAIXA', 'ATENDENTE'],
    },
    {
      to: '/catalogo',
      label: 'Catálogo & Combos',
      icon: Layers,
      roles: ['ADMIN', 'GERENTE', 'ATENDENTE', 'COZINHA'],
    },
    {
      to: '/insumos',
      label: 'Insumos (Controle 86)',
      icon: PackageCheck,
      roles: ['ADMIN', 'GERENTE', 'COZINHA'],
    },
  ];

  return (
    <div className="min-h-screen flex flex-col bg-slate-50">
      {/* Topbar Executiva */}
      <header className="sticky top-0 z-30 bg-slate-900 text-white border-b border-slate-800 shadow-sm">
        <div className="max-w-[1600px] mx-auto px-4 lg:px-6 h-16 flex items-center justify-between gap-4">
          {/* Identidade Visual & Loja */}
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-amber-500 text-slate-950 flex items-center justify-center font-black shadow">
              <Coffee className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-bold tracking-tight text-base">
                  OrderFlow Master
                </span>
                <span className="px-2 py-0.5 text-[11px] font-mono font-semibold rounded-md bg-amber-500/20 text-amber-300 border border-amber-500/30">
                  {user?.codigoLoja || 'MATRIZ-01'}
                </span>
              </div>
              <p className="text-xs text-slate-400 truncate max-w-[260px] sm:max-w-md">
                {user?.nomeLoja || 'Cafeteria OrderFlow — Unidade Matriz'}
              </p>
            </div>
          </div>

          {/* Navegação Principal */}
          <nav className="hidden xl:flex items-center gap-1">
            {navItems.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    `flex items-center gap-2 px-3.5 py-2 rounded-xl text-xs font-semibold transition ${
                      isActive
                        ? 'bg-amber-500 text-slate-950 shadow-sm'
                        : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                    }`
                  }
                >
                  <Icon className="w-4 h-4" />
                  <span>{item.label}</span>
                </NavLink>
              );
            })}
          </nav>

          {/* Status STOMP, Modo API & Seletor de Perfil RBAC */}
          <div className="flex items-center gap-2.5">
            {/* Alternador Simulação Local vs Backend Real */}
            <button
              type="button"
              onClick={() => toggleSimulationMode(!simulationMode)}
              title="Alternar entre Simulação Local Resiliente e Backend Spring Boot em localhost:8080"
              className={`hidden md:flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-semibold border transition ${
                simulationMode
                  ? 'bg-indigo-950/80 border-indigo-700 text-indigo-200 hover:bg-indigo-900'
                  : 'bg-emerald-950/80 border-emerald-700 text-emerald-200 hover:bg-emerald-900'
              }`}
            >
              <Server className="w-3.5 h-3.5" />
              <span>{simulationMode ? 'Motor Local (MSW/Sim)' : 'API Real (:8080)'}</span>
            </button>

            {/* Indicador STOMP */}
            <div
              className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-semibold border ${
                wsConnected
                  ? 'bg-emerald-950/60 border-emerald-800 text-emerald-300'
                  : 'bg-rose-950/60 border-rose-800 text-rose-300'
              }`}
            >
              {wsConnected ? (
                <Wifi className="w-3.5 h-3.5 text-emerald-400" />
              ) : (
                <WifiOff className="w-3.5 h-3.5 text-rose-400" />
              )}
              <span className="hidden sm:inline">STOMP</span>
            </div>

            {/* Seletor Rápido RBAC */}
            <div className="flex items-center gap-1.5 bg-slate-800 border border-slate-700 rounded-xl px-2.5 py-1">
              <UserCheck className="w-3.5 h-3.5 text-amber-400 shrink-0" />
              <select
                aria-label="Perfil RBAC Ativo"
                value={user?.role || 'GERENTE'}
                onChange={(e) => switchRoleForDemo(e.target.value as UserRole)}
                className="bg-transparent text-xs font-bold text-white outline-none cursor-pointer"
              >
                {ROLES.map((r) => (
                  <option key={r} value={r} className="bg-slate-900 text-white">
                    {r}
                  </option>
                ))}
              </select>
            </div>

            <button
              type="button"
              onClick={logout}
              className="p-2 rounded-xl bg-slate-800 hover:bg-rose-600 text-slate-300 hover:text-white transition"
              title="Encerrar sessão"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Barra de Navegação Mobile/Tablet */}
        <div className="xl:hidden border-t border-slate-800 px-4 py-2 flex items-center gap-1.5 overflow-x-auto">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold whitespace-nowrap ${
                    isActive
                      ? 'bg-amber-500 text-slate-950'
                      : 'text-slate-300 hover:bg-slate-800'
                  }`
                }
              >
                <Icon className="w-3.5 h-3.5" />
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </div>
      </header>

      {/* Banner de Evento STOMP em Tempo Real */}
      {liveToast && (
        <div className="bg-amber-600 text-white px-4 py-2.5 shadow-md">
          <div className="max-w-[1600px] mx-auto flex items-center justify-between gap-3 text-xs font-semibold">
            <div className="flex items-center gap-2">
              <Bell className="w-4 h-4 animate-bounce" />
              <span>{liveToast}</span>
            </div>
            <button
              type="button"
              onClick={() => setLiveToast(null)}
              className="underline hover:opacity-80"
            >
              Fechar
            </button>
          </div>
        </div>
      )}

      {/* Área Principal */}
      <main className="flex-1 max-w-[1600px] w-full mx-auto px-4 lg:px-6 py-6">
        <Outlet />
      </main>
    </div>
  );
};
