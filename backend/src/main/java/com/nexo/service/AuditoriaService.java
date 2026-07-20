package com.nexo.service;

import com.nexo.domain.EventoAuditoria;
import com.nexo.repository.EventoAuditoriaRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {

    private final EventoAuditoriaRepository repository;

    public AuditoriaService(EventoAuditoriaRepository repository) {
        this.repository = repository;
    }

    public void registrar(String usuarioNome, EventoAuditoria.Tipo tipo, String acao, String detalhe, String ip) {
        repository.save(new EventoAuditoria(usuarioNome, tipo, acao, detalhe, ip));
    }
}
