package com.nexo.repository;

import com.nexo.domain.CadastroPendente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CadastroPendenteRepository extends JpaRepository<CadastroPendente, Long> {

    /** A turma vem junto: a fila mostra o nome dela, e sem o join seria uma consulta por linha. */
    @Query("""
           select c from CadastroPendente c join fetch c.turma
           where c.situacao = :situacao
           order by c.criadoEm
           """)
    List<CadastroPendente> listarPorSituacao(@Param("situacao") CadastroPendente.Situacao situacao);

    long countBySituacao(CadastroPendente.Situacao situacao);

    /**
     * Decide o pedido de forma atômica: só muda se ainda estiver pendente. Dois cliques
     * (ou dois diretores) na mesma linha não criam o aluno duas vezes — quem perde a
     * corrida recebe 0 linhas afetadas.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           update CadastroPendente c
              set c.situacao = :nova, c.decididoPor = :por, c.decididoEm = :agora
            where c.id = :id and c.situacao = com.nexo.domain.CadastroPendente.Situacao.PENDENTE
           """)
    int decidir(@Param("id") Long id,
                @Param("nova") CadastroPendente.Situacao nova,
                @Param("por") String por,
                @Param("agora") Instant agora);
}
