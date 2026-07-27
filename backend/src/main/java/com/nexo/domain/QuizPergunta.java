package com.nexo.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/** Pergunta de múltipla escolha do quiz de um desafio. */
@Entity
@Table(name = "quiz_perguntas")
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Desafio getDesafio() { return desafio; }
    public void setDesafio(Desafio desafio) { this.desafio = desafio; }
    public String getEnunciado() { return enunciado; }
    public void setEnunciado(String enunciado) { this.enunciado = enunciado; }
    public List<String> getAlternativas() { return alternativas; }
    public void setAlternativas(List<String> alternativas) { this.alternativas = alternativas; }
    public int getRespostaCorreta() { return respostaCorreta; }
    public void setRespostaCorreta(int respostaCorreta) { this.respostaCorreta = respostaCorreta; }
}
