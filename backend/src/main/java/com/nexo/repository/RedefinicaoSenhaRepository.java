package com.nexo.repository;

import com.nexo.domain.RedefinicaoSenha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RedefinicaoSenhaRepository extends JpaRepository<RedefinicaoSenha, Long> {

    Optional<RedefinicaoSenha> findByTokenHash(String tokenHash);

    /**
     * Consome o link de forma atômica: só um UPDATE consegue virar {@code usado} de
     * {@code false} para {@code true}. Duas requisições simultâneas com o mesmo link
     * não redefinem a senha duas vezes — a segunda recebe 0 linhas afetadas.
     */
    @Modifying
    @Query("update RedefinicaoSenha r set r.usado = true "
         + "where r.id = :id and r.usado = false and r.expiraEm > :agora")
    int consumir(@Param("id") Long id, @Param("agora") Instant agora);

    /** Invalida os links ainda abertos do usuário: só o pedido mais recente vale. */
    @Modifying
    @Query("update RedefinicaoSenha r set r.usado = true where r.usuario.id = :usuarioId and r.usado = false")
    int invalidarPendentes(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("delete from RedefinicaoSenha r where r.expiraEm < :agora")
    int removerExpirados(@Param("agora") Instant agora);
}
