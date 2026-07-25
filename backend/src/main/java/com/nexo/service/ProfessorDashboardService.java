package com.nexo.service;

import com.nexo.api.ApiException;
import com.nexo.domain.Aluno;
import com.nexo.domain.Professor;
import com.nexo.domain.Turma;
import com.nexo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Monta o dashboard do professor (turmas, próximas aulas, alunos em atenção,
 * feed de atividades e KPIs) — substitui os arrays hardcoded de dashboard-professor.ts.
 */
@Service
public class ProfessorDashboardService {

    public record TurmaResumo(String nome, String disciplina, int alunos, double mediaGeral, int progresso) {}

    public record ProximaAula(String turma, String disciplina, String hora, LocalDate data, String sala, int alunos) {}

    public record AlunoAtencaoDTO(String nome, String turma, double mediaAtual, int frequencia,
                                  String status, String foto, Instant ultimaAtividade) {}

    public record AtividadeDTO(String tipo, String descricao, String turma, String icone, String cor, Instant criadaEm) {}

    public record ProfessorDashboardDTO(int turmasAtivas, int totalAlunos, int correcoesPendentes, int avaliacoesMes,
                                        List<TurmaResumo> turmas, List<ProximaAula> proximasAulas,
                                        List<AlunoAtencaoDTO> alunosAtencao, List<AtividadeDTO> atividades) {}

    private final ProfessorRepository professores;
    private final TurmaRepository turmas;
    private final AlunoRepository alunos;
    private final AvaliacaoRepository avaliacoes;
    private final AulaAgendadaRepository aulas;
    private final AtividadeProfessorRepository atividadesProf;
    private final EvasaoService evasao;
    private final AgregadosAcademicos agregados;

    public ProfessorDashboardService(ProfessorRepository professores, TurmaRepository turmas, AlunoRepository alunos,
                                     AvaliacaoRepository avaliacoes, AulaAgendadaRepository aulas,
                                     AtividadeProfessorRepository atividadesProf, EvasaoService evasao,
                                     AgregadosAcademicos agregados) {
        this.professores = professores;
        this.turmas = turmas;
        this.alunos = alunos;
        this.avaliacoes = avaliacoes;
        this.aulas = aulas;
        this.atividadesProf = atividadesProf;
        this.evasao = evasao;
        this.agregados = agregados;
    }

    @Transactional(readOnly = true)
    public ProfessorDashboardDTO montar(Long usuarioId) {
        Professor prof = professores.findByUsuarioId(usuarioId)
                .orElseThrow(() -> ApiException.notFound("Professor não encontrado para o usuário logado."));

        List<Turma> minhasTurmas = turmas.findByProfessorIdOrderByNome(prof.getId());
        LocalDate hoje = LocalDate.now();

        // Alunos de todas as turmas e agregados de nota/frequência carregados de uma vez —
        // antes eram uma query por turma mais seis por aluno (evasao.avaliar era chamado duas vezes).
        var indices = agregados.carregar(null);
        Map<Long, List<Aluno>> alunosPorTurma = minhasTurmas.isEmpty() ? Map.of()
                : alunos.findByTurmaIdInComTurma(minhasTurmas.stream().map(Turma::getId).toList()).stream()
                        .collect(Collectors.groupingBy(a -> a.getTurma().getId()));

        List<TurmaResumo> turmasResumo = new ArrayList<>();
        List<AlunoAtencaoDTO> atencao = new ArrayList<>();
        int totalAlunos = 0;
        int correcoesPendentes = 0;
        int avaliacoesMes = 0;

        for (Turma t : minhasTurmas) {
            List<Aluno> alunosTurma = alunosPorTurma.getOrDefault(t.getId(), List.of());
            totalAlunos += alunosTurma.size();

            double media = alunosTurma.stream()
                    .map(a -> evasao.avaliar(a, indices).media())
                    .filter(m -> m > 0)
                    .mapToDouble(Double::doubleValue)
                    .average().orElse(0);
            media = Math.round(media * 10.0) / 10.0;
            turmasResumo.add(new TurmaResumo(t.getNome(), prof.getDisciplina(),
                    alunosTurma.size(), media, (int) Math.round(media * 10)));

            for (var av : avaliacoes.buscar(t.getId(), null)) {
                correcoesPendentes += av.getPendentesCorrecao();
                if (av.getData() != null && av.getData().getYear() == hoje.getYear()
                        && av.getData().getMonthValue() == hoje.getMonthValue()) {
                    avaliacoesMes++;
                }
            }

            for (Aluno a : alunosTurma) {
                var r = evasao.avaliar(a, indices);
                if (r.risco() != EvasaoService.Risco.BAIXO) {
                    atencao.add(new AlunoAtencaoDTO(a.getNome(), t.getNome(), r.media(),
                            Math.max(0, (int) Math.round(100 - r.percentualFaltas())),
                            r.risco() == EvasaoService.Risco.ALTO ? "critico" : "atencao",
                            a.getFoto(), a.getUltimoAcessoEm()));
                }
            }
        }

        atencao.sort(Comparator.comparingDouble(AlunoAtencaoDTO::mediaAtual));
        List<AlunoAtencaoDTO> topAtencao = atencao.size() > 5 ? new ArrayList<>(atencao.subList(0, 5)) : atencao;

        List<ProximaAula> proximas = aulas.findTop6ByProfessorIdOrderByDataAscHoraAsc(prof.getId()).stream()
                .map(al -> new ProximaAula(al.getTurma() != null ? al.getTurma().getNome() : "—",
                        al.getDisciplina(), al.getHora(), al.getData(), al.getSala(), al.getQtdAlunos()))
                .toList();

        List<AtividadeDTO> feed = atividadesProf.findTop6ByProfessorIdOrderByCriadaEmDesc(prof.getId()).stream()
                .map(at -> new AtividadeDTO(at.getTipo(), at.getDescricao(), at.getTurma(),
                        at.getIcone(), at.getCor(), at.getCriadaEm()))
                .toList();

        return new ProfessorDashboardDTO(minhasTurmas.size(), totalAlunos, correcoesPendentes, avaliacoesMes,
                turmasResumo, proximas, topAtencao, feed);
    }
}
