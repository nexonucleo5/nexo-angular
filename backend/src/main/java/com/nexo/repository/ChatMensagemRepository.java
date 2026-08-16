package com.nexo.repository;

import com.nexo.domain.ChatMensagem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMensagemRepository extends JpaRepository<ChatMensagem, Long> {

    /**
     * Trecho final da conversa, do mais recente para o mais antigo — o chamador
     * inverte para exibir. A versão sem limite carregava a conversa inteira a cada
     * abertura da tela: o custo por request crescia junto com o histórico, sem teto.
     */
    @Query("""
           select m from ChatMensagem m
           where (m.remetenteId = :a and m.destinatarioId = :b)
              or (m.remetenteId = :b and m.destinatarioId = :a)
           order by m.criadaEm desc, m.id desc
           """)
    List<ChatMensagem> conversaRecente(@Param("a") Long a, @Param("b") Long b, Pageable limite);
}
