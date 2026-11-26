package com.nexo.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Um registro do log da aplicação, como o Logback o emitiu.
 *
 * <p>Diferente de {@link EventoAuditoria}, que conta o que as <em>pessoas</em> fizeram
 * (entrou, alterou, errou a senha), este conta o que <em>o sistema</em> viveu: exceção
 * não tratada, acesso negado, requisição lenta, falha numa rotina agendada, e também
 * eventos pedagógicos detalhados (aluno iniciou/errou/concluiu desafio).
 * Sem ele o único lugar onde isso existia era o console do contêiner, que some a cada deploy.
 */
@Entity
@Table(name = "logs_aplicacao",
       indexes = {
               @Index(name = "idx_logs_aplicacao_criado_em", columnList = "criado_em"),
               @Index(name = "idx_logs_aplicacao_nivel", columnList = "nivel, criado_em"),
               @Index(name = "idx_logs_aplicacao_categoria", columnList = "categoria, criado_em")
       })
@Getter
@Setter
public class LogAplicacao {

    public enum Nivel { INFO, WARN, ERROR }

    /**
     * Categoria semântica do log — permite filtrar eventos pedagógicos separadamente
     * dos erros de sistema. SISTEMA é o padrão para logs técnicos (WARN/ERROR);
     * DESAFIO agrupa toda a jornada do aluno nos desafios.
     */
    public enum Categoria { SISTEMA, DESAFIO, ACESSO, CONTEUDO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Nivel nivel;

    /** Classe que emitiu o log (nome completo do logger). */
    @Column(nullable = false, length = 300)
    private String origem;

    @Column(nullable = false, length = 1000)
    private String mensagem;

    /** Stack trace, truncado — o suficiente para achar a causa sem encher a tabela. */
    @Column(length = 4000)
    private String excecao;

    /** Identificador da requisição (cabeçalho X-Request-Id): liga o log ao que o usuário viu. */
    @Column(length = 40)
    private String requisicaoId;

    @Column(length = 10)
    private String metodo;

    @Column(length = 300)
    private String caminho;

    private String usuario;

    /**
     * Quanto a requisição levou, quando o log é sobre lentidão. Fica fora da mensagem para
     * que "Requisição lenta" seja sempre o mesmo texto e possa ser agrupada por rota.
     */
    private Integer duracaoMs;

    /** Categoria semântica; nula em logs legados equivale a SISTEMA. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Categoria categoria;

    /** ID do aluno envolvido no evento, quando aplicável (ex.: desafio). */
    private Long alunoId;

    /** ID do desafio envolvido no evento, quando aplicável. */
    private Long desafioId;

    /**
     * Informações extras estruturadas em texto livre (ex.: "acertos=3/5 tentativa=2 xp=0").
     * Mantido como texto para não exigir tabela extra e ainda ser pesquisável.
     */
    @Column(length = 500)
    private String detalhes;

    @Column(nullable = false)
    private Instant criadoEm = Instant.now();
}
