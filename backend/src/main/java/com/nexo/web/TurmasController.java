package com.nexo.web;

import com.nexo.api.ApiException;
import com.nexo.domain.*;
import com.nexo.repository.*;
import com.nexo.security.UsuarioAutenticado;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/turmas")
@org.springframework.transaction.annotation.Transactional
public class TurmasController {

    private final TurmaRepository turmas;
    private final AlunoRepository alunos;
    private final FrequenciaRepository frequencias;
    private final ConteudoAulaRepository conteudos;
    private final NotaRepository notas;
    private final ProfessorRepository professores;

    public TurmasController(TurmaRepository turmas, AlunoRepository alunos, FrequenciaRepository frequencias,
                            ConteudoAulaRepository conteudos, NotaRepository notas, ProfessorRepository professores) {
        this.turmas = turmas;
        this.alunos = alunos;
        this.frequencias = frequencias;
        this.conteudos = conteudos;
        this.notas = notas;
        this.professores = professores;
    }

    public record TurmaDTO(Long id, String nome, int anoLetivo, String turno) {
        static TurmaDTO of(Turma t) { return new TurmaDTO(t.getId(), t.getNome(), t.getAnoLetivo(), t.getTurno()); }
    }

    /** 6º-9º fundamental antes do ensino médio — ordem pedagógica, não alfabética. */
    private static final List<String> ORDEM_ANOS = List.of(
            "6º Ano", "7º Ano", "8º Ano", "9º Ano", "1º Ano EM", "2º Ano EM", "3º Ano EM");

    @GetMapping
    public List<TurmaDTO> listar() {
        return turmas.findAll().stream()
                .sorted(Comparator.comparingInt(this::ordemDoAno).thenComparing(Turma::getNome))
                .map(TurmaDTO::of)
                .toList();
    }

    private int ordemDoAno(Turma turma) {
        for (int i = 0; i < ORDEM_ANOS.size(); i++) {
            if (turma.getNome().startsWith(ORDEM_ANOS.get(i))) return i;
        }
        return ORDEM_ANOS.size();
    }

    // ── Frequência (Diário de Classe) ────────────────────────────────────────

    public record PresencaAluno(Long alunoId, String nome, Boolean presente) {}

    @GetMapping("/{turmaId}/frequencia")
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public List<PresencaAluno> frequenciaDoDia(@PathVariable Long turmaId,
                                               @RequestParam(required = false) LocalDate data,
                                               @AuthenticationPrincipal UsuarioAutenticado operador) {
        LocalDate dia = data != null ? data : LocalDate.now();
        exigirLeciona(exigirTurma(turmaId), operador);

        Map<Long, Boolean> registradas = frequencias.findByTurmaIdAndData(turmaId, dia).stream()
                .collect(Collectors.toMap(f -> f.getAluno().getId(), Frequencia::isPresente));

        return alunos.findByTurmaIdOrderByNome(turmaId).stream()
                .map(a -> new PresencaAluno(a.getId(), a.getNome(), registradas.get(a.getId())))
                .toList();
    }

    public record PresencaRequest(Long alunoId, boolean presente) {}

    public record SalvarFrequenciaRequest(LocalDate data, List<PresencaRequest> presencas) {}

    public record ResumoFrequencia(int total, long presentes, long ausentes, double percentualPresenca) {}

    @PostMapping("/{turmaId}/frequencia")
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public ResumoFrequencia salvarFrequencia(@PathVariable Long turmaId,
                                             @RequestBody SalvarFrequenciaRequest request,
                                             @AuthenticationPrincipal UsuarioAutenticado operador) {
        Turma turma = exigirTurma(turmaId);
        exigirLeciona(turma, operador);
        if (request.presencas() == null || request.presencas().isEmpty()) {
            throw ApiException.badRequest("Envie a lista de presenças.");
        }
        LocalDate dia = request.data() != null ? request.data() : LocalDate.now();

        for (PresencaRequest p : request.presencas()) {
            Aluno aluno = alunos.findById(p.alunoId())
                    .orElseThrow(() -> ApiException.badRequest("Aluno inexistente: " + p.alunoId()));
            Frequencia freq = frequencias.findByAlunoIdAndTurmaIdAndData(p.alunoId(), turmaId, dia)
                    .orElseGet(() -> {
                        Frequencia nova = new Frequencia();
                        nova.setAluno(aluno);
                        nova.setTurma(turma);
                        nova.setData(dia);
                        return nova;
                    });
            freq.setPresente(p.presente());
            frequencias.save(freq);
        }

        List<Frequencia> doDia = frequencias.findByTurmaIdAndData(turmaId, dia);
        long presentes = doDia.stream().filter(Frequencia::isPresente).count();
        int total = doDia.size();
        double percentual = total == 0 ? 0 : Math.round(presentes * 1000.0 / total) / 10.0;
        return new ResumoFrequencia(total, presentes, total - presentes, percentual);
    }

