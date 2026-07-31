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

    /**
     * Uma frase dizendo do que se trata. Aparece já no card, antes de abrir:
     * é o que deixa o aluno escolher o que estudar sem ter de abrir tudo.
     *
     * Os três campos abaixo aceitam nulo no banco de propósito. O projeto não
     * tem ferramenta de migração e roda com ddl-auto: update, que não consegue
     * adicionar coluna NOT NULL a uma tabela que já tem linhas — declará-los
     * obrigatórios quebrava a subida em qualquer banco já existente. Quem
     * garante o preenchimento é o ConteudoMateriaSeed, que reescreve o conjunto.
     */
    @Column(length = 500)
    private String resumo;

    @Column(nullable = false, length = 4000)
    private String texto;

    /** Exemplo concreto ou macete — fecha a leitura com algo que gruda. */
    @Column(length = 1000)
    private String exemplo;

    /** Minutos estimados de leitura. Mostrado no card para não assustar. */
    @Column(nullable = true)
    private Integer minutos;

    /** Ordem de exibição dentro da matéria. */
    private int ordem;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Materia getMateria() { return materia; }
    public void setMateria(Materia materia) { this.materia = materia; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getResumo() { return resumo; }
    public void setResumo(String resumo) { this.resumo = resumo; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public String getExemplo() { return exemplo; }
    public void setExemplo(String exemplo) { this.exemplo = exemplo; }
    public Integer getMinutos() { return minutos; }
    public void setMinutos(Integer minutos) { this.minutos = minutos; }
    public int getOrdem() { return ordem; }
    public void setOrdem(int ordem) { this.ordem = ordem; }
}
