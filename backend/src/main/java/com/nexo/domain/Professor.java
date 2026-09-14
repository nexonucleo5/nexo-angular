package com.nexo.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "professores")
@Getter
@Setter
public class Professor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    private Usuario usuario;

    @Column(nullable = false)
    private String nome;

    private LocalDate dataNascimento;

    private String sexo;

    /** Matérias que o docente leciona (cadastro do diretor). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "professor_materias",
            joinColumns = @JoinColumn(name = "professor_id"),
            inverseJoinColumns = @JoinColumn(name = "materia_id"))
    @OrderBy("nome")
    private Set<Materia> materias = new LinkedHashSet<>();

    private String email;

    private String foto;

    /** Turmas atendidas (texto para exibição no monitoramento docente). */
    private String turmas;

    // ── Métricas de produtividade (monitoramento docente) ──
    private int correcoesPendentes;
    private double tempoRespostaDias;
    private int interacoesSemana;
    private double avaliacao;
    private int tarefasConcluidas;
    private int tarefasTotal;

    /**
     * Matérias em uma linha ("História, Geografia") — é o que o monitoramento
     * docente e o dashboard do professor exibem no lugar do antigo campo texto.
     */
    public String getDisciplinas() {
        return materias.stream().map(Materia::getNome).collect(Collectors.joining(", "));
    }
}
