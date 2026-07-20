package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Configurações do usuário armazenadas como documento JSON particionado por seção,
 * espelhando o formato do signal<Settings> do Angular. O PATCH parcial por seção
 * é resolvido no serviço (merge servidor-vence).
 */
@Entity
@Table(name = "configuracoes_usuario")
public class ConfiguracaoUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(unique = true)
    private Usuario usuario;

    @Lob
    @Column(nullable = false)
    private String json;

    private Instant atualizadaEm = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getJson() { return json; }
    public void setJson(String json) { this.json = json; }
    public Instant getAtualizadaEm() { return atualizadaEm; }
    public void setAtualizadaEm(Instant atualizadaEm) { this.atualizadaEm = atualizadaEm; }
}
