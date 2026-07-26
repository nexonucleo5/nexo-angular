package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "alunos")
public class Aluno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    private Usuario usuario;

    @Column(nullable = false)
    private String nome;

    private String sexo;

    private LocalDate dataNascimento;

    @Column(unique = true)
    private String emailInstitucional;

    @ManyToOne(fetch = FetchType.LAZY)
    private Turma turma;

    /** Índice de engajamento (0-100), atualizado a partir de acessos/entregas. */
    private int engajamento;

    /** Foto de perfil (usada em telas de gestão como Evasão). */
    private String foto;

    /** Último acesso do aluno à plataforma. */
    private Instant ultimoAcessoEm;

    /** Nº de intervenções pedagógicas registradas (gestão de evasão). */
    private int intervencoes;

    /** Data da intervenção mais recente. */
    private Instant ultimaIntervencaoEm;

    // ── Gamificação (dashboard do aluno) ──
    private int xpTotal;
    private int xpSemana;
    private int metaSemanalXp;
    private int ofensivaDias;
    private int tarefasFeitasHoje;
    private int tarefasHoje;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getSexo() { return sexo; }
    public void setSexo(String sexo) { this.sexo = sexo; }
    public LocalDate getDataNascimento() { return dataNascimento; }
    public void setDataNascimento(LocalDate dataNascimento) { this.dataNascimento = dataNascimento; }
    public String getEmailInstitucional() { return emailInstitucional; }
    public void setEmailInstitucional(String emailInstitucional) { this.emailInstitucional = emailInstitucional; }
    public Turma getTurma() { return turma; }
    public void setTurma(Turma turma) { this.turma = turma; }
    public int getEngajamento() { return engajamento; }
    public void setEngajamento(int engajamento) { this.engajamento = engajamento; }
    public String getFoto() { return foto; }
    public void setFoto(String foto) { this.foto = foto; }
    public Instant getUltimoAcessoEm() { return ultimoAcessoEm; }
    public void setUltimoAcessoEm(Instant ultimoAcessoEm) { this.ultimoAcessoEm = ultimoAcessoEm; }
    public int getIntervencoes() { return intervencoes; }
    public void setIntervencoes(int intervencoes) { this.intervencoes = intervencoes; }
    public Instant getUltimaIntervencaoEm() { return ultimaIntervencaoEm; }
    public void setUltimaIntervencaoEm(Instant ultimaIntervencaoEm) { this.ultimaIntervencaoEm = ultimaIntervencaoEm; }
    public int getXpTotal() { return xpTotal; }
    public void setXpTotal(int xpTotal) { this.xpTotal = xpTotal; }
    public int getXpSemana() { return xpSemana; }
    public void setXpSemana(int xpSemana) { this.xpSemana = xpSemana; }
    public int getMetaSemanalXp() { return metaSemanalXp; }
    public void setMetaSemanalXp(int metaSemanalXp) { this.metaSemanalXp = metaSemanalXp; }
    public int getOfensivaDias() { return ofensivaDias; }
    public void setOfensivaDias(int ofensivaDias) { this.ofensivaDias = ofensivaDias; }
    public int getTarefasFeitasHoje() { return tarefasFeitasHoje; }
    public void setTarefasFeitasHoje(int tarefasFeitasHoje) { this.tarefasFeitasHoje = tarefasFeitasHoje; }
    public int getTarefasHoje() { return tarefasHoje; }
    public void setTarefasHoje(int tarefasHoje) { this.tarefasHoje = tarefasHoje; }
}
