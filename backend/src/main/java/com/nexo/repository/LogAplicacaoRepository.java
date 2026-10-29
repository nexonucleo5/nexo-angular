package com.nexo.repository;

import com.nexo.domain.LogAplicacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LogAplicacaoRepository extends JpaRepository<LogAplicacao, Long> {

    /**
     * Mesma convenção de {@code EventoAuditoriaRepository.buscar}: {@code desde}
     * obrigatório (o PostgreSQL não infere o tipo de um parâmetro de data nulo) e
     * {@code busca} já em minúsculas e com os curingas.
     */
    @Query("""
           select l from LogAplicacao l
           where l.criadoEm >= :desde
             and (:nivel is null or l.nivel = :nivel)
             and (:busca is null
                  or lower(l.mensagem) like :busca
                  or lower(l.origem) like :busca
                  or lower(l.caminho) like :busca
                  or lower(l.usuario) like :busca
                  or lower(l.requisicaoId) like :busca
                  or lower(l.excecao) like :busca)
           order by l.criadoEm desc, l.id desc
           """)
    Page<LogAplicacao> buscar(@Param("nivel") LogAplicacao.Nivel nivel,
                              @Param("busca") String busca,
                              @Param("desde") Instant desde,
                              Pageable pageable);

    /** (nível, total) desde o instante — alimenta os contadores do resumo numa consulta só. */
    @Query("select l.nivel, count(l) from LogAplicacao l where l.criadoEm >= :desde group by l.nivel")
    List<Object[]> contarPorNivelDesde(@Param("desde") Instant desde);

    /**
     * Os problemas que mais se repetiram desde o instante — (nível, origem, mensagem, método,
     * rota, total, última vez). Agrupar pelo texto é o que faz mil ocorrências do mesmo erro
     * virarem uma linha; a rota entra na chave para que "Requisição lenta" em /relatorios e
     * em /contas sejam dois problemas — é a rota que diz onde mexer.
     */
    @Query("""
           select l.nivel, l.origem, l.mensagem, l.metodo, l.caminho, count(l), max(l.criadoEm)
           from LogAplicacao l
           where l.criadoEm >= :desde and l.nivel <> com.nexo.domain.LogAplicacao.Nivel.INFO
           group by l.nivel, l.origem, l.mensagem, l.metodo, l.caminho
           order by count(l) desc, max(l.criadoEm) desc
           """)
    List<Object[]> principaisProblemasDesde(@Param("desde") Instant desde, Pageable pageable);

    /** (quando, nível) de cada WARN/ERROR desde o instante — a matéria-prima do gráfico por hora. */
    @Query("""
           select l.criadoEm, l.nivel from LogAplicacao l
           where l.criadoEm >= :desde and l.nivel <> com.nexo.domain.LogAplicacao.Nivel.INFO
           """)
    List<Object[]> ocorrenciasDesde(@Param("desde") Instant desde);

    @Query("select max(l.criadoEm) from LogAplicacao l where l.nivel = :nivel")
    Instant ultimoDoNivel(@Param("nivel") LogAplicacao.Nivel nivel);

    @Modifying
    @Query("delete from LogAplicacao l where l.criadoEm < :corte")
    int removerAnterioresA(@Param("corte") Instant corte);
}
