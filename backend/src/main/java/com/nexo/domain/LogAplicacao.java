package com.nexo.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Um registro do log da aplicação (WARN/ERROR), como o Logback o emitiu.
 *
 * <p>Diferente de {@link EventoAuditoria}, que conta o que as <em>pessoas</em> fizeram
 * (entrou, alterou, errou a senha), este conta o que <em>o sistema</em> viveu: exceção
 * não tratada, acesso negado, requisição lenta, falha numa rotina agendada. Sem ele o
 * único lugar onde isso existia era o console do contêiner, que some a cada deploy.
 */
@Entity
@Table(name = "logs_aplicacao",
       indexes = {
               @Index(name = "idx_logs_aplicacao_criado_em", columnList = "criado_em"),
               @Index(name = "idx_logs_aplicacao_nivel", columnList = "nivel, criado_em")
       })
@Getter
@Setter
public class LogAplicacao {

    public enum Nivel { INFO, WARN, ERROR }

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

    @Column(nullable = false)
    private Instant criadoEm = Instant.now();
}
