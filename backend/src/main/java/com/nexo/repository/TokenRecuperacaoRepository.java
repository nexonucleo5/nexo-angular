package com.nexo.repository;

import com.nexo.domain.TokenRecuperacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TokenRecuperacaoRepository extends JpaRepository<TokenRecuperacao, Long> {

    Optional<TokenRecuperacao> findByTokenHash(String tokenHash);

    /**
     * Invalida os pedidos anteriores do usuário. Pedir um link novo tem que aposentar o
     * antigo: senão um e-mail de recuperação esquecido na caixa de entrada continua valendo
     * pelos 30 minutos dele, e quem tiver acesso a essa caixa usa o link antigo.
     */
    @Modifying
    @Query("update TokenRecuperacao t set t.usadoEm = :agora "
            + "where t.usuario.id = :usuarioId and t.usadoEm is null")
    int invalidarPendentesDoUsuario(@Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);

    /** Poda das linhas que não decidem mais nada — mesma ideia do RefreshTokenCleanupJob. */
    @Modifying
    @Query("delete from TokenRecuperacao t where t.expiraEm < :agora")
    int removerVencidos(@Param("agora") Instant agora);
}
