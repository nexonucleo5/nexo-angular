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

    /**
     * Etapa em que a matéria é ofertada. Física e Química só existem no médio,
     * Ciências só no fundamental, e o resto atravessa as duas (AMBOS).
     *
     * <p>Coluna como texto e não como enum nativo: papel novo em banco existente
     * já custou uma migração (ver SchemaMigracao), e aqui o mesmo vale para
     * segmento futuro. Quem valida é o enum no Java.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "segmento", length = 20)
    private SegmentoEnsino segmento = SegmentoEnsino.AMBOS;

    public Materia() {}

    public Materia(String nome) {
        this.nome = nome;
    }

    public Materia(String nome, SegmentoEnsino segmento) {
        this.nome = nome;
        this.segmento = segmento;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    /** Nunca nulo: linha gravada antes da coluna existir vale como AMBOS. */
    public SegmentoEnsino getSegmento() { return segmento != null ? segmento : SegmentoEnsino.AMBOS; }
    public void setSegmento(SegmentoEnsino segmento) { this.segmento = segmento; }
}
