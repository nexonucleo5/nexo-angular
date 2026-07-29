package com.nexo.web;

import com.nexo.api.ApiException;
import com.nexo.domain.Professor;
import com.nexo.repository.ProfessorRepository;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.ProfessorService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/professores")
@org.springframework.transaction.annotation.Transactional
public class ProfessoresController {

    private final ProfessorService professorService;
    private final ProfessorRepository professores;

    public ProfessoresController(ProfessorService professorService, ProfessorRepository professores) {
        this.professorService = professorService;
        this.professores = professores;
    }

    public record ProfessorDTO(Long id, String nome, String email, String sexo,
                               LocalDate dataNascimento, String disciplinas, String foto) {
        static ProfessorDTO of(Professor p) {
            return new ProfessorDTO(p.getId(), p.getNome(), p.getEmail(), p.getSexo(),
                    p.getDataNascimento(), p.getDisciplinas(), p.getFoto());
        }
    }

    /** Recurso endereçável do docente — destino do Location do cadastro. */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('DIRETOR')")
    public ProfessorDTO detalhar(@PathVariable Long id) {
        return professores.findById(id).map(ProfessorDTO::of)
                .orElseThrow(() -> ApiException.notFound("Professor não encontrado."));
    }

    @PostMapping
    @PreAuthorize("hasRole('DIRETOR')")
    public ResponseEntity<ProfessorService.ProfessorCriado> cadastrar(
            @RequestBody ProfessorService.CadastroProfessor request,
            @AuthenticationPrincipal UsuarioAutenticado operador) {
        ProfessorService.ProfessorCriado criado = professorService.cadastrar(request, operador.nome());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri()).body(criado);
    }
}
