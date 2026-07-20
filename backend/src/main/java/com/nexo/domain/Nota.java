package com.nexo.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "notas",
       uniqueConstraints = @UniqueConstraint(columnNames = {"aluno_id", "disciplina", "periodo"}))
public class Nota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Aluno aluno;

    @ManyToOne(fetch = FetchType.LAZY)
    private Turma turma;

    @Column(nullable = false)
    private String disciplina;

    /** Período letivo, ex.: "2026-1". */
    @Column(nullable = false)
    private String periodo;

    private Double p1;

    private Double p2;

    private Double t1;

    private Double participacao;

    /** Média ponderada oficial calculada no servidor. */
    public Double getMedia() {
        double soma = 0;
        double pesos = 0;
        if (p1 != null) { soma += p1 * 3; pesos += 3; }
        if (p2 != null) { soma += p2 * 3; pesos += 3; }
        if (t1 != null) { soma += t1 * 2; pesos += 2; }
        if (participacao != null) { soma += participacao * 2; pesos += 2; }
        if (pesos == 0) return null;
        return Math.round((soma / pesos) * 10.0) / 10.0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }
    public Turma getTurma() { return turma; }
    public void setTurma(Turma turma) { this.turma = turma; }
    public String getDisciplina() { return disciplina; }
    public void setDisciplina(String disciplina) { this.disciplina = disciplina; }
    public String getPeriodo() { return periodo; }
    public void setPeriodo(String periodo) { this.periodo = periodo; }
    public Double getP1() { return p1; }
    public void setP1(Double p1) { this.p1 = p1; }
    public Double getP2() { return p2; }
    public void setP2(Double p2) { this.p2 = p2; }
    public Double getT1() { return t1; }
    public void setT1(Double t1) { this.t1 = t1; }
    public Double getParticipacao() { return participacao; }
    public void setParticipacao(Double participacao) { this.participacao = participacao; }
}
