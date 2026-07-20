package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "eventos_auditoria")
public class EventoAuditoria {

    public enum Tipo { LOGIN, LOGOUT, ACESSO, ALTERACAO, ERRO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String usuarioNome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo;

    @Column(nullable = false)
    private String acao;

    @Column(length = 2000)
    private String detalhe;

    private String ip;

    private Instant criadoEm = Instant.now();

    public EventoAuditoria() {}

    public EventoAuditoria(String usuarioNome, Tipo tipo, String acao, String detalhe, String ip) {
        this.usuarioNome = usuarioNome;
        this.tipo = tipo;
        this.acao = acao;
        this.detalhe = detalhe;
        this.ip = ip;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsuarioNome() { return usuarioNome; }
    public void setUsuarioNome(String usuarioNome) { this.usuarioNome = usuarioNome; }
    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }
    public String getAcao() { return acao; }
    public void setAcao(String acao) { this.acao = acao; }
    public String getDetalhe() { return detalhe; }
    public void setDetalhe(String detalhe) { this.detalhe = detalhe; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public Instant getCriadoEm() { return criadoEm; }
    public void setCriadoEm(Instant criadoEm) { this.criadoEm = criadoEm; }
}
