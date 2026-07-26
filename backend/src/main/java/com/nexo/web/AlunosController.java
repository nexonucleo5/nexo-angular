package com.nexo.web;

import com.nexo.api.ApiException;
import com.nexo.domain.Nota;
import com.nexo.domain.ObservacaoPedagogica;
import com.nexo.repository.AlunoRepository;
import com.nexo.repository.NotaRepository;
import com.nexo.repository.ObservacaoPedagogicaRepository;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.AlunoService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/alunos")
@org.springframework.transaction.annotation.Transactional
public class AlunosController {

    private final AlunoService alunoService;
    private final AlunoRepository alunos;
    private final NotaRepository notas;
    private final ObservacaoPedagogicaRepository observacoes;

    public AlunosController(AlunoService alunoService, AlunoRepository alunos,
                            NotaRepository notas, ObservacaoPedagogicaRepository observacoes) {
        this.alunoService = alunoService;
        this.alunos = alunos;
        this.notas = notas;
        this.observacoes = observacoes;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRETOR')")
    public AlunoService.AlunoCriado cadastrar(@RequestBody AlunoService.CadastroAluno request,
                                              @AuthenticationPrincipal UsuarioAutenticado operador) {
        return alunoService.cadastrar(request, operador.nome());
    }

    // ── Notas ────────────────────────────────────────────────────────────────

    public record EditarNotasRequest(String disciplina, String periodo,
                                     Double p1, Double p2, Double t1, Double participacao) {}

    public record NotaDTO(Long id, Long alunoId, String aluno, String disciplina, String periodo,
                          Double p1, Double p2, Double t1, Double participacao, Double media) {
        static NotaDTO of(Nota n) {
            return new NotaDTO(n.getId(), n.getAluno().getId(), n.getAluno().getNome(), n.getDisciplina(),
                    n.getPeriodo(), n.getP1(), n.getP2(), n.getT1(), n.getParticipacao(), n.getMedia());
        }
    }

    @PatchMapping("/{alunoId}/notas")
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public NotaDTO editarNotas(@PathVariable Long alunoId, @RequestBody EditarNotasRequest request) {
        validarNota("p1", request.p1());
        validarNota("p2", request.p2());
        validarNota("t1", request.t1());
        validarNota("participacao", request.participacao());

        var aluno = alunos.findById(alunoId)
                .orElseThrow(() -> ApiException.notFound("Aluno não encontrado."));

        String disciplina = request.disciplina() != null ? request.disciplina() : "Geral";
        String periodo = request.periodo() != null ? request.periodo() : "2026-1";

        Nota nota = notas.findByAlunoId(alunoId).stream()
                .filter(n -> n.getDisciplina().equals(disciplina) && n.getPeriodo().equals(periodo))
                .findFirst()
                .orElseGet(() -> {
                    Nota nova = new Nota();
                    nova.setAluno(aluno);
                    nova.setTurma(aluno.getTurma());
                    nova.setDisciplina(disciplina);
                    nova.setPeriodo(periodo);
                    return nova;
                });

        if (request.p1() != null) nota.setP1(request.p1());
        if (request.p2() != null) nota.setP2(request.p2());
        if (request.t1() != null) nota.setT1(request.t1());
        if (request.participacao() != null) nota.setParticipacao(request.participacao());
        return NotaDTO.of(notas.save(nota));
    }

    private void validarNota(String campo, Double valor) {
        if (valor != null && (valor < 0 || valor > 10)) {
            throw ApiException.validation("Nota fora do intervalo permitido.",
                    java.util.Map.of(campo, "A nota deve estar entre 0 e 10."));
        }
    }

    // ── Observações pedagógicas ──────────────────────────────────────────────

    public record NovaObservacaoRequest(@NotBlank String texto) {}

    public record ObservacaoDTO(Long id, Long alunoId, String autor, String texto, Instant criadaEm) {
        static ObservacaoDTO of(ObservacaoPedagogica o) {
            return new ObservacaoDTO(o.getId(), o.getAluno().getId(), o.getAutorNome(), o.getTexto(), o.getCriadaEm());
        }
    }

    @GetMapping("/{alunoId}/observacoes")
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public List<ObservacaoDTO> listarObservacoes(@PathVariable Long alunoId) {
        return observacoes.findByAlunoIdOrderByCriadaEmDesc(alunoId).stream().map(ObservacaoDTO::of).toList();
    }

    @PostMapping("/{alunoId}/observacoes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public ObservacaoDTO criarObservacao(@PathVariable Long alunoId,
                                         @RequestBody NovaObservacaoRequest request,
                                         @AuthenticationPrincipal UsuarioAutenticado autor) {
        var aluno = alunos.findById(alunoId)
                .orElseThrow(() -> ApiException.notFound("Aluno não encontrado."));
        ObservacaoPedagogica obs = new ObservacaoPedagogica();
        obs.setAluno(aluno);
        obs.setAutorNome(autor.nome());
        obs.setTexto(request.texto());
        return ObservacaoDTO.of(observacoes.save(obs));
    }
}
