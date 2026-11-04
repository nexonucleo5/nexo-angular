package com.nexo.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Pergunta de múltipla escolha do quiz de um desafio. */
@Entity
@Table(name = "quiz_perguntas")
@Getter
@Setter
public class QuizPergunta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Desafio desafio;

    @Column(nullable = false, length = 1000)
    private String enunciado;

    @ElementCollection
    @CollectionTable(name = "quiz_pergunta_alternativas", joinColumns = @JoinColumn(name = "pergunta_id"))
    @OrderColumn(name = "posicao")
    @Column(name = "texto", length = 500)
    private List<String> alternativas = new ArrayList<>();

    /** Índice (0-based) da alternativa correta na lista acima. */
    @Column(nullable = false)
    private int respostaCorreta;
}
