package com.nexo.web;

import com.nexo.repository.ConteudoAulaRepository;
import com.nexo.repository.ProfessorRepository;
import com.nexo.service.EvasaoService;
import com.nexo.service.RelatorioService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Endpoints exclusivos do Diretor: evasão, relatórios e monitoramento docente. */
@RestController
@PreAuthorize("hasRole('DIRETOR')")
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class GestaoDiretorController {

    private final EvasaoService evasaoService;
    private final RelatorioService relatorioService;
    private final ProfessorRepository professores;
    public GestaoDiretorController(EvasaoService evasaoService, RelatorioService relatorioService,
                                   ProfessorRepository professores,
                                   ConteudoAulaRepository conteudos) {
        this.evasaoService = evasaoService;
        this.relatorioService = relatorioService;
        this.professores = professores;
    }

    // ── Gestão de Evasão ─────────────────────────────────────────────────────

    @GetMapping("/api/evasao/risco")
    public List<EvasaoService.AlunoRiscoDTO> risco(@RequestParam(required = false) EvasaoService.Risco risco,
                                                   @RequestParam(required = false) Long turma,
                                                   @RequestParam(required = false) String busca) {
        return evasaoService.calcularRisco(risco, turma, busca);
    }

    // ── Relatórios ───────────────────────────────────────────────────────────

    @GetMapping("/api/relatorios/desempenho")
    public RelatorioService.Desempenho desempenho(@RequestParam(required = false) String periodo) {
        return relatorioService.desempenho(periodo);
    }

    @GetMapping("/api/relatorios/periodos")
    public List<String> periodos() {
        return relatorioService.periodosDisponiveis();
    }

    @GetMapping("/api/relatorios/desempenho/export")
    public ResponseEntity<byte[]> exportar(@RequestParam String formato,
                                           @RequestParam(required = false) String periodo) {
        byte[] arquivo = relatorioService.exportar(formato, periodo);
        boolean pdf = "pdf".equalsIgnoreCase(formato);
        String nome = "relatorio-desempenho." + (pdf ? "pdf" : "xlsx");
        MediaType tipo = pdf ? MediaType.APPLICATION_PDF
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nome + "\"")
                .contentType(tipo)
                .body(arquivo);
    }

    // ── Monitoramento Docente ────────────────────────────────────────────────

    /**
     * O {@code id} é o do Professor; {@code usuarioId} é o da conta de login, que é
     * por onde o chat identifica o interlocutor. São numerações diferentes (tabelas
     * distintas), então mandar o id do professor para o chat abriria a conversa de
     * outra pessoa. Vem nulo para docente ainda sem conta — nesse caso não há com
     * quem conversar, e a tela desabilita o botão.
     */
    public record ProfessorMonitorDTO(Long id, Long usuarioId, String nome, String disciplina, String foto,
                                      String turmas, int correcoesPendentes, double tempoRespostaDias,
                                      int interacoesSemana, double avaliacao, int tarefasConcluidas,
                                      int tarefasTotal, String status) {}

    /** Classificação de desempenho derivada da avaliação docente. */
    private static String statusDe(double avaliacao) {
        if (avaliacao >= 4.7) return "Excelente";
        if (avaliacao >= 4.3) return "Bom";
        return "Atenção";
    }

    @GetMapping("/api/monitoramento/professores")
    public List<ProfessorMonitorDTO> monitoramento() {
        return professores.findAllComMaterias().stream()
                .sorted((a, b) -> Double.compare(b.getAvaliacao(), a.getAvaliacao()))
                .map(p -> new ProfessorMonitorDTO(p.getId(),
                        p.getUsuario() != null ? p.getUsuario().getId() : null,
                        p.getNome(), p.getDisciplinas(), p.getFoto(),
                        p.getTurmas(), p.getCorrecoesPendentes(), p.getTempoRespostaDias(),
                        p.getInteracoesSemana(), p.getAvaliacao(), p.getTarefasConcluidas(),
                        p.getTarefasTotal(), statusDe(p.getAvaliacao())))
                .toList();
    }
}
