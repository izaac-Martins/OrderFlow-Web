import React from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { ManagerPinProvider } from './context/ManagerPinContext';
import { ProtectedRoute } from './components/ProtectedRoute';
import { AppShell } from './components/AppShell';
import { LoginPage } from './pages/LoginPage';
import { PdvPage } from './pages/PdvPage';
import { PedidosMonitorPage } from './pages/PedidosMonitorPage';
import { CaixaPage } from './pages/CaixaPage';
import { CatalogoPage } from './pages/CatalogoPage';
import { InsumosPage } from './pages/InsumosPage';

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <ManagerPinProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />

            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <AppShell />
                </ProtectedRoute>
              }
            >
              <Route index element={<Navigate to="/pdv" replace />} />
              <Route
                path="pdv"
                element={
                  <ProtectedRoute
                    allowedRoles={[
                      'ADMIN',
                      'GERENTE',
                      'OPERADOR_CAIXA',
                      'ATENDENTE',
                    ]}
                  >
                    <PdvPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="pedidos"
                element={
                  <ProtectedRoute
                    allowedRoles={[
                      'ADMIN',
                      'GERENTE',
                      'OPERADOR_CAIXA',
                      'ATENDENTE',
                      'COZINHA',
                    ]}
                  >
                    <PedidosMonitorPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="caixa"
                element={
                  <ProtectedRoute
                    allowedRoles={[
                      'ADMIN',
                      'GERENTE',
                      'OPERADOR_CAIXA',
                      'ATENDENTE',
                    ]}
                  >
                    <CaixaPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="catalogo"
                element={
                  <ProtectedRoute
                    allowedRoles={['ADMIN', 'GERENTE', 'ATENDENTE', 'COZINHA']}
                  >
                    <CatalogoPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="insumos"
                element={
                  <ProtectedRoute
                    allowedRoles={['ADMIN', 'GERENTE', 'COZINHA']}
                  >
                    <InsumosPage />
                  </ProtectedRoute>
                }
              />
            </Route>

            <Route path="*" element={<Navigate to="/pdv" replace />} />
          </Routes>
        </BrowserRouter>
      </ManagerPinProvider>
    </AuthProvider>
  );
};

export default App;
