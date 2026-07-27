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

    /** Preenchidos ao finalizar o quiz (null enquanto o desafio não é concluído por quiz). */
    private Integer acertos;

    private Integer totalPerguntas;

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
}
