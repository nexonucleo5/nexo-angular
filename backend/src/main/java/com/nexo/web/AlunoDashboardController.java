package com.nexo.web;

import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.AlunoDashboardService;
import com.nexo.service.ProgressoMateriaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** O que é do aluno logado: gamificação, notas, matérias e progresso de estudo. */
@RestController
@RequestMapping("/api/aluno")
@PreAuthorize("hasRole('ALUNO')")
public class AlunoDashboardController {

    private final AlunoDashboardService service;
    private final ProgressoMateriaService progresso;

    public AlunoDashboardController(AlunoDashboardService service, ProgressoMateriaService progresso) {
        this.service = service;
        this.progresso = progresso;
    }

    @GetMapping("/dashboard")
    public AlunoDashboardService.AlunoDashboardDTO dashboard(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return service.montar(principal.id());
    }

    @GetMapping("/notas")
    public java.util.List<AlunoDashboardService.NotaAlunoDTO> notas(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return service.notasDoAluno(principal.id());
    }

    // ── Matérias e progresso de estudo ───────────────────────────────────────

    /**
     * As matérias que o aluno cursa, com o progresso real dele em cada uma.
     * Recurso separado de /api/materias de propósito: aquele é o catálogo da
     * escola, este é a visão do aluno sobre o próprio estudo.
     */
    @GetMapping("/materias")
    public java.util.List<ProgressoMateriaService.MateriaProgressoDTO> materias(
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        return progresso.materiasDoAluno(principal.id());
    }

    /** Conteúdos já concluídos numa matéria — a tela marca os itens da lista. */
    @GetMapping("/materias/{materiaId}/concluidos")
    public java.util.List<Long> concluidos(@PathVariable Long materiaId,
                                           @AuthenticationPrincipal UsuarioAutenticado principal) {
        return progresso.concluidosNaMateria(principal.id(), materiaId);
    }

    /**
     * PUT/DELETE e não POST: concluir é um estado do conteúdo para aquele aluno,
     * não um evento que se acumula. Os dois são idempotentes — clicar duas vezes
     * no mesmo botão não conta duas conclusões nem estoura erro.
     */
    @PutMapping("/conteudos/{conteudoId}/concluido")
    public ResponseEntity<Void> concluir(@PathVariable Long conteudoId,
                                         @AuthenticationPrincipal UsuarioAutenticado principal) {
        progresso.concluir(principal.id(), conteudoId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/conteudos/{conteudoId}/concluido")
    public ResponseEntity<Void> desmarcar(@PathVariable Long conteudoId,
                                          @AuthenticationPrincipal UsuarioAutenticado principal) {
        progresso.desmarcar(principal.id(), conteudoId);
        return ResponseEntity.noContent().build();
    }
}
