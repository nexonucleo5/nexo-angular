package com.nexo.domain;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** Progresso de um aluno em um desafio. */
@Entity
@Table(name = "desafios_aluno",
       // Composto e nesta ordem: serve tanto findByAlunoId (prefixo) quanto
       // findByAlunoIdAndDesafioId, que roda a cada abertura e conclusão de desafio.
       indexes = @Index(name = "idx_desafios_aluno_aluno_desafio", columnList = "aluno_id, desafio_id"))
@Getter
public class DesafioAluno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Aluno aluno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Desafio desafio;

    /** ABERTO | PROGRESSO | CONCLUIDO */
    @Setter
    private String status = "ABERTO";

    @Setter
    private int progresso;

    /**
     * Preenchidos apenas quando o aluno gabarita o quiz. Tentativa reprovada não
     * grava placar — assim reabrir o desafio não revela nada da tentativa anterior.
     */
    @Setter
    private Integer acertos;

    @Setter
    private Integer totalPerguntas;

    /**
     * Quantas vezes o aluno já enviou o quiz deste desafio (reprovadas incluídas).
     * Integer (e não int) porque a coluna nasce nula nas linhas criadas antes deste campo.
     */
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Integer tentativas = 0;

    public DesafioAluno() {}

    public DesafioAluno(Aluno aluno, Desafio desafio, String status, int progresso) {
        this.aluno = aluno;
        this.desafio = desafio;
        this.status = status;
        this.progresso = progresso;
    }

    public int getTentativas() { return tentativas == null ? 0 : tentativas; }
    /** Primitivo de propósito: o campo é wrapper só por causa de linha legada nula. */
    public void setTentativas(int tentativas) { this.tentativas = tentativas; }
}
