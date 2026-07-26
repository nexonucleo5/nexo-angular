package com.nexo.domain;

import jakarta.persistence.*;

/**
 * Catálogo de matérias da escola. Antes existia apenas como lista hardcoded no
 * client (materias.data.ts) e como texto solto em Professor/Nota/Avaliação;
 * agora é a tabela de referência usada no cadastro de professor.
 */
@Entity
@Table(name = "materias")
public class Materia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nome;

    public Materia() {}

    public Materia(String nome) {
        this.nome = nome;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
}
