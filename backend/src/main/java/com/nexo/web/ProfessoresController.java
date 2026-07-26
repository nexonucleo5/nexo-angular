package com.nexo.web;

import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.ProfessorService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/professores")
@org.springframework.transaction.annotation.Transactional
public class ProfessoresController {

    private final ProfessorService professorService;

    public ProfessoresController(ProfessorService professorService) {
        this.professorService = professorService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRETOR')")
    public ProfessorService.ProfessorCriado cadastrar(@RequestBody ProfessorService.CadastroProfessor request,
                                                      @AuthenticationPrincipal UsuarioAutenticado operador) {
        return professorService.cadastrar(request, operador.nome());
    }
}
