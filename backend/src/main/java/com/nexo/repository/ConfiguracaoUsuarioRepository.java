package com.nexo.repository;

import com.nexo.domain.ConfiguracaoUsuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConfiguracaoUsuarioRepository extends JpaRepository<ConfiguracaoUsuario, Long> {
    Optional<ConfiguracaoUsuario> findByUsuarioId(Long usuarioId);

    List<ConfiguracaoUsuario> findByUsuarioIdIn(Collection<Long> usuarioIds);

    /** A configuração de todos os alunos numa consulta só (uso: sincronização no arranque). */
    @org.springframework.data.jpa.repository.Query(
            "select c from ConfiguracaoUsuario c join fetch c.usuario u where u.role = com.nexo.domain.Role.ALUNO")
    List<ConfiguracaoUsuario> findAllDeAlunos();
}
