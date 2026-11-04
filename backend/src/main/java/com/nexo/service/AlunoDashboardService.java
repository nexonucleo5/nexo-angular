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
                                    List<AtividadeDTO> atividades, List<RankingItemDTO> ranking,
                                    boolean foraDoRanking) {}

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

        // Projeção ordenada pelo banco: traz poucas colunas por aluno em vez da entidade
        // inteira gerenciada, e dispensa o sort em memória. Já vem sem quem desligou
        // "Exibir no ranking" (ver AlunoRepository.rankingPorXp).
        List<AlunoRepository.RankingXp> ranking = alunos.rankingPorXp();

        // Quem se tirou do ranking não tem posição nele: 0 + o sinal explícito, que a tela
        // usa para explicar em vez de mostrar "#0".
        boolean foraDoRanking = !aluno.isExibirNoRanking();
        int posicao = 0;
        if (!foraDoRanking) {
            posicao = 1;
            for (int i = 0; i < ranking.size(); i++) {
                if (ranking.get(i).getId().equals(aluno.getId())) {
                    posicao = i + 1;
                    break;
                }
            }
        }

        int topN = Math.min(5, ranking.size());
        List<RankingItemDTO> topRanking = new ArrayList<>(topN + 1);
        for (int i = 0; i < topN; i++) {
            AlunoRepository.RankingXp a = ranking.get(i);
            boolean sou = a.getId().equals(aluno.getId());
            topRanking.add(new RankingItemDTO(i + 1, a.getNome(), a.getXpTotal(), fotoVisivel(a, sou), sou));
        }
        // Garante que o aluno logado apareça mesmo fora do top 5 — a menos que tenha
        // escolhido não aparecer.
        if (!foraDoRanking && topRanking.stream().noneMatch(RankingItemDTO::isMe)) {
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
                aluno.getXpTotal(), nivelDe(aluno.getXpTotal()), feed, topRanking, foraDoRanking);
    }

    /**
     * A foto de um colega só sai se ele deixou o perfil público; a própria foto, sempre.
     * Sem a foto a tela cai no avatar padrão — o nome continua, porque quem está no
     * ranking aceitou aparecer nele.
     */
    private static String fotoVisivel(AlunoRepository.RankingXp a, boolean souEu) {
        return souEu || a.isPerfilPublico() ? a.getFoto() : null;
    }
}
