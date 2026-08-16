package com.nexo.service;

import com.nexo.api.ApiException;
import com.nexo.domain.Aluno;
import com.nexo.domain.Desafio;
import com.nexo.domain.DesafioAluno;
import com.nexo.domain.QuizPergunta;
import com.nexo.repository.AlunoRepository;
import com.nexo.repository.DesafioAlunoRepository;
import com.nexo.repository.DesafioRepository;
import com.nexo.repository.QuizPerguntaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Desafios do aluno (gamificação) — catálogo + progresso individual,
 * substitui o array hardcoded de desafios.ts. Concluir um desafio credita XP.
 */
@Service
public class DesafioService {

    public record DesafioDTO(Long id, String titulo, String materia, String nivel, int xp,
                             int tempoMin, String status, int progresso,
                             Integer acertos, Integer totalPerguntas, int tentativas) {}

    public record StatsDTO(int concluidos, int total, int taxaSucesso, int sequenciaDias) {}

    public record DesafiosResponse(StatsDTO stats, List<DesafioDTO> desafios) {}

    /** Alternativa correta só vem preenchida quando o aluno já concluiu o desafio. */
    public record QuizPerguntaDTO(Long id, String enunciado, List<String> alternativas, Integer respostaCorreta) {}

    public record RespostaItem(Long perguntaId, Integer alternativaEscolhida) {}

    public record FinalizarQuizRequest(List<RespostaItem> respostas) {}

    /**
     * Resultado de uma tentativa. Só é aprovado quem acerta 100% — e só nesse caso
     * o placar vem preenchido. Tentativa reprovada devolve {@code acertos = null}
     * de propósito: o aluno não descobre quantas (nem quais) errou, apenas refaz.
     */
    public record QuizResultadoDTO(boolean aprovado, Integer acertos, int totalPerguntas,
                                   int xpGanho, String status, int tentativas) {}

    private final DesafioRepository desafios;
    private final DesafioAlunoRepository progresso;
    private final AlunoRepository alunos;
    private final QuizPerguntaRepository perguntas;

    public DesafioService(DesafioRepository desafios, DesafioAlunoRepository progresso, AlunoRepository alunos,
                          QuizPerguntaRepository perguntas) {
        this.desafios = desafios;
        this.progresso = progresso;
        this.alunos = alunos;
        this.perguntas = perguntas;
    }

    @Transactional(readOnly = true)
    public DesafiosResponse listar(Long usuarioId) {
        Aluno aluno = aluno(usuarioId);
        Map<Long, DesafioAluno> porDesafio = progressoDoAluno(aluno.getId());

        List<DesafioDTO> lista = desafios.findAll().stream()
                .map(d -> paraDTO(d, porDesafio.get(d.getId())))
                .toList();

        int concluidos = (int) lista.stream().filter(x -> "CONCLUIDO".equals(x.status())).count();
        int iniciados = (int) lista.stream().filter(x -> !"ABERTO".equals(x.status())).count();
        int taxa = iniciados == 0 ? 0 : Math.round(concluidos * 100f / iniciados);
        return new DesafiosResponse(new StatsDTO(concluidos, lista.size(), taxa, aluno.getOfensivaDias()), lista);
    }

    @Transactional(readOnly = true)
    public DesafioDTO obter(Long usuarioId, Long desafioId) {
        Aluno aluno = aluno(usuarioId);
        Desafio d = desafio(desafioId);
        DesafioAluno da = progresso.findByAlunoIdAndDesafioId(aluno.getId(), desafioId).orElse(null);
        return paraDTO(d, da);
    }

    @Transactional
    public DesafioDTO iniciar(Long usuarioId, Long desafioId) {
        Aluno aluno = aluno(usuarioId);
        Desafio d = desafio(desafioId);
        DesafioAluno da = progressoOuNovo(aluno, d, desafioId);
        if (!"CONCLUIDO".equals(da.getStatus())) {
            da.setStatus("PROGRESSO");
            da.setProgresso(Math.max(da.getProgresso(), 10));
            progresso.save(da);
        }
        return paraDTO(d, da);
    }

    @Transactional
    public DesafioDTO concluir(Long usuarioId, Long desafioId) {
        Aluno aluno = aluno(usuarioId);
        Desafio d = desafio(desafioId);
        DesafioAluno da = progressoOuNovo(aluno, d, desafioId);
        boolean jaConcluido = "CONCLUIDO".equals(da.getStatus());
        da.setStatus("CONCLUIDO");
        da.setProgresso(100);
        progresso.save(da);
        if (!jaConcluido) {
            creditarXp(aluno, d.getXp());
        }
        return paraDTO(d, da);
    }

