package com.nexo.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "avaliacoes")
public class Avaliacao {

    public enum Status { RASCUNHO, PUBLICADA, EM_CORRECAO, CORRIGIDA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo;

    private String disciplina;

    @ManyToOne(fetch = FetchType.LAZY)
    private Turma turma;

    /** Prova, Trabalho, Simulado... */
    private String tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.RASCUNHO;

    private LocalDate data;

    private int entregas;

    private int pendentesCorrecao;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDisciplina() { return disciplina; }
    public void setDisciplina(String disciplina) { this.disciplina = disciplina; }
    public Turma getTurma() { return turma; }
    public void setTurma(Turma turma) { this.turma = turma; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public int getEntregas() { return entregas; }
    public void setEntregas(int entregas) { this.entregas = entregas; }
    public int getPendentesCorrecao() { return pendentesCorrecao; }
    public void setPendentesCorrecao(int pendentesCorrecao) { this.pendentesCorrecao = pendentesCorrecao; }
}
