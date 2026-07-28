package com.nexo.repository;

import com.nexo.domain.RefreshToken;
import com.nexo.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void deleteByUsuario(Usuario usuario);

    /** Usado pela limpeza agendada — tokens revogados ou vencidos não servem mais pra nada. */
    @Modifying
    @Query("delete from RefreshToken t where t.revogado = true or t.expiraEm < :agora")
    int removerExpiradosOuRevogados(@Param("agora") Instant agora);

    /**
     * Derruba todas as sessões ativas do usuário de uma vez. Acionado quando um refresh
     * token já rotacionado é reapresentado — sinal de que uma cópia vazou e não dá para
     * distinguir o dono legítimo de quem a obteve.
     */
    @Modifying
    @Query("update RefreshToken t set t.revogado = true where t.usuario.id = :usuarioId and t.revogado = false")
    int revogarTodosDoUsuario(@Param("usuarioId") Long usuarioId);
}
