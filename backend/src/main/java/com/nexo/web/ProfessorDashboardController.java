package com.nexo.web;

import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.ProfessorDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard do professor logado. */
@RestController
@RequestMapping("/api/professor")
@PreAuthorize("hasRole('PROFESSOR')")
public class ProfessorDashboardController {

    private final ProfessorDashboardService service;

    public ProfessorDashboardController(ProfessorDashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public ProfessorDashboardService.ProfessorDashboardDTO dashboard(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return service.montar(principal.id());
    }
}
