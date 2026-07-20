package com.nexo.web;

import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.AlunoDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard de gamificação do aluno logado. */
@RestController
@RequestMapping("/api/aluno")
@PreAuthorize("hasRole('ALUNO')")
public class AlunoDashboardController {

    private final AlunoDashboardService service;

    public AlunoDashboardController(AlunoDashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public AlunoDashboardService.AlunoDashboardDTO dashboard(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return service.montar(principal.id());
    }

    @GetMapping("/notas")
    public java.util.List<AlunoDashboardService.NotaAlunoDTO> notas(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return service.notasDoAluno(principal.id());
    }
}
