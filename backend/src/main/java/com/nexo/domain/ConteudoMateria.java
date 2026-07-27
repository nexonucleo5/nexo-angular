package com.nexo.domain;

import jakarta.persistence.*;

/**
 * Conteúdo/documento de uma matéria (texto estruturado, sem upload de arquivo
 * nesta etapa). Substitui gradualmente os tópicos hardcoded de materias.data.ts.
 */
@Entity
@Table(name = "conteudos_materia")
public class ConteudoMateria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Materia materia;

    @Column(nullable = false)
    private String titulo;

    @Column(nullable = false, length = 4000)
    private String texto;

    /** Ordem de exibição dentro da matéria. */
    private int ordem;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Materia getMateria() { return materia; }
    public void setMateria(Materia materia) { this.materia = materia; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public int getOrdem() { return ordem; }
    public void setOrdem(int ordem) { this.ordem = ordem; }
}
