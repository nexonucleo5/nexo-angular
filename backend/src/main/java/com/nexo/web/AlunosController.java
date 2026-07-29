package com.nexo.web;

import com.nexo.api.ApiException;
import com.nexo.domain.Aluno;
import com.nexo.domain.Nota;
import com.nexo.domain.ObservacaoPedagogica;
import com.nexo.domain.Professor;
import com.nexo.domain.Turma;
import com.nexo.repository.AlunoRepository;
import com.nexo.repository.NotaRepository;
import com.nexo.repository.ObservacaoPedagogicaRepository;
import com.nexo.repository.ProfessorRepository;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.AlunoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/alunos")
@org.springframework.transaction.annotation.Transactional
public class AlunosController {

    private final AlunoService alunoService;
    private final AlunoRepository alunos;
    private final NotaRepository notas;
    private final ObservacaoPedagogicaRepository observacoes;
    private final ProfessorRepository professores;

    public AlunosController(AlunoService alunoService, AlunoRepository alunos,
                            NotaRepository notas, ObservacaoPedagogicaRepository observacoes,
                            ProfessorRepository professores) {
        this.alunoService = alunoService;
        this.alunos = alunos;
        this.notas = notas;
        this.observacoes = observacoes;
        this.professores = professores;
    }

    /** PROFESSOR só age sobre alunos da turma que leciona; DIRETOR tem acesso irrestrito. */
    private void exigirLeciona(Turma turma, UsuarioAutenticado operador) {
        if (!"PROFESSOR".equals(operador.role())) return;
        Professor professor = professores.findByUsuarioId(operador.id()).orElse(null);
        boolean leciona = professor != null && turma != null
                && turma.getProfessor() != null && turma.getProfessor().getId().equals(professor.getId());
        if (!leciona) {
            throw ApiException.forbidden("Você não leciona a turma deste aluno.");
        }
    }

    public record AlunoDTO(Long id, String nome, String emailInstitucional, String sexo,
                           LocalDate dataNascimento, Long turmaId, String turma,
                           int engajamento, String foto) {
        static AlunoDTO of(Aluno a) {
            return new AlunoDTO(a.getId(), a.getNome(), a.getEmailInstitucional(), a.getSexo(),
                    a.getDataNascimento(),
                    a.getTurma() != null ? a.getTurma().getId() : null,
                    a.getTurma() != null ? a.getTurma().getNome() : null,
                    a.getEngajamento(), a.getFoto());
        }
    }

    /**
     * Recurso endereçável do aluno. Sem ele o POST não tinha para onde apontar o
     * Location, e o aluno só existia dentro de listagens e agregações.
     */
    @GetMapping("/{alunoId}")
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public AlunoDTO detalhar(@PathVariable Long alunoId,
                             @AuthenticationPrincipal UsuarioAutenticado operador) {
        Aluno aluno = alunos.findById(alunoId)
                .orElseThrow(() -> ApiException.notFound("Aluno não encontrado."));
        // Mesmo escopo das demais operações sobre o aluno: um GET novo sem esta
        // checagem entregaria qualquer aluno da escola a qualquer professor.
        exigirLeciona(aluno.getTurma(), operador);
        return AlunoDTO.of(aluno);
    }

    @PostMapping
    @PreAuthorize("hasRole('DIRETOR')")
    public ResponseEntity<AlunoService.AlunoCriado> cadastrar(
            @RequestBody AlunoService.CadastroAluno request,
            @AuthenticationPrincipal UsuarioAutenticado operador) {
        AlunoService.AlunoCriado criado = alunoService.cadastrar(request, operador.nome());
        return ResponseEntity.created(uriDoItem(criado.id())).body(criado);
    }

    /** URI do recurso recém-criado, a partir do caminho da própria requisição. */
    private static URI uriDoItem(Long id) {
        return ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
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
    public NotaDTO editarNotas(@PathVariable Long alunoId, @RequestBody EditarNotasRequest request,
                               @AuthenticationPrincipal UsuarioAutenticado operador) {
        validarNota("p1", request.p1());
        validarNota("p2", request.p2());
        validarNota("t1", request.t1());
        validarNota("participacao", request.participacao());

        var aluno = alunos.findById(alunoId)
                .orElseThrow(() -> ApiException.notFound("Aluno não encontrado."));
        exigirLeciona(aluno.getTurma(), operador);

        String disciplina = request.disciplina() != null ? request.disciplina() : "Geral";
        String periodo = request.periodo() != null ? request.periodo() : "2026-1";

        Nota nota = notas.findByAlunoIdAndDisciplinaAndPeriodo(alunoId, disciplina, periodo)
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

    public record NovaObservacaoRequest(@NotBlank(message = "Escreva a observação.") String texto) {}

    public record ObservacaoDTO(Long id, Long alunoId, String autor, String texto, Instant criadaEm) {
        static ObservacaoDTO of(ObservacaoPedagogica o) {
            return new ObservacaoDTO(o.getId(), o.getAluno().getId(), o.getAutorNome(), o.getTexto(), o.getCriadaEm());
        }
    }

    @GetMapping("/{alunoId}/observacoes")
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public List<ObservacaoDTO> listarObservacoes(@PathVariable Long alunoId,
                                                 @AuthenticationPrincipal UsuarioAutenticado operador) {
        var aluno = alunos.findById(alunoId)
                .orElseThrow(() -> ApiException.notFound("Aluno não encontrado."));
        exigirLeciona(aluno.getTurma(), operador);
        return observacoes.findByAlunoIdOrderByCriadaEmDesc(alunoId).stream().map(ObservacaoDTO::of).toList();
    }

    @GetMapping("/{alunoId}/observacoes/{id}")
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public ObservacaoDTO detalharObservacao(@PathVariable Long alunoId, @PathVariable Long id,
                                            @AuthenticationPrincipal UsuarioAutenticado operador) {
        var aluno = alunos.findById(alunoId)
                .orElseThrow(() -> ApiException.notFound("Aluno não encontrado."));
        exigirLeciona(aluno.getTurma(), operador);
        ObservacaoPedagogica obs = observacoes.findById(id)
                .orElseThrow(() -> ApiException.notFound("Observação não encontrada."));
        // A observação precisa ser mesmo deste aluno: sem isto o id na URI viraria
        // um atalho para ler a observação de um aluno de outra turma.
        if (!obs.getAluno().getId().equals(alunoId)) {
            throw ApiException.notFound("Observação não encontrada.");
        }
        return ObservacaoDTO.of(obs);
    }

    @PostMapping("/{alunoId}/observacoes")
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public ResponseEntity<ObservacaoDTO> criarObservacao(@PathVariable Long alunoId,
                                                         @Valid @RequestBody NovaObservacaoRequest request,
                                                         @AuthenticationPrincipal UsuarioAutenticado autor) {
        var aluno = alunos.findById(alunoId)
                .orElseThrow(() -> ApiException.notFound("Aluno não encontrado."));
        exigirLeciona(aluno.getTurma(), autor);
        ObservacaoPedagogica obs = new ObservacaoPedagogica();
        obs.setAluno(aluno);
        obs.setAutorNome(autor.nome());
        obs.setTexto(request.texto());
        ObservacaoDTO dto = ObservacaoDTO.of(observacoes.save(obs));
        return ResponseEntity.created(uriDoItem(dto.id())).body(dto);
    }
}
