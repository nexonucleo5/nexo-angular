package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "mensagens",
       indexes = @Index(name = "idx_mensagens_conversa_criada", columnList = "conversa_id, criada_em"))
public class Mensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Conversa conversa;

    @Column(nullable = false)
    private String autorNome;

    /** true quando a mensagem foi enviada pelo usuário logado (lado direito do chat). */
    private boolean minha;

    @Column(nullable = false, length = 4000)
    private String texto;

    private Instant criadaEm = Instant.now();

    private boolean lida;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Conversa getConversa() { return conversa; }
    public void setConversa(Conversa conversa) { this.conversa = conversa; }
    public String getAutorNome() { return autorNome; }
    public void setAutorNome(String autorNome) { this.autorNome = autorNome; }
    public boolean isMinha() { return minha; }
    public void setMinha(boolean minha) { this.minha = minha; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public Instant getCriadaEm() { return criadaEm; }
    public void setCriadaEm(Instant criadaEm) { this.criadaEm = criadaEm; }
    public boolean isLida() { return lida; }
    public void setLida(boolean lida) { this.lida = lida; }
}
