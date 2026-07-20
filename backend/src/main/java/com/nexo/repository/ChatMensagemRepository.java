package com.nexo.repository;

import com.nexo.domain.ChatMensagem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMensagemRepository extends JpaRepository<ChatMensagem, Long> {

    @Query("""
           select m from ChatMensagem m
           where (m.remetenteId = :a and m.destinatarioId = :b)
              or (m.remetenteId = :b and m.destinatarioId = :a)
           order by m.criadaEm asc
           """)
    List<ChatMensagem> conversa(@Param("a") Long a, @Param("b") Long b);
}
