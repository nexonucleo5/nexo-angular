package com.nexo.repository;

import com.nexo.domain.ChatMensagemOculta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMensagemOcultaRepository extends JpaRepository<ChatMensagemOculta, Long> {

    boolean existsByUsuarioIdAndMensagemId(Long usuarioId, Long mensagemId);

    void deleteByMensagemId(Long mensagemId);
}
