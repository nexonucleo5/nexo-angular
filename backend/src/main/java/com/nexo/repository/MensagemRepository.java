package com.nexo.repository;

import com.nexo.domain.Mensagem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {
    List<Mensagem> findByConversaIdOrderByCriadaEmAsc(Long conversaId);

    /**
     * Mensagens de várias conversas numa query só — a listagem da caixa de entrada
     * fazia uma consulta por conversa (N+1) para montar a resposta.
     */
    List<Mensagem> findByConversaIdInOrderByCriadaEmAsc(Collection<Long> conversaIds);
}
