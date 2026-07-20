package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Atividade recente do professor (feed do dashboard). */
@Entity
@Table(name = "atividades_professor")
public class AtividadeProfessor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Professor professor;

    /** correcao | avaliacao | aviso | diario */
    private String tipo;

    @Column(nullable = false)
    private String descricao;

    private String turma;

    private String icone;

    private String cor;

    private Instant criadaEm;

    public AtividadeProfessor() {}

    public AtividadeProfessor(Professor professor, String tipo, String descricao, String turma,
                              String icone, String cor, Instant criadaEm) {
        this.professor = professor;
        this.tipo = tipo;
        this.descricao = descricao;
        this.turma = turma;
        this.icone = icone;
        this.cor = cor;
        this.criadaEm = criadaEm;
    }

    public Long getId() { return id; }
    public Professor getProfessor() { return professor; }
    public String getTipo() { return tipo; }
    public String getDescricao() { return descricao; }
    public String getTurma() { return turma; }
    public String getIcone() { return icone; }
    public String getCor() { return cor; }
    public Instant getCriadaEm() { return criadaEm; }
}
