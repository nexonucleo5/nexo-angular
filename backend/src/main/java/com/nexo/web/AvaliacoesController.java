package com.nexo.web;

import com.nexo.api.ApiException;
import com.nexo.domain.Avaliacao;
import com.nexo.domain.Professor;
import com.nexo.domain.Questao;
import com.nexo.domain.Turma;
import com.nexo.repository.AvaliacaoRepository;
import com.nexo.repository.ProfessorRepository;
import com.nexo.repository.QuestaoRepository;
import com.nexo.repository.TurmaRepository;
import com.nexo.security.UsuarioAutenticado;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
@org.springframework.transaction.annotation.Transactional
public class AvaliacoesController {

    private final AvaliacaoRepository avaliacoes;
    private final QuestaoRepository questoes;
    private final TurmaRepository turmas;
    private final ProfessorRepository professores;

    public AvaliacoesController(AvaliacaoRepository avaliacoes, QuestaoRepository questoes, TurmaRepository turmas,
                                ProfessorRepository professores) {
        this.avaliacoes = avaliacoes;
        this.questoes = questoes;
        this.turmas = turmas;
        this.professores = professores;
    }

    /** PROFESSOR só cria avaliação para a turma que leciona; DIRETOR tem acesso irrestrito. */
    private void exigirLeciona(Turma turma, UsuarioAutenticado operador) {
        if (!"PROFESSOR".equals(operador.role())) return;
        Professor professor = professores.findByUsuarioId(operador.id()).orElse(null);
        boolean leciona = professor != null
                && turma.getProfessor() != null && turma.getProfessor().getId().equals(professor.getId());
        if (!leciona) {
            throw ApiException.forbidden("Você não leciona esta turma.");
        }
    }

    public record AvaliacaoDTO(Long id, String titulo, String disciplina, String turma, String tipo,
                               String status, LocalDate data, int entregas, int pendentesCorrecao) {
        static AvaliacaoDTO of(Avaliacao a) {
            return new AvaliacaoDTO(a.getId(), a.getTitulo(), a.getDisciplina(),
                    a.getTurma() != null ? a.getTurma().getNome() : null,
                    a.getTipo(), a.getStatus().name(), a.getData(), a.getEntregas(), a.getPendentesCorrecao());
        }
    }

    /**
     * Ids das turmas que o operador pode enxergar, ou {@code null} quando não há
     * restrição (DIRETOR). Mesma regra de {@link #exigirLeciona}, aplicada a listagens.
     */
    private List<Long> turmasVisiveis(UsuarioAutenticado operador) {
        if (!"PROFESSOR".equals(operador.role())) return null;
        Professor professor = professores.findByUsuarioId(operador.id()).orElse(null);
        if (professor == null) return List.of();
        return turmas.findByProfessorIdOrderByNome(professor.getId()).stream().map(Turma::getId).toList();
    }

    @GetMapping("/api/avaliacoes")
    public List<AvaliacaoDTO> listar(@RequestParam(required = false) Long turma,
                                     @RequestParam(required = false) Avaliacao.Status status,
                                     @AuthenticationPrincipal UsuarioAutenticado operador) {
        // A criação já exigia lecionar a turma, mas a listagem não: qualquer professor
        // via as avaliações de todas as turmas da escola, inclusive as dos colegas.
        List<Long> minhas = turmasVisiveis(operador);
        if (minhas == null) {
            return avaliacoes.buscar(turma, status).stream().map(AvaliacaoDTO::of).toList();
        }
        if (turma != null) {
            if (!minhas.contains(turma)) {
                throw ApiException.forbidden("Você não leciona esta turma.");
            }
            return avaliacoes.buscarPorTurmas(List.of(turma), status).stream().map(AvaliacaoDTO::of).toList();
        }
        return avaliacoes.buscarPorTurmas(minhas, status).stream().map(AvaliacaoDTO::of).toList();
    }

    public record NovaAvaliacaoRequest(String titulo, String disciplina, Long turmaId,
                                       String tipo, LocalDate data) {}

