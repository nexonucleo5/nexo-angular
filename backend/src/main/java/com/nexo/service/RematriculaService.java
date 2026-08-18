package com.nexo.service;

import com.nexo.api.ApiException;
import com.nexo.domain.Aluno;
import com.nexo.domain.DocumentoEntregue;
import com.nexo.domain.EventoAuditoria;
import com.nexo.domain.Matricula;
import com.nexo.domain.TipoDocumento;
import com.nexo.domain.Turma;
import com.nexo.repository.AlunoRepository;
import com.nexo.repository.DocumentoEntregueRepository;
import com.nexo.repository.MatriculaRepository;
import com.nexo.repository.TurmaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rematrícula: renova o vínculo do aluno para o ano letivo seguinte, promovendo-o
 * para a turma da série seguinte.
 *
 * <p>É o trabalho sazonal que concentra o maior volume da secretaria, e por isso
 * existe em duas formas: uma matrícula por vez e a turma inteira de uma vez. O
 * resultado do lote diz o que foi renovado <b>e por que cada um ficou de fora</b> —
 * um "12 de 30 renovadas" sem os motivos obriga a conferir os 18 na mão.
 *
 * <p>O que a renovação carrega e o que ela zera:
 * <ul>
 *   <li>vínculo novo em PENDENTE — rematrícula se confirma, não se presume;</li>
 *   <li>documentos permanentes (certidão, RG, CPF) acompanham o aluno;</li>
 *   <li>os que vencem (comprovante de residência, foto, vacinação, histórico)
 *       voltam à fila, que é exatamente o que a secretaria precisa cobrar.</li>
 * </ul>
 */
@Service
public class RematriculaService {

    private final MatriculaRepository matriculas;
    private final TurmaRepository turmas;
    private final AlunoRepository alunos;
    private final DocumentoEntregueRepository documentos;
    private final AuditoriaService auditoria;

    public RematriculaService(MatriculaRepository matriculas, TurmaRepository turmas, AlunoRepository alunos,
                              DocumentoEntregueRepository documentos, AuditoriaService auditoria) {
        this.matriculas = matriculas;
        this.turmas = turmas;
        this.alunos = alunos;
        this.documentos = documentos;
        this.auditoria = auditoria;
    }

    public record RematriculaDTO(Long matriculaId, Long origemId, Long alunoId, String aluno,
                                 String turmaAnterior, String turmaNova, int anoLetivo) {}

    /** Por que uma matrícula do lote não foi renovada — em texto que se lê na tela. */
    public record IgnoradaDTO(Long matriculaId, String aluno, String motivo) {}

    public record ResultadoLoteDTO(int renovadas, int ignoradas,
                                   List<RematriculaDTO> novas, List<IgnoradaDTO> semRenovar) {}

    /** Motivo pelo qual uma matrícula não pode ser renovada, ou null se pode. */
    private String impedimento(Matricula m, int anoDestino) {
        if (m.getStatus() == Matricula.Status.CANCELADA) {
            return "Matrícula cancelada — o retorno é matrícula nova, não rematrícula.";
        }
        if (m.getStatus() == Matricula.Status.PENDENTE) {
            return "Matrícula ainda não efetivada neste ano.";
        }
        if (m.getTurma() == null) {
            return "Aluno sem turma: defina a turma antes de renovar.";
        }
        if (ProgressaoSerie.ehConcluinte(m.getTurma().getNome())) {
            return "Concluinte do ensino médio — não há série seguinte.";
        }
        if (ProgressaoSerie.proximaTurma(m.getTurma().getNome()).isEmpty()) {
            return "Não foi possível determinar a série seguinte de " + m.getTurma().getNome() + ".";
        }
        if (jaRenovada(m.getAluno().getId(), anoDestino)) {
            return "Já existe matrícula para " + anoDestino + ".";
        }
        return null;
    }

    /** Renova uma matrícula. O ano de destino é sempre o seguinte ao dela. */
    @Transactional
    public RematriculaDTO renovar(Long matriculaId, String operador) {
        Matricula origem = matriculas.findById(matriculaId)
                .orElseThrow(() -> ApiException.notFound("Matrícula não encontrada."));

        int anoDestino = origem.getAnoLetivo() + 1;
        String impedimento = impedimento(origem, anoDestino);
        if (impedimento != null) {
            throw ApiException.validation("Rematrícula não permitida.", Map.of("matricula", impedimento));
        }
        return criar(origem, anoDestino, operador);
    }

