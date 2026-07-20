package com.nexo.service;

import com.nexo.api.ApiException;
import com.nexo.domain.Aluno;
import com.nexo.domain.Desafio;
import com.nexo.domain.DesafioAluno;
import com.nexo.repository.AlunoRepository;
import com.nexo.repository.DesafioAlunoRepository;
import com.nexo.repository.DesafioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                             int tempoMin, String status, int progresso) {}

    public record StatsDTO(int concluidos, int total, int taxaSucesso, int sequenciaDias) {}

    public record DesafiosResponse(StatsDTO stats, List<DesafioDTO> desafios) {}

    private final DesafioRepository desafios;
    private final DesafioAlunoRepository progresso;
    private final AlunoRepository alunos;

    public DesafioService(DesafioRepository desafios, DesafioAlunoRepository progresso, AlunoRepository alunos) {
        this.desafios = desafios;
        this.progresso = progresso;
        this.alunos = alunos;
    }

    @Transactional(readOnly = true)
    public DesafiosResponse listar(Long usuarioId) {
        Aluno aluno = aluno(usuarioId);
        Map<Long, DesafioAluno> porDesafio = new HashMap<>();
        for (DesafioAluno da : progresso.findByAlunoId(aluno.getId())) {
            porDesafio.put(da.getDesafio().getId(), da);
        }

        List<DesafioDTO> lista = desafios.findAll().stream().map(d -> {
            DesafioAluno da = porDesafio.get(d.getId());
            String status = da != null ? da.getStatus() : "ABERTO";
            int prog = da != null ? da.getProgresso() : 0;
            return new DesafioDTO(d.getId(), d.getTitulo(), d.getMateria(), d.getNivel(),
                    d.getXp(), d.getTempoMin(), status, prog);
        }).toList();

        int concluidos = (int) lista.stream().filter(x -> "CONCLUIDO".equals(x.status())).count();
        int iniciados = (int) lista.stream().filter(x -> !"ABERTO".equals(x.status())).count();
        int taxa = iniciados == 0 ? 0 : Math.round(concluidos * 100f / iniciados);
        return new DesafiosResponse(new StatsDTO(concluidos, lista.size(), taxa, aluno.getOfensivaDias()), lista);
    }

    @Transactional
    public DesafioDTO iniciar(Long usuarioId, Long desafioId) {
        Aluno aluno = aluno(usuarioId);
        Desafio d = desafios.findById(desafioId)
                .orElseThrow(() -> ApiException.notFound("Desafio não encontrado."));
        DesafioAluno da = progresso.findByAlunoIdAndDesafioId(aluno.getId(), desafioId)
                .orElseGet(() -> new DesafioAluno(aluno, d, "ABERTO", 0));
        if (!"CONCLUIDO".equals(da.getStatus())) {
            da.setStatus("PROGRESSO");
            da.setProgresso(Math.max(da.getProgresso(), 10));
            progresso.save(da);
        }
        return new DesafioDTO(d.getId(), d.getTitulo(), d.getMateria(), d.getNivel(), d.getXp(),
                d.getTempoMin(), da.getStatus(), da.getProgresso());
    }

    @Transactional
    public DesafioDTO concluir(Long usuarioId, Long desafioId) {
        Aluno aluno = aluno(usuarioId);
        Desafio d = desafios.findById(desafioId)
                .orElseThrow(() -> ApiException.notFound("Desafio não encontrado."));
        DesafioAluno da = progresso.findByAlunoIdAndDesafioId(aluno.getId(), desafioId)
                .orElseGet(() -> new DesafioAluno(aluno, d, "ABERTO", 0));
        boolean jaConcluido = "CONCLUIDO".equals(da.getStatus());
        da.setStatus("CONCLUIDO");
        da.setProgresso(100);
        progresso.save(da);
        if (!jaConcluido) {
            // credita XP ao aluno
            aluno.setXpTotal(aluno.getXpTotal() + d.getXp());
            aluno.setXpSemana(aluno.getXpSemana() + d.getXp());
            alunos.save(aluno);
        }
        return new DesafioDTO(d.getId(), d.getTitulo(), d.getMateria(), d.getNivel(), d.getXp(),
                d.getTempoMin(), da.getStatus(), da.getProgresso());
    }

    private Aluno aluno(Long usuarioId) {
        return alunos.findByUsuarioId(usuarioId)
                .orElseThrow(() -> ApiException.notFound("Aluno não encontrado para o usuário logado."));
    }
}
