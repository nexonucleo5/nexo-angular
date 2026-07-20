package com.nexo.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "matriculas")
public class Matricula {

    public enum Status { ATIVA, PENDENTE, TRANCADA, CANCELADA }

    public enum Documentacao { COMPLETA, PENDENTE, INCOMPLETA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Aluno aluno;

    @ManyToOne(fetch = FetchType.LAZY)
    private Turma turma;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDENTE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Documentacao documentacao = Documentacao.PENDENTE;

    private LocalDate dataMatricula = LocalDate.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }
    public Turma getTurma() { return turma; }
    public void setTurma(Turma turma) { this.turma = turma; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Documentacao getDocumentacao() { return documentacao; }
    public void setDocumentacao(Documentacao documentacao) { this.documentacao = documentacao; }
    public LocalDate getDataMatricula() { return dataMatricula; }
    public void setDataMatricula(LocalDate dataMatricula) { this.dataMatricula = dataMatricula; }
}
