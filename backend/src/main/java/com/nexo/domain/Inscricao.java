package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Inscrição de um aluno numa turma do sistema de aprendizado.
 *
 * <p>Substitui a antiga {@code Matricula}. A matrícula era o vínculo jurídico do
 * aluno com a escola, e carregava o que isso implica: status de trancamento e
 * cancelamento, situação da documentação, ano letivo, cadeia de rematrícula. Nada
 * disso pertence aqui — quem matricula é o sistema de aula da escola. O que este
 * sistema precisa saber é só uma coisa: <b>de qual turma este aluno vê o
 * conteúdo</b>, já que matéria, desafio e progresso são todos recortados por turma.
 *
 * <p>Daí sobrarem três campos. {@code ativo} existe para desligar o acesso ao
 * conteúdo da turma sem apagar o histórico de progresso do aluno.
 */
@Entity
@Table(name = "inscricoes")
public class Inscricao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Aluno aluno;

    @ManyToOne(fetch = FetchType.LAZY)
    private Turma turma;

    /** Desligado, o aluno deixa de ver o conteúdo da turma — mas o progresso fica. */
    @Column(nullable = false)
    private boolean ativo = true;

    private Instant criadaEm = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }
    public Turma getTurma() { return turma; }
    public void setTurma(Turma turma) { this.turma = turma; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public Instant getCriadaEm() { return criadaEm; }
    public void setCriadaEm(Instant criadaEm) { this.criadaEm = criadaEm; }
}
