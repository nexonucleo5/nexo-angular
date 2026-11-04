package com.nexo.service;

import com.nexo.domain.EventoAuditoria;
import com.nexo.repository.EventoAuditoriaRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class AuditoriaService {

    private final EventoAuditoriaRepository repository;

    public AuditoriaService(EventoAuditoriaRepository repository) {
        this.repository = repository;
    }

    public void registrar(String usuarioNome, EventoAuditoria.Tipo tipo, String acao, String detalhe, String ip) {
        repository.save(new EventoAuditoria(usuarioNome, tipo, acao, detalhe, ip));
    }

    /**
     * Registra uma <em>consulta</em> no máximo uma vez por usuário a cada {@code janela}.
     *
     * <p>Quem olha uma tela de logs dispara dezenas de requisições — digitar na busca, trocar o
     * filtro, paginar. Um evento por requisição encheria a trilha com as leituras do próprio
     * administrador e empurraria para fora o que ela existe para mostrar. Uma entrada por
     * "sessão de consulta" ainda diz quem olhou, de onde e quando, que é o que importa se a
     * conta for usada indevidamente.
     */
    public void registrarConsulta(String usuarioNome, String acao, String ip, Duration janela) {
        Instant desde = Instant.now().minus(janela);
        if (repository.existsByUsuarioNomeAndAcaoAndCriadoEmAfter(usuarioNome, acao, desde)) return;
        registrar(usuarioNome, EventoAuditoria.Tipo.ACESSO, acao, null, ip);
    }
}
