package com.nexo.repository;

import com.nexo.domain.Role;
import com.nexo.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByLoginIgnoreCase(String login);
    boolean existsByLoginIgnoreCase(String login);
    List<Usuario> findByRoleInOrderByNome(List<Role> roles);

    /**
     * Só o papel, sem materializar a entidade. Usado na checagem que roda a cada
     * mensagem do chat: antes cada mensagem carregava dois {@code Usuario} completos
     * para o contexto de persistência apenas para comparar dois enums.
     */
    @Query("select u.role from Usuario u where u.id = :id")
    Optional<Role> findRoleById(@Param("id") Long id);
}
