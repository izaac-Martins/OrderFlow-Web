package com.orderflow.auth.repository;

import com.orderflow.auth.domain.Usuario;
import com.orderflow.shared.security.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByLojaIdAndEmailIgnoreCase(UUID lojaId, String email);

    List<Usuario> findByEmailIgnoreCaseAndAtivoTrue(String email);

    Optional<Usuario> findByIdAndLojaId(UUID id, UUID lojaId);

    List<Usuario> findByLojaIdAndRoleInAndAtivoTrue(UUID lojaId, Collection<Role> roles);
}
