import React from 'react';
import { Navigate } from 'react-router-dom';
import { ShieldAlert } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import type { UserRole } from '../types/api';

interface ProtectedRouteProps {
  allowedRoles?: UserRole[];
  children: React.ReactNode;
}

export const ProtectedRoute: React.FC<ProtectedRouteProps> = ({
  allowedRoles,
  children,
}) => {
  const { isAuthenticated, user, hasAnyRole, switchRoleForDemo } = useAuth();

  if (!isAuthenticated || !user) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles && allowedRoles.length > 0 && !hasAnyRole(allowedRoles)) {
    return (
      <div className="max-w-xl mx-auto my-14 bg-white rounded-2xl border border-rose-200 shadow-sm p-8 text-center space-y-4">
        <div className="w-14 h-14 rounded-2xl bg-rose-100 text-rose-700 flex items-center justify-center mx-auto">
          <ShieldAlert className="w-7 h-7" />
        </div>
        <h2 className="text-xl font-bold text-slate-900">
          Acesso Restrito por Perfil (RBAC)
        </h2>
        <p className="text-sm text-slate-600">
          Seu perfil atual é{' '}
          <span className="font-mono font-bold text-slate-900">{user.role}</span>. Esta
          área exige um dos seguintes perfis:{' '}
          <span className="font-mono font-semibold text-amber-800">
            {allowedRoles.join(', ')}
          </span>
          .
        </p>
        <div className="pt-2 flex flex-wrap items-center justify-center gap-2">
          {allowedRoles.map((role) => (
            <button
              key={role}
              type="button"
              onClick={() => switchRoleForDemo(role)}
              className="px-4 py-2 rounded-xl bg-amber-600 hover:bg-amber-700 text-white text-xs font-semibold transition"
            >
              Alternar para {role}
            </button>
          ))}
        </div>
      </div>
    );
  }

  return <>{children}</>;
};
