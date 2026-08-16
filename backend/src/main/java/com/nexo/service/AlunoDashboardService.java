package com.nexo.service;

import com.nexo.api.ApiException;
import com.nexo.domain.Aluno;
import com.nexo.repository.AlunoRepository;
import com.nexo.repository.AtividadeAlunoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Monta o dashboard de gamificação do aluno (XP, ofensiva, ranking, tarefas,
 * atividades recentes) — substitui os arrays hardcoded de dashboards.ts.
 */
@Service
public class AlunoDashboardService {

    public record AtividadeDTO(String titulo, String materia, int xp, int progresso, String icone, Instant criadaEm) {}

    public record RankingItemDTO(int posicao, String nome, int xp, String foto, boolean isMe) {}

    public record AlunoDashboardDTO(String nome, int xpSemana, int metaSemanalXp, int ofensivaDias,
                                    int posicao, int totalAlunos, String turmaNome,
                                    int tarefasFeitasHoje, int tarefasHoje, int xpTotal, int nivel,
                                    List<AtividadeDTO> atividades, List<RankingItemDTO> ranking) {}

    public record NotaAlunoDTO(String disciplina, Double media, Double p1, Double p2, Double t1, Double participacao) {}

    private final AlunoRepository alunos;
    private final AtividadeAlunoRepository atividades;
    private final com.nexo.repository.NotaRepository notas;

    public AlunoDashboardService(AlunoRepository alunos, AtividadeAlunoRepository atividades,
                                 com.nexo.repository.NotaRepository notas) {
        this.alunos = alunos;
        this.atividades = atividades;
        this.notas = notas;
    }

    /** Nível derivado do XP total (1 nível a cada 400 XP). */
    private static int nivelDe(int xpTotal) {
        return 1 + xpTotal / 400;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<NotaAlunoDTO> notasDoAluno(Long usuarioId) {
        Aluno aluno = alunos.findByUsuarioId(usuarioId)
                .orElseThrow(() -> ApiException.notFound("Aluno não encontrado para o usuário logado."));
        return notas.findByAlunoId(aluno.getId()).stream()
                .map(n -> new NotaAlunoDTO(n.getDisciplina(), n.getMedia(), n.getP1(), n.getP2(), n.getT1(), n.getParticipacao()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AlunoDashboardDTO montar(Long usuarioId) {
        Aluno aluno = alunos.findByUsuarioId(usuarioId)
                .orElseThrow(() -> ApiException.notFound("Aluno não encontrado para o usuário logado."));

        // Projeção ordenada pelo banco: traz 4 colunas por aluno em vez da entidade
        // inteira gerenciada, e dispensa o sort em memória.
        List<AlunoRepository.RankingXp> ranking = alunos.rankingPorXp();

        int posicao = 1;
        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).getId().equals(aluno.getId())) {
                posicao = i + 1;
                break;
            }
        }

        int topN = Math.min(5, ranking.size());
        List<RankingItemDTO> topRanking = new ArrayList<>(topN + 1);
        for (int i = 0; i < topN; i++) {
            AlunoRepository.RankingXp a = ranking.get(i);
            topRanking.add(new RankingItemDTO(i + 1, a.getNome(), a.getXpTotal(), a.getFoto(),
                    a.getId().equals(aluno.getId())));
        }
        // Garante que o aluno logado apareça mesmo fora do top 5
        if (topRanking.stream().noneMatch(RankingItemDTO::isMe)) {
            topRanking.add(new RankingItemDTO(posicao, aluno.getNome(), aluno.getXpTotal(), aluno.getFoto(), true));
        }

        List<AtividadeDTO> feed = atividades.findTop6ByAlunoIdOrderByCriadaEmDesc(aluno.getId()).stream()
                .map(a -> new AtividadeDTO(a.getTitulo(), a.getMateria(), a.getXp(), a.getProgresso(),
                        a.getIcone(), a.getCriadaEm()))
                .toList();

        return new AlunoDashboardDTO(aluno.getNome(), aluno.getXpSemana(), aluno.getMetaSemanalXp(),
                aluno.getOfensivaDias(), posicao, ranking.size(),
                aluno.getTurma() != null ? aluno.getTurma().getNome() : null,
                aluno.getTarefasFeitasHoje(), aluno.getTarefasHoje(),
                aluno.getXpTotal(), nivelDe(aluno.getXpTotal()), feed, topRanking);
    }
}
