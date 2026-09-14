package com.nexo.domain;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/** Catálogo de desafios (gamificação do aluno). */
@Entity
@Table(name = "desafios")
@Getter
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

    /**
     * Desafio despublicado sai do catálogo do aluno sem apagar o que já foi
     * respondido. Boolean pelo mesmo motivo de ConteudoMateria.publicado: a
     * coluna nasce nula em banco já existente, e nulo lá vale como publicado.
     */
    @Column(name = "publicado")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Boolean publicado = Boolean.TRUE;

    public Desafio() {}

    public Desafio(String titulo, String materia, String nivel, int xp, int tempoMin) {
        this.titulo = titulo;
        this.materia = materia;
        this.nivel = nivel;
        this.xp = xp;
        this.tempoMin = tempoMin;
    }

    /** Nunca nulo: desafio gravado antes da coluna existir já estava no ar. */
    public boolean isPublicado() { return publicado == null || publicado; }
    /** Primitivo de propósito: o campo é wrapper só por causa de linha legada nula. */
    public void setPublicado(boolean publicado) { this.publicado = publicado; }
}
