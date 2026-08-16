package com.nexo.domain;

import jakarta.persistence.*;

/** Progresso de um aluno em um desafio. */
@Entity
@Table(name = "desafios_aluno")
public class DesafioAluno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Aluno aluno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Desafio desafio;

    /** ABERTO | PROGRESSO | CONCLUIDO */
    private String status = "ABERTO";

    private int progresso;

    /**
     * Preenchidos apenas quando o aluno gabarita o quiz. Tentativa reprovada não
     * grava placar — assim reabrir o desafio não revela nada da tentativa anterior.
     */
    private Integer acertos;

    private Integer totalPerguntas;

    /**
     * Quantas vezes o aluno já enviou o quiz deste desafio (reprovadas incluídas).
     * Integer (e não int) porque a coluna nasce nula nas linhas criadas antes deste campo.
     */
    private Integer tentativas = 0;

    public DesafioAluno() {}

    public DesafioAluno(Aluno aluno, Desafio desafio, String status, int progresso) {
        this.aluno = aluno;
        this.desafio = desafio;
        this.status = status;
        this.progresso = progresso;
    }

    public Long getId() { return id; }
    public Aluno getAluno() { return aluno; }
    public Desafio getDesafio() { return desafio; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getProgresso() { return progresso; }
    public void setProgresso(int progresso) { this.progresso = progresso; }
    public Integer getAcertos() { return acertos; }
    public void setAcertos(Integer acertos) { this.acertos = acertos; }
    public Integer getTotalPerguntas() { return totalPerguntas; }
    public void setTotalPerguntas(Integer totalPerguntas) { this.totalPerguntas = totalPerguntas; }
    public int getTentativas() { return tentativas == null ? 0 : tentativas; }
    public void setTentativas(int tentativas) { this.tentativas = tentativas; }
}
