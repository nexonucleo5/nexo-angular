package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Token de uso único para redefinir a senha sem estar autenticado.
 *
 * <p>Como o {@link RefreshToken}, a tabela guarda apenas o <b>hash</b> do valor: quem
 * conseguir ler o banco não obtém um token utilizável. O valor em claro existe no
 * instante em que é gerado e no e-mail enviado — em lugar nenhum mais.
 *
 * <p>A linha é marcada como usada em vez de apagada. Assim, reapresentar um token já
 * gasto é distinguível de apresentar um token que nunca existiu, e a diferença vai para
 * a trilha de auditoria: token gasto reaparecendo é sinal de que o e-mail foi lido por
 * quem não devia.
 */
@Entity
@Table(name = "tokens_recuperacao", indexes = {
        @Index(name = "idx_recuperacao_hash", columnList = "tokenHash", unique = true),
        @Index(name = "idx_recuperacao_expira", columnList = "expiraEm")
})
public class TokenRecuperacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Usuario usuario;

    @Column(nullable = false)
    private Instant expiraEm;

    /** Nulo enquanto não usado; o instante do uso quando gasto. */
    private Instant usadoEm;

    private Instant criadoEm = Instant.now();

    public Long getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public Instant getExpiraEm() { return expiraEm; }
    public void setExpiraEm(Instant expiraEm) { this.expiraEm = expiraEm; }
    public Instant getUsadoEm() { return usadoEm; }
    public void setUsadoEm(Instant usadoEm) { this.usadoEm = usadoEm; }
    public Instant getCriadoEm() { return criadoEm; }

    public boolean isUsado() { return usadoEm != null; }
    public boolean isExpirado() { return !expiraEm.isAfter(Instant.now()); }
}
