package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Atividade recente do aluno (feed de gamificação do dashboard). */
@Entity
@Table(name = "atividades_aluno")
public class AtividadeAluno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Aluno aluno;

    @Column(nullable = false)
    private String titulo;

    private String materia;

    private int xp;

    /** Progresso da atividade (0-100). */
    private int progresso;

    /** Emoji ilustrativo exibido no feed. */
    private String icone;

    private Instant criadaEm;

    public AtividadeAluno() {}

    public AtividadeAluno(Aluno aluno, String titulo, String materia, int xp, int progresso, String icone, Instant criadaEm) {
        this.aluno = aluno;
        this.titulo = titulo;
        this.materia = materia;
        this.xp = xp;
        this.progresso = progresso;
        this.icone = icone;
        this.criadaEm = criadaEm;
    }

    public Long getId() { return id; }
    public Aluno getAluno() { return aluno; }
    public String getTitulo() { return titulo; }
    public String getMateria() { return materia; }
    public int getXp() { return xp; }
    public int getProgresso() { return progresso; }
    public String getIcone() { return icone; }
    public Instant getCriadaEm() { return criadaEm; }
}
