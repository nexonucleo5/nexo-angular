package com.nexo.repository;

import com.nexo.domain.Role;
import com.nexo.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByLoginIgnoreCase(String login);
    boolean existsByLoginIgnoreCase(String login);
    List<Usuario> findByRoleInOrderByNome(List<Role> roles);
}
