package com.nexo.web;

import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.DesafioService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Desafios do aluno logado (catálogo + progresso). */
@RestController
@RequestMapping("/api/aluno/desafios")
@PreAuthorize("hasRole('ALUNO')")
public class DesafiosController {

    private final DesafioService service;

    public DesafiosController(DesafioService service) {
        this.service = service;
    }

    @GetMapping
    public DesafioService.DesafiosResponse listar(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return service.listar(principal.id());
    }

    @PostMapping("/{id}/iniciar")
    public DesafioService.DesafioDTO iniciar(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        return service.iniciar(principal.id(), id);
    }

    @PostMapping("/{id}/concluir")
    public DesafioService.DesafioDTO concluir(@AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        return service.concluir(principal.id(), id);
    }
}