    /**
     * Renova a turma inteira. Nada de tudo-ou-nada: cada matrícula é avaliada por
     * si, e quem não pode entra na lista de ignoradas com o motivo. Um concluinte
     * no meio da turma não pode impedir a renovação dos outros 29.
     */
    @Transactional
    public ResultadoLoteDTO renovarTurma(Long turmaId, String operador) {
        Turma turma = turmas.findById(turmaId)
                .orElseThrow(() -> ApiException.notFound("Turma não encontrada."));

        List<Matricula> daTurma = matriculas.findAll().stream()
                .filter(m -> m.getTurma() != null && m.getTurma().getId().equals(turmaId))
                .toList();

        List<RematriculaDTO> novas = new ArrayList<>();
        List<IgnoradaDTO> ignoradas = new ArrayList<>();

        for (Matricula m : daTurma) {
            int anoDestino = m.getAnoLetivo() + 1;
            String impedimento = impedimento(m, anoDestino);
            if (impedimento != null) {
                ignoradas.add(new IgnoradaDTO(m.getId(), m.getAluno().getNome(), impedimento));
                continue;
            }
            novas.add(criar(m, anoDestino, operador));
        }

        auditoria.registrar(operador, EventoAuditoria.Tipo.ALTERACAO,
                "Rematrícula em lote",
                turma.getNome() + ": " + novas.size() + " renovada(s), " + ignoradas.size() + " ignorada(s)",
                null);

        return new ResultadoLoteDTO(novas.size(), ignoradas.size(), novas, ignoradas);
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    private RematriculaDTO criar(Matricula origem, int anoDestino, String operador) {
        String nomeProxima = ProgressaoSerie.proximaTurma(origem.getTurma().getNome())
                .orElseThrow(() -> ApiException.badRequest("Série seguinte indeterminada."));

        Turma destino = turmas.findAll().stream()
                .filter(t -> t.getNome().equals(nomeProxima))
                .findFirst()
                .orElseThrow(() -> ApiException.validation("Turma de destino inexistente.",
                        Map.of("turma", "A turma " + nomeProxima + " não existe. Crie-a antes de renovar.")));

        long ocupadas = alunos.countByTurmaId(destino.getId());
        if (ocupadas >= destino.getCapacidade()) {
            throw ApiException.validation("Turma de destino lotada.",
                    Map.of("turma", nomeProxima + " já tem " + ocupadas + " alunos para "
                            + destino.getCapacidade() + " vagas."));
        }

        Matricula nova = new Matricula();
        nova.setAluno(origem.getAluno());
        nova.setTurma(destino);
        nova.setAnoLetivo(anoDestino);
        nova.setOrigemMatriculaId(origem.getId());
        // PENDENTE de propósito: rematrícula se confirma com o responsável, e é a
        // entrega dos documentos do ano novo que a efetiva.
        nova.setStatus(Matricula.Status.PENDENTE);
        nova.setDocumentacao(Matricula.Documentacao.INCOMPLETA);
        nova.setDataMatricula(LocalDate.now());
        matriculas.save(nova);

        copiarDocumentosPermanentes(origem, nova);

        // O aluno passa a cursar a turma nova — o diário e as notas seguem por ela.
        Aluno aluno = origem.getAluno();
        aluno.setTurma(destino);
        alunos.save(aluno);

        auditoria.registrar(operador, EventoAuditoria.Tipo.ALTERACAO, "Rematrícula",
                aluno.getNome() + ": " + origem.getTurma().getNome() + " → " + destino.getNome()
                        + " (" + anoDestino + ")", null);

        return new RematriculaDTO(nova.getId(), origem.getId(), aluno.getId(), aluno.getNome(),
                origem.getTurma().getNome(), destino.getNome(), anoDestino);
    }

    /**
     * Certidão, RG e CPF acompanham o aluno; o resto é pedido de novo. Sem isto a
     * secretaria recadastraria à mão, todo ano, documento que não muda nunca.
     */
    private void copiarDocumentosPermanentes(Matricula origem, Matricula nova) {
        for (DocumentoEntregue doc : documentos.findByMatriculaId(origem.getId())) {
            TipoDocumento tipo = doc.getTipo();
            if (!tipo.isPermanente()) continue;

            DocumentoEntregue copia = new DocumentoEntregue(nova, tipo, doc.getRecebidoPor(),
                    doc.getObservacao());
            copia.setEntregueEm(doc.getEntregueEm()); // preserva quando foi entregue de fato
            documentos.save(copia);
        }
    }

    private boolean jaRenovada(Long alunoId, int anoDestino) {
        return matriculas.findAll().stream()
                .anyMatch(m -> m.getAluno().getId().equals(alunoId) && m.getAnoLetivo() == anoDestino);
    }
}
