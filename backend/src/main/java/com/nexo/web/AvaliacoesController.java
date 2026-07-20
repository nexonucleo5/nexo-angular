package com.nexo.web;

import com.nexo.api.ApiException;
import com.nexo.domain.Avaliacao;
import com.nexo.domain.Questao;
import com.nexo.repository.AvaliacaoRepository;
import com.nexo.repository.QuestaoRepository;
import com.nexo.repository.TurmaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

    public AvaliacoesController(AvaliacaoRepository avaliacoes, QuestaoRepository questoes, TurmaRepository turmas) {
        this.avaliacoes = avaliacoes;
        this.questoes = questoes;
        this.turmas = turmas;
    }

    public record AvaliacaoDTO(Long id, String titulo, String disciplina, String turma, String tipo,
                               String status, LocalDate data, int entregas, int pendentesCorrecao) {
        static AvaliacaoDTO of(Avaliacao a) {
            return new AvaliacaoDTO(a.getId(), a.getTitulo(), a.getDisciplina(),
                    a.getTurma() != null ? a.getTurma().getNome() : null,
                    a.getTipo(), a.getStatus().name(), a.getData(), a.getEntregas(), a.getPendentesCorrecao());
        }
    }

    @GetMapping("/api/avaliacoes")
    public List<AvaliacaoDTO> listar(@RequestParam(required = false) Long turma,
                                     @RequestParam(required = false) Avaliacao.Status status) {
        return avaliacoes.buscar(turma, status).stream().map(AvaliacaoDTO::of).toList();
    }

    public record NovaAvaliacaoRequest(String titulo, String disciplina, Long turmaId,
                                       String tipo, LocalDate data) {}

    @PostMapping("/api/avaliacoes")
    @ResponseStatus(HttpStatus.CREATED)
    public AvaliacaoDTO criar(@RequestBody NovaAvaliacaoRequest request) {
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
            avaliacao.setTurma(turmas.findById(request.turmaId())
                    .orElseThrow(() -> ApiException.badRequest("Turma inexistente.")));
        }
        return AvaliacaoDTO.of(avaliacoes.save(avaliacao));
    }

    @GetMapping("/api/avaliacoes/fila-correcao")
    public List<AvaliacaoDTO> filaCorrecao() {
        return avaliacoes.findByStatusInOrderByDataAsc(
                        List.of(Avaliacao.Status.EM_CORRECAO, Avaliacao.Status.PUBLICADA)).stream()
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
