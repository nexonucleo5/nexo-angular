package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Um documento entregue pelo responsável para uma matrícula.
 *
 * <p>A existência da linha é a entrega: não há campo "entregue = false", porque o
 * que não foi entregue simplesmente não tem linha. Isso evita o estado ambíguo de
 * uma linha marcada como não entregue conviver com nenhuma linha.
 *
 * <p>Guarda quem recebeu e quando — é o que responde "quando isso foi entregue?"
 * meses depois, sem depender da memória de ninguém.
 */
@Entity
@Table(name = "documentos_entregues",
        uniqueConstraints = @UniqueConstraint(name = "uk_documento_por_matricula",
                columnNames = {"matricula_id", "tipo"}))
public class DocumentoEntregue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "matricula_id", nullable = false)
    private Matricula matricula;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40)
    private TipoDocumento tipo;

    private Instant entregueEm = Instant.now();

    /** Quem registrou a entrega — nome, igual ao resto da auditoria. */
    @Column(length = 120)
    private String recebidoPor;

    /** Ex.: "cópia simples, original conferido" ou "válido até 03/2027". */
    @Column(length = 300)
    private String observacao;

    public DocumentoEntregue() {}

    public DocumentoEntregue(Matricula matricula, TipoDocumento tipo, String recebidoPor, String observacao) {
        this.matricula = matricula;
        this.tipo = tipo;
        this.recebidoPor = recebidoPor;
        this.observacao = observacao;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Matricula getMatricula() { return matricula; }
    public void setMatricula(Matricula matricula) { this.matricula = matricula; }
    public TipoDocumento getTipo() { return tipo; }
    public void setTipo(TipoDocumento tipo) { this.tipo = tipo; }
    public Instant getEntregueEm() { return entregueEm; }
    public void setEntregueEm(Instant entregueEm) { this.entregueEm = entregueEm; }
    public String getRecebidoPor() { return recebidoPor; }
    public void setRecebidoPor(String recebidoPor) { this.recebidoPor = recebidoPor; }
    public String getObservacao() { return observacao; }
    public void setObservacao(String observacao) { this.observacao = observacao; }
}
