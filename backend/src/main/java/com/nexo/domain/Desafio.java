package com.nexo.domain;

import jakarta.persistence.*;

/** Catálogo de desafios (gamificação do aluno). */
@Entity
@Table(name = "desafios")
public class Desafio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo;

    private String materia;

    /** FACIL | MEDIO | DIFICIL */
    private String nivel;

    private int xp;

    private int tempoMin;

    public Desafio() {}

    public Desafio(String titulo, String materia, String nivel, int xp, int tempoMin) {
        this.titulo = titulo;
        this.materia = materia;
        this.nivel = nivel;
        this.xp = xp;
        this.tempoMin = tempoMin;
    }

    public Long getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getMateria() { return materia; }
    public String getNivel() { return nivel; }
    public int getXp() { return xp; }
    public int getTempoMin() { return tempoMin; }
}