    // ── Conteúdos ministrados ────────────────────────────────────────────────

    public record NovoConteudoRequest(String titulo, String descricao, String observacoes, LocalDate data) {}

    public record ConteudoDTO(Long id, LocalDate data, String titulo, String descricao,
                              String observacoes, String professor) {
        static ConteudoDTO of(ConteudoAula c) {
            return new ConteudoDTO(c.getId(), c.getData(), c.getTitulo(), c.getDescricao(), c.getObservacoes(),
                    c.getProfessor() != null ? c.getProfessor().getNome() : null);
        }
    }

    @GetMapping("/{turmaId}/conteudos")
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public List<ConteudoDTO> historicoConteudos(@PathVariable Long turmaId,
                                                @AuthenticationPrincipal UsuarioAutenticado operador) {
        exigirLeciona(exigirTurma(turmaId), operador);
        return conteudos.findByTurmaIdOrderByDataDesc(turmaId).stream().map(ConteudoDTO::of).toList();
    }

    @PostMapping("/{turmaId}/conteudos")
    @PreAuthorize("hasRole('PROFESSOR')")
    public ConteudoDTO registrarConteudo(@PathVariable Long turmaId,
                                         @RequestBody NovoConteudoRequest request,
                                         @AuthenticationPrincipal UsuarioAutenticado usuario) {
        Turma turma = exigirTurma(turmaId);
        exigirLeciona(turma, usuario);
        if (request.titulo() == null || request.titulo().isBlank()) {
            throw ApiException.validation("Dados inválidos.", Map.of("titulo", "Informe o título da aula."));
        }
        ConteudoAula conteudo = new ConteudoAula();
        conteudo.setTurma(turma);
        conteudo.setTitulo(request.titulo().trim());
        conteudo.setDescricao(request.descricao());
        conteudo.setObservacoes(request.observacoes());
        conteudo.setData(request.data() != null ? request.data() : LocalDate.now());
        professores.findByUsuarioId(usuario.id()).ifPresent(conteudo::setProfessor);
        return ConteudoDTO.of(conteudos.save(conteudo));
    }

    // ── Notas da turma ───────────────────────────────────────────────────────

    @GetMapping("/{turmaId}/notas")
    @PreAuthorize("hasAnyRole('PROFESSOR','DIRETOR')")
    public List<AlunosController.NotaDTO> notasDaTurma(@PathVariable Long turmaId,
                                                       @RequestParam(required = false) String disciplina,
                                                       @RequestParam(required = false) String periodo,
                                                       @AuthenticationPrincipal UsuarioAutenticado operador) {
        exigirLeciona(exigirTurma(turmaId), operador);
        return notas.buscarPorTurma(turmaId, emptyToNull(disciplina), emptyToNull(periodo)).stream()
                .map(n -> new AlunosController.NotaDTO(n.getId(), n.getAluno().getId(), n.getAluno().getNome(),
                        n.getDisciplina(), n.getPeriodo(), n.getP1(), n.getP2(), n.getT1(),
                        n.getParticipacao(), n.getMedia()))
                .toList();
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private Turma exigirTurma(Long turmaId) {
        return turmas.findById(turmaId)
                .orElseThrow(() -> ApiException.notFound("Turma não encontrada."));
    }

    /** PROFESSOR só acessa a turma que leciona (Turma.professor); DIRETOR tem acesso irrestrito. */
    private void exigirLeciona(Turma turma, UsuarioAutenticado operador) {
        if (!"PROFESSOR".equals(operador.role())) return;
        Professor professor = professores.findByUsuarioId(operador.id()).orElse(null);
        boolean leciona = professor != null
                && turma.getProfessor() != null && turma.getProfessor().getId().equals(professor.getId());
        if (!leciona) {
            throw ApiException.forbidden("Você não leciona esta turma.");
        }
    }
}
