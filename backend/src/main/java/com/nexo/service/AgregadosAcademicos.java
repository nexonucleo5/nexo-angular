package com.nexo.service;

import com.nexo.domain.Nota;
import com.nexo.repository.FrequenciaRepository;
import com.nexo.repository.NotaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carrega médias e frequências de TODOS os alunos em duas queries e entrega o
 * resultado indexado por aluno.
 *
 * <p>Existe para eliminar o N+1 que dominava o tempo de resposta: relatórios, evasão
 * e o dashboard do professor consultavam notas e frequência aluno a aluno, gerando
 * centenas de idas ao banco por request. As médias continuam sendo calculadas em Java
 * por {@link Nota#calcularMedia} — a fórmula é ponderada e arredondada por nota, então
 * agregá-la com {@code avg()} no SQL mudaria os números.
 */
@Service
public class AgregadosAcademicos {

    /** Aulas registradas e faltas de um aluno. */
    public record Presenca(long total, long faltas) {}

    /** Agregados prontos para consulta em memória, sem novas idas ao banco. */
    public record Indices(Map<Long, Double> medias, Map<Long, Presenca> presencas) {

        /** Média do aluno, ou {@code null} se ele não tem nenhuma nota lançada. */
        public Double media(Long alunoId) {
            return medias.get(alunoId);
        }

        /** Média do aluno, com 0.0 para quem não tem nota (regra usada na análise de evasão). */
        public double mediaOuZero(Long alunoId) {
            return medias.getOrDefault(alunoId, 0.0);
        }

        /** Percentual de faltas com uma casa decimal; 0 para aluno sem registros. */
        public double percentualFaltas(Long alunoId) {
            Presenca p = presencas.get(alunoId);
            if (p == null || p.total() == 0) return 0;
            return Math.round(p.faltas() * 1000.0 / p.total()) / 10.0;
        }

        /** Percentual de presença sem arredondar; 0 para aluno sem registros. */
        public double percentualPresenca(Long alunoId) {
            Presenca p = presencas.get(alunoId);
            if (p == null || p.total() == 0) return 0;
            return (p.total() - p.faltas()) * 100.0 / p.total();
        }

        /** Soma de todos os registros de frequência — dispensa um {@code count()} extra. */
        public Presenca totalGeral() {
            long total = 0;
            long faltas = 0;
            for (Presenca p : presencas.values()) {
                total += p.total();
                faltas += p.faltas();
            }
            return new Presenca(total, faltas);
        }
    }

    private final NotaRepository notas;
    private final FrequenciaRepository frequencias;

    public AgregadosAcademicos(NotaRepository notas, FrequenciaRepository frequencias) {
        this.notas = notas;
        this.frequencias = frequencias;
    }

    /**
     * Carrega os agregados em duas queries.
     *
     * @param periodo período letivo a considerar nas notas; {@code null}/vazio usa todas.
     */
    @Transactional(readOnly = true)
    public Indices carregar(String periodo) {
        return new Indices(mediasPorAluno(periodo), presencasPorAluno());
    }

    private Map<Long, Double> mediasPorAluno(String periodo) {
        List<NotaRepository.NotaBruta> brutas = (periodo == null || periodo.isBlank())
                ? notas.projetarTodas()
                : notas.projetarPorPeriodo(periodo);

        Map<Long, List<Double>> porAluno = new HashMap<>();
        for (NotaRepository.NotaBruta n : brutas) {
            Double media = Nota.calcularMedia(n.getP1(), n.getP2(), n.getT1(), n.getParticipacao());
            if (media != null) {
                porAluno.computeIfAbsent(n.getAlunoId(), k -> new ArrayList<>()).add(media);
            }
        }

        Map<Long, Double> medias = new HashMap<>();
        porAluno.forEach((alunoId, lista) ->
                medias.put(alunoId, lista.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
        return medias;
    }

    private Map<Long, Presenca> presencasPorAluno() {
        Map<Long, Presenca> presencas = new HashMap<>();
        for (FrequenciaRepository.FrequenciaResumo r : frequencias.resumoPorAluno()) {
            presencas.put(r.getAlunoId(), new Presenca(r.getTotal(), r.getFaltas()));
        }
        return presencas;
    }
}
