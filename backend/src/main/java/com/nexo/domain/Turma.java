package com.nexo.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "turmas")
public class Turma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    private int anoLetivo;

    private String turno;

    /**
     * Limite de vagas da turma — controla a ocupação que a secretaria enxerga.
     * Integer (e não int): bancos criados antes da coluna têm linhas NULL até a
     * migração preenchê-las (ver SchemaMigracao).
     */
    private Integer capacidade = CAPACIDADE_PADRAO;

    public static final int CAPACIDADE_PADRAO = 35;

    /** Professor responsável (usado no dashboard/monitoramento docente). */
    @ManyToOne(fetch = FetchType.LAZY)
    private Professor professor;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public int getAnoLetivo() { return anoLetivo; }
    public void setAnoLetivo(int anoLetivo) { this.anoLetivo = anoLetivo; }
    public String getTurno() { return turno; }
    public void setTurno(String turno) { this.turno = turno; }
    /** Etapa de ensino, derivada do nome ("9º Ano B" → fundamental, "1º Ano EM A" → médio). */
    public SegmentoEnsino getSegmento() { return SegmentoEnsino.daTurma(nome); }
    public int getCapacidade() { return capacidade != null ? capacidade : CAPACIDADE_PADRAO; }
    public void setCapacidade(Integer capacidade) { this.capacidade = capacidade; }
    public Professor getProfessor() { return professor; }
    public void setProfessor(Professor professor) { this.professor = professor; }
}
