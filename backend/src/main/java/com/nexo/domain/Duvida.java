package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "duvidas")
public class Duvida {

    public enum Status { ABERTA, RESPONDIDA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Aluno aluno;

    private String disciplina;

    @Column(nullable = false, length = 4000)
    private String pergunta;

    @Column(length = 4000)
    private String resposta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ABERTA;

    private Instant criadaEm = Instant.now();

    private Instant respondidaEm;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }
    public String getDisciplina() { return disciplina; }
    public void setDisciplina(String disciplina) { this.disciplina = disciplina; }
    public String getPergunta() { return pergunta; }
    public void setPergunta(String pergunta) { this.pergunta = pergunta; }
    public String getResposta() { return resposta; }
    public void setResposta(String resposta) { this.resposta = resposta; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCriadaEm() { return criadaEm; }
    public void setCriadaEm(Instant criadaEm) { this.criadaEm = criadaEm; }
    public Instant getRespondidaEm() { return respondidaEm; }
    public void setRespondidaEm(Instant respondidaEm) { this.respondidaEm = respondidaEm; }
}
