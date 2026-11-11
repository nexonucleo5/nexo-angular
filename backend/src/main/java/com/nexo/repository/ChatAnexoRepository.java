package com.nexo.repository;

import com.nexo.domain.ChatAnexo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatAnexoRepository extends JpaRepository<ChatAnexo, String> {

    /** Só os metadados — o histórico nunca carrega os bytes. */
    interface Metadados {
        String getId();
        Long getMensagemId();
        String getNome();
        String getTipoConteudo();
        Long getTamanho();
    }

    /** O necessário para responder o download, fora do contexto de persistência (ver FotoPerfilRepository). */
    interface Conteudo {
        Long getMensagemId();
        String getNome();
        String getTipoConteudo();
        byte[] getDados();
    }

    List<Metadados> findByMensagemIdInOrderByCriadoEm(Collection<Long> mensagemIds);

    Optional<Conteudo> findConteudoById(String id);

    void deleteByMensagemId(Long mensagemId);
}
