package com.nexo.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

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

    private LocalDate dataNascimento;

    private String sexo;

    /** Matérias que o docente leciona (cadastro do diretor). */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "professor_materias",
            joinColumns = @JoinColumn(name = "professor_id"),
            inverseJoinColumns = @JoinColumn(name = "materia_id"))
    @OrderBy("nome")
    private Set<Materia> materias = new LinkedHashSet<>();

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

    /**
     * Matérias em uma linha ("História, Geografia") — é o que o monitoramento
     * docente e o dashboard do professor exibem no lugar do antigo campo texto.
     */
    public String getDisciplinas() {
        return materias.stream().map(Materia::getNome).collect(Collectors.joining(", "));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public LocalDate getDataNascimento() { return dataNascimento; }
    public void setDataNascimento(LocalDate dataNascimento) { this.dataNascimento = dataNascimento; }
    public String getSexo() { return sexo; }
    public void setSexo(String sexo) { this.sexo = sexo; }
    public Set<Materia> getMaterias() { return materias; }
    public void setMaterias(Set<Materia> materias) { this.materias = materias; }
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
