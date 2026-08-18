package com.nexo.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "matriculas")
public class Matricula {

    public enum Status { ATIVA, PENDENTE, TRANCADA, CANCELADA }

    public enum Documentacao { COMPLETA, PENDENTE, INCOMPLETA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Aluno aluno;

    @ManyToOne(fetch = FetchType.LAZY)
    private Turma turma;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDENTE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Documentacao documentacao = Documentacao.PENDENTE;

    private LocalDate dataMatricula = LocalDate.now();

    /**
     * Ano letivo a que este vínculo se refere. É o que separa a matrícula de 2026
     * da rematrícula de 2027 do mesmo aluno — sem ele, renovar criaria uma segunda
     * matrícula indistinguível da primeira.
     *
     * <p>Integer para o banco antigo: linha gravada antes da coluna existir fica
     * nula e o getter cai no ano da própria data de matrícula (ver SchemaMigracao,
     * que também preenche as linhas de uma vez).
     */
    @Column(name = "ano_letivo")
    private Integer anoLetivo;

    /** A matrícula que deu origem a esta, quando veio de rematrícula. */
    @Column(name = "origem_matricula_id")
    private Long origemMatriculaId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }
    public Turma getTurma() { return turma; }
    public void setTurma(Turma turma) { this.turma = turma; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Documentacao getDocumentacao() { return documentacao; }
    public void setDocumentacao(Documentacao documentacao) { this.documentacao = documentacao; }
    public LocalDate getDataMatricula() { return dataMatricula; }
    public void setDataMatricula(LocalDate dataMatricula) { this.dataMatricula = dataMatricula; }
    /** Nunca nulo: sem a coluna preenchida, vale o ano da data de matrícula. */
    public int getAnoLetivo() {
        if (anoLetivo != null) return anoLetivo;
        return dataMatricula != null ? dataMatricula.getYear() : LocalDate.now().getYear();
    }
    public void setAnoLetivo(Integer anoLetivo) { this.anoLetivo = anoLetivo; }
    public Long getOrigemMatriculaId() { return origemMatriculaId; }
    public void setOrigemMatriculaId(Long origemMatriculaId) { this.origemMatriculaId = origemMatriculaId; }
}
