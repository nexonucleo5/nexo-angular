package com.nexo.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Pedido de "esqueci minha senha": um valor aleatório de uso único enviado ao e-mail de
 * recuperação da conta. Segue o desenho do {@link RefreshToken} — o banco guarda só o
 * hash, de modo que um vazamento da tabela não entrega links válidos.
 */
@Entity
@Table(name = "redefinicoes_senha",
       // invalidarPendentes: um UPDATE por usuário a cada pedido novo e a cada conclusão.
       indexes = @Index(name = "idx_redefinicoes_senha_usuario", columnList = "usuario_id"))
@Getter
@Setter
public class RedefinicaoSenha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SHA-256 (hex) do valor enviado por e-mail — nunca o valor em claro. */
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Usuario usuario;

    @Column(nullable = false)
    private Instant expiraEm;

    private Instant criadoEm = Instant.now();

    /** Ligado quando o link foi usado ou substituído por um pedido mais novo. */
    private boolean usado = false;
}
