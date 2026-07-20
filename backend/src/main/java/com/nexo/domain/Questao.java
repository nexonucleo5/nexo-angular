package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "questoes")
public class Questao {

    public enum Tipo { OBJETIVA, DISSERTATIVA }

    public enum Dificuldade { FACIL, MEDIA, DIFICIL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 4000)
    private String enunciado;

    private String disciplina;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tipo tipo = Tipo.OBJETIVA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Dificuldade dificuldade = Dificuldade.MEDIA;

    private Instant criadaEm = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEnunciado() { return enunciado; }
    public void setEnunciado(String enunciado) { this.enunciado = enunciado; }
    public String getDisciplina() { return disciplina; }
    public void setDisciplina(String disciplina) { this.disciplina = disciplina; }
    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }
    public Dificuldade getDificuldade() { return dificuldade; }
    public void setDificuldade(Dificuldade dificuldade) { this.dificuldade = dificuldade; }
    public Instant getCriadaEm() { return criadaEm; }
    public void setCriadaEm(Instant criadaEm) { this.criadaEm = criadaEm; }
}
