package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

import lombok.Getter;

/** Atividade recente do aluno (feed de gamificação do dashboard). */
@Entity
@Table(name = "atividades_aluno",
       // Mesmo caso: findTop6ByAlunoIdOrderByCriadaEmDesc.
       indexes = @Index(name = "idx_atividades_aluno_criada", columnList = "aluno_id, criada_em"))
@Getter
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
}