    @PostMapping("/api/avaliacoes")
    @ResponseStatus(HttpStatus.CREATED)
    public AvaliacaoDTO criar(@RequestBody NovaAvaliacaoRequest request,
                              @AuthenticationPrincipal UsuarioAutenticado operador) {
        if (request.titulo() == null || request.titulo().isBlank()) {
            throw ApiException.validation("Dados inválidos.", Map.of("titulo", "Informe o título da avaliação."));
        }
        Avaliacao avaliacao = new Avaliacao();
        avaliacao.setTitulo(request.titulo().trim());
        avaliacao.setDisciplina(request.disciplina());
        avaliacao.setTipo(request.tipo());
        avaliacao.setData(request.data());
        avaliacao.setStatus(Avaliacao.Status.PUBLICADA);
        if (request.turmaId() != null) {
            Turma turma = turmas.findById(request.turmaId())
                    .orElseThrow(() -> ApiException.badRequest("Turma inexistente."));
            exigirLeciona(turma, operador);
            avaliacao.setTurma(turma);
        }
        return AvaliacaoDTO.of(avaliacoes.save(avaliacao));
    }

    @GetMapping("/api/avaliacoes/fila-correcao")
    public List<AvaliacaoDTO> filaCorrecao(@AuthenticationPrincipal UsuarioAutenticado operador) {
        List<Avaliacao.Status> pendentes = List.of(Avaliacao.Status.EM_CORRECAO, Avaliacao.Status.PUBLICADA);
        List<Long> minhas = turmasVisiveis(operador);
        List<Avaliacao> lista = minhas == null
                ? avaliacoes.findByStatusInOrderByDataAsc(pendentes)
                : avaliacoes.findByStatusInAndTurmas(pendentes, minhas);
        return lista.stream()
                .filter(a -> a.getPendentesCorrecao() > 0)
                .map(AvaliacaoDTO::of)
                .toList();
    }

    // ── Banco de questões ────────────────────────────────────────────────────

    public record QuestaoDTO(Long id, String enunciado, String disciplina, String tipo,
                             String dificuldade, Instant criadaEm) {
        static QuestaoDTO of(Questao q) {
            return new QuestaoDTO(q.getId(), q.getEnunciado(), q.getDisciplina(),
                    q.getTipo().name(), q.getDificuldade().name(), q.getCriadaEm());
        }
    }

    @GetMapping("/api/questoes")
    public List<QuestaoDTO> listarQuestoes() {
        return questoes.findAll().stream().map(QuestaoDTO::of).toList();
    }

    public record NovaQuestaoRequest(String enunciado, String disciplina,
                                     Questao.Tipo tipo, Questao.Dificuldade dificuldade) {}

    @PostMapping("/api/questoes")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestaoDTO criarQuestao(@RequestBody NovaQuestaoRequest request) {
        if (request.enunciado() == null || request.enunciado().isBlank()) {
            throw ApiException.validation("Dados inválidos.", Map.of("enunciado", "Informe o enunciado."));
        }
        Questao questao = new Questao();
        questao.setEnunciado(request.enunciado().trim());
        questao.setDisciplina(request.disciplina());
        if (request.tipo() != null) questao.setTipo(request.tipo());
        if (request.dificuldade() != null) questao.setDificuldade(request.dificuldade());
        return QuestaoDTO.of(questoes.save(questao));
    }

    @PutMapping("/api/questoes/{id}")
    public QuestaoDTO atualizarQuestao(@PathVariable Long id, @RequestBody NovaQuestaoRequest request) {
        Questao q = questoes.findById(id)
                .orElseThrow(() -> ApiException.notFound("Questão não encontrada."));
        if (request.enunciado() != null && !request.enunciado().isBlank()) q.setEnunciado(request.enunciado().trim());
        if (request.disciplina() != null) q.setDisciplina(request.disciplina());
        if (request.tipo() != null) q.setTipo(request.tipo());
        if (request.dificuldade() != null) q.setDificuldade(request.dificuldade());
        return QuestaoDTO.of(questoes.save(q));
    }

    @DeleteMapping("/api/questoes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirQuestao(@PathVariable Long id) {
        if (!questoes.existsById(id)) {
            throw ApiException.notFound("Questão não encontrada.");
        }
        questoes.deleteById(id);
    }
}
