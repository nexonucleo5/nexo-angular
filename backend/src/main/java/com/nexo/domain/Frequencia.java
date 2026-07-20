package com.nexo.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "frequencias",
       uniqueConstraints = @UniqueConstraint(columnNames = {"aluno_id", "turma_id", "data"}))
public class Frequencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Aluno aluno;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Turma turma;

    @Column(nullable = false)
    private LocalDate data;

    private boolean presente;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }
    public Turma getTurma() { return turma; }
    public void setTurma(Turma turma) { this.turma = turma; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public boolean isPresente() { return presente; }
    public void setPresente(boolean presente) { this.presente = presente; }
}
