package com.nexo.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "professores")
public class Professor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    private Usuario usuario;

    @Column(nullable = false)
    private String nome;

    private String disciplina;

    private String email;

    private String foto;

    /** Turmas atendidas (texto para exibição no monitoramento docente). */
    private String turmas;

    // ── Métricas de produtividade (monitoramento docente) ──
    private int correcoesPendentes;
    private double tempoRespostaDias;
    private int interacoesSemana;
    private double avaliacao;
    private int tarefasConcluidas;
    private int tarefasTotal;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getDisciplina() { return disciplina; }
    public void setDisciplina(String disciplina) { this.disciplina = disciplina; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFoto() { return foto; }
    public void setFoto(String foto) { this.foto = foto; }
    public String getTurmas() { return turmas; }
    public void setTurmas(String turmas) { this.turmas = turmas; }
    public int getCorrecoesPendentes() { return correcoesPendentes; }
    public void setCorrecoesPendentes(int correcoesPendentes) { this.correcoesPendentes = correcoesPendentes; }
    public double getTempoRespostaDias() { return tempoRespostaDias; }
    public void setTempoRespostaDias(double tempoRespostaDias) { this.tempoRespostaDias = tempoRespostaDias; }
    public int getInteracoesSemana() { return interacoesSemana; }
    public void setInteracoesSemana(int interacoesSemana) { this.interacoesSemana = interacoesSemana; }
    public double getAvaliacao() { return avaliacao; }
    public void setAvaliacao(double avaliacao) { this.avaliacao = avaliacao; }
    public int getTarefasConcluidas() { return tarefasConcluidas; }
    public void setTarefasConcluidas(int tarefasConcluidas) { this.tarefasConcluidas = tarefasConcluidas; }
    public int getTarefasTotal() { return tarefasTotal; }
    public void setTarefasTotal(int tarefasTotal) { this.tarefasTotal = tarefasTotal; }
}
