package com.nexo.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

import lombok.Getter;

/** Aula agendada na grade de horários do professor (próximas aulas do dashboard). */
@Entity
@Table(name = "aulas_agendadas")
@Getter
public class AulaAgendada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Professor professor;

    @ManyToOne(fetch = FetchType.LAZY)
    private Turma turma;

    private String disciplina;

    private LocalDate data;

    /** Horário no formato HH:mm. */
    private String hora;

    private String sala;

    private int qtdAlunos;

    public AulaAgendada() {}

    public AulaAgendada(Professor professor, Turma turma, String disciplina, LocalDate data,
                        String hora, String sala, int qtdAlunos) {
        this.professor = professor;
        this.turma = turma;
        this.disciplina = disciplina;
        this.data = data;
        this.hora = hora;
        this.sala = sala;
        this.qtdAlunos = qtdAlunos;
    }
}
