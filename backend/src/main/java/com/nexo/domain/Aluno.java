package com.nexo.domain;

import jakarta.persistence.*;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * Aluno como o sistema de aprendizado precisa dele: um nome, um acesso e a turma
 * cujo conteúdo ele cursa.
 *
 * <p>Não há dado pessoal aqui de propósito — nascimento, sexo e endereço saíram, e
 * as colunas são removidas no arranque (ver {@link com.nexo.config.SchemaMigracao}).
 * Quem guarda a ficha do aluno é o sistema de aula da escola; este aqui cuida de
 * aprendizado e retenção de conteúdo, e uma base invadida não deve entregar nada
 * além de nome e progresso.
 */
@Entity
@Table(name = "alunos",
       // Recorte por turma: é o filtro de praticamente toda tela de professor, e a
       // coluna que os agregados de nota/frequência atravessam para chegar à turma.
       // Sem ele o Postgres varre a tabela de alunos inteira em cada uma dessas telas.
       indexes = @Index(name = "idx_alunos_turma", columnList = "turma_id"))
@Getter
@Setter
public class Aluno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    private Usuario usuario;

    @Column(nullable = false)
    private String nome;

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

    // ── Privacidade (espelho das chaves de Configurações → Privacidade do aluno) ──
    // O documento JSON de configuração segue sendo o que a tela lê e grava; estas duas
    // colunas existem porque o ranking precisa filtrar no banco, e abrir o JSON de
    // todos os alunos a cada request seria varrer a escola inteira. Quem as mantém é o
    // ConfiguracaoService, na mesma transação que grava o JSON.
    // default true no DDL: o Hibernate adiciona coluna NOT NULL em tabela com linhas
    // só se ela tiver valor padrão.

    /** Aparece no ranking dos outros alunos. */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean exibirNoRanking = true;

    /** Outros alunos veem a foto (hoje, o ranking é o único lugar em que um aluno vê outro). */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean perfilPublico = true;
}
