package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "conversas")
@Getter
@Setter
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
}
