package com.nexo.repository;

import com.nexo.domain.EventoAuditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface EventoAuditoriaRepository extends JpaRepository<EventoAuditoria, Long> {
    List<EventoAuditoria> findTop200ByOrderByCriadoEmDesc();

    boolean existsByUsuarioNomeAndAcaoAndCriadoEmAfter(String usuarioNome, String acao, Instant desde);

    long countByTipoAndCriadoEmAfter(EventoAuditoria.Tipo tipo, Instant desde);

    /**
     * Eventos para a tela de logs do administrador, do mais recente para o mais antigo.
     *
     * <p>{@code desde} é obrigatório (a camada acima passa {@code Instant.EPOCH} para "sem
     * limite"): um parâmetro de data nulo em {@code :p is null} não tem como o banco
     * inferir o tipo, e o PostgreSQL recusa. {@code busca} já chega em minúsculas e com
     * os curingas, a mesma convenção de {@code UsuarioRepository.buscar}.
     */
    @Query("""
           select e from EventoAuditoria e
           where e.criadoEm >= :desde
             and (:tipo is null or e.tipo = :tipo)
             and (:busca is null
                  or lower(e.usuarioNome) like :busca
                  or lower(e.acao) like :busca
                  or lower(e.detalhe) like :busca
                  or lower(e.ip) like :busca)
           order by e.criadoEm desc, e.id desc
           """)
    Page<EventoAuditoria> buscar(@Param("tipo") EventoAuditoria.Tipo tipo,
                                 @Param("busca") String busca,
                                 @Param("desde") Instant desde,
                                 Pageable pageable);

    /**
     * Apaga os eventos anteriores ao corte — ver {@code AuditoriaRetencaoJob}.
     *
     * <p>Usa o índice {@code idx_eventos_auditoria_criado_em}, que já existia: o
     * DELETE alcança direto a faixa antiga em vez de varrer a tabela.
     */
    @Modifying
    @Query("delete from EventoAuditoria e where e.criadoEm < :corte")
    int removerAnterioresA(@Param("corte") Instant corte);
}