    /**
     * Perguntas do quiz do desafio. Abrir o quiz de um desafio ABERTO já o marca
     * como PROGRESSO (mesmo efeito do antigo botão "Iniciar"). A alternativa correta
     * só é revelada a quem já gabaritou o desafio; como tentativa reprovada nunca
     * marca CONCLUIDO, o aluno que errou continua sem acesso ao gabarito.
     */
    @Transactional
    public List<QuizPerguntaDTO> listarPerguntas(Long usuarioId, Long desafioId) {
        Aluno aluno = aluno(usuarioId);
        Desafio d = desafio(desafioId);
        DesafioAluno da = progressoOuNovo(aluno, d, desafioId);
        if (!"CONCLUIDO".equals(da.getStatus())) {
            da.setStatus("PROGRESSO");
            da.setProgresso(Math.max(da.getProgresso(), 10));
            progresso.save(da);
        }

        boolean concluido = "CONCLUIDO".equals(da.getStatus());
        return perguntas.findByDesafioIdOrderById(desafioId).stream()
                .map(p -> new QuizPerguntaDTO(p.getId(), p.getEnunciado(), new ArrayList<>(p.getAlternativas()),
                        concluido ? p.getRespostaCorreta() : null))
                .toList();
    }

    /**
     * Corrige as respostas de uma tentativa.
     *
     * <p>Regra: o desafio só é concluído (e o XP só é creditado) quando o aluno acerta
     * <b>todas</b> as perguntas. Errou uma que seja, nada é persistido além do contador
     * de tentativas — o status continua PROGRESSO, o que mantém o gabarito escondido em
     * {@link #listarPerguntas} e permite refazer o quiz do zero.
     */
    @Transactional
    public QuizResultadoDTO finalizarQuiz(Long usuarioId, Long desafioId, FinalizarQuizRequest request) {
        Aluno aluno = aluno(usuarioId);
        Desafio d = desafio(desafioId);
        List<QuizPergunta> lista = perguntas.findByDesafioIdOrderById(desafioId);
        if (lista.isEmpty()) {
            throw ApiException.badRequest("Este desafio não tem perguntas de quiz cadastradas.");
        }

        Map<Long, Integer> escolhas = new HashMap<>();
        if (request.respostas() != null) {
            for (RespostaItem r : request.respostas()) {
                escolhas.put(r.perguntaId(), r.alternativaEscolhida());
            }
        }

        int acertos = 0;
        for (QuizPergunta p : lista) {
            Integer escolhida = escolhas.get(p.getId());
            if (escolhida != null && escolhida == p.getRespostaCorreta()) acertos++;
        }
        boolean aprovado = acertos == lista.size();

        DesafioAluno da = progressoOuNovo(aluno, d, desafioId);
        boolean jaConcluido = "CONCLUIDO".equals(da.getStatus());
        da.setTentativas(da.getTentativas() + 1);

        int xpGanho = 0;
        if (aprovado) {
            da.setStatus("CONCLUIDO");
            da.setProgresso(100);
            da.setAcertos(acertos);
            da.setTotalPerguntas(lista.size());
            if (!jaConcluido) {
                xpGanho = d.getXp();
                creditarXp(aluno, xpGanho);
            }
        } else if (!jaConcluido) {
            // Reprovado: segue em PROGRESSO, sem XP e sem placar gravado.
            da.setStatus("PROGRESSO");
            da.setProgresso(Math.max(da.getProgresso(), 50));
        }
        progresso.save(da);

        return new QuizResultadoDTO(aprovado, aprovado ? acertos : null, lista.size(),
                xpGanho, da.getStatus(), da.getTentativas());
    }

    private Map<Long, DesafioAluno> progressoDoAluno(Long alunoId) {
        Map<Long, DesafioAluno> porDesafio = new HashMap<>();
        for (DesafioAluno da : progresso.findByAlunoId(alunoId)) {
            porDesafio.put(da.getDesafio().getId(), da);
        }
        return porDesafio;
    }

    private DesafioAluno progressoOuNovo(Aluno aluno, Desafio d, Long desafioId) {
        return progresso.findByAlunoIdAndDesafioId(aluno.getId(), desafioId)
                .orElseGet(() -> new DesafioAluno(aluno, d, "ABERTO", 0));
    }

    private void creditarXp(Aluno aluno, int xp) {
        aluno.setXpTotal(aluno.getXpTotal() + xp);
        aluno.setXpSemana(aluno.getXpSemana() + xp);
        alunos.save(aluno);
    }

    private static DesafioDTO paraDTO(Desafio d, DesafioAluno da) {
        String status = da != null ? da.getStatus() : "ABERTO";
        int prog = da != null ? da.getProgresso() : 0;
        Integer acertos = da != null ? da.getAcertos() : null;
        Integer total = da != null ? da.getTotalPerguntas() : null;
        int tentativas = da != null ? da.getTentativas() : 0;
        return new DesafioDTO(d.getId(), d.getTitulo(), d.getMateria(), d.getNivel(), d.getXp(),
                d.getTempoMin(), status, prog, acertos, total, tentativas);
    }

    private Aluno aluno(Long usuarioId) {
        return alunos.findByUsuarioId(usuarioId)
                .orElseThrow(() -> ApiException.notFound("Aluno não encontrado para o usuário logado."));
    }

    private Desafio desafio(Long desafioId) {
        return desafios.findById(desafioId)
                .orElseThrow(() -> ApiException.notFound("Desafio não encontrado."));
    }
}
