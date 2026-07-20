package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "conversas")
public class Conversa {

    public enum Caixa { ENTRADA, ENVIADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String assunto;

    /** Nome do interlocutor (aluno/responsável/professor). */
    private String participanteNome;

    private String participantePapel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Caixa caixa = Caixa.ENTRADA;

    private Instant atualizadaEm = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAssunto() { return assunto; }
    public void setAssunto(String assunto) { this.assunto = assunto; }
    public String getParticipanteNome() { return participanteNome; }
    public void setParticipanteNome(String participanteNome) { this.participanteNome = participanteNome; }
    public String getParticipantePapel() { return participantePapel; }
    public void setParticipantePapel(String participantePapel) { this.participantePapel = participantePapel; }
    public Caixa getCaixa() { return caixa; }
    public void setCaixa(Caixa caixa) { this.caixa = caixa; }
    public Instant getAtualizadaEm() { return atualizadaEm; }
    public void setAtualizadaEm(Instant atualizadaEm) { this.atualizadaEm = atualizadaEm; }
}
