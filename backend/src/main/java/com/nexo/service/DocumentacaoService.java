package com.nexo.service;

import com.nexo.api.ApiException;
import com.nexo.domain.DocumentoEntregue;
import com.nexo.domain.EventoAuditoria;
import com.nexo.domain.Matricula;
import com.nexo.domain.TipoDocumento;
import com.nexo.repository.DocumentoEntregueRepository;
import com.nexo.repository.MatriculaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Checklist de documentos da matrícula.
 *
 * <p>O estado da documentação deixa de ser escolhido na mão e passa a ser
 * consequência da lista: com todos os obrigatórios entregues fica COMPLETA, com
 * nenhum documento fica INCOMPLETA, e no meio do caminho fica PENDENTE. Assim o
 * painel e a fila de trabalho param de depender de alguém lembrar de trocar o
 * estado depois de receber o papel.
 */
@Service
public class DocumentacaoService {

    private final MatriculaRepository matriculas;
    private final DocumentoEntregueRepository documentos;
    private final AuditoriaService auditoria;

    public DocumentacaoService(MatriculaRepository matriculas, DocumentoEntregueRepository documentos,
                               AuditoriaService auditoria) {
        this.matriculas = matriculas;
        this.documentos = documentos;
        this.auditoria = auditoria;
    }

    /** Um item do checklist: o que é, se é exigido e, se entregue, quando e por quem. */
    public record ItemChecklistDTO(String tipo, String rotulo, boolean obrigatorio, boolean entregue,
                                   Instant entregueEm, String recebidoPor, String observacao) {}

    public record ChecklistDTO(Long matriculaId, String aluno, String situacao,
                               int entregues, int totalObrigatorios, int obrigatoriosEntregues,
                               List<String> faltantes, List<ItemChecklistDTO> itens) {}

    /**
     * Transacional de escrita, e não readOnly, de propósito: matrícula gravada
     * antes do checklist existir tem a situação escolhida na mão e pode contradizer
     * a lista — foi o que apareceu no banco de exemplo, com "COMPLETA" e quatro
     * obrigatórios faltando. A leitura reconcilia esse caso uma vez, e a partir daí
     * a situação é sempre consequência dos documentos.
     */
    @Transactional
    public ChecklistDTO checklist(Long matriculaId) {
        return sincronizar(exigirMatricula(matriculaId));
    }

    public record RegistrarDocumentoRequest(String observacao) {}

    /**
     * Registra a entrega. Idempotente: reenviar atualiza a observação em vez de
     * criar uma segunda linha para o mesmo documento.
     */
    @Transactional
    public ChecklistDTO registrar(Long matriculaId, TipoDocumento tipo, String observacao, String operador) {
        Matricula matricula = exigirMatricula(matriculaId);

        DocumentoEntregue doc = documentos.findByMatriculaIdAndTipo(matriculaId, tipo)
                .orElseGet(() -> new DocumentoEntregue(matricula, tipo, operador, null));
        doc.setRecebidoPor(operador);
        doc.setObservacao(observacao == null || observacao.isBlank() ? null : observacao.trim());
        if (doc.getId() == null) doc.setEntregueEm(Instant.now());
        documentos.save(doc);

        auditoria.registrar(operador, EventoAuditoria.Tipo.ALTERACAO,
                "Documento recebido", matricula.getAluno().getNome() + " — " + tipo.getRotulo(), null);
        return recalcularEMontar(matricula);
    }

    /** Desfaz a entrega (documento devolvido, ou registrado por engano). */
    @Transactional
    public ChecklistDTO remover(Long matriculaId, TipoDocumento tipo, String operador) {
        Matricula matricula = exigirMatricula(matriculaId);
        documentos.findByMatriculaIdAndTipo(matriculaId, tipo).ifPresent(doc -> {
            documentos.delete(doc);
            auditoria.registrar(operador, EventoAuditoria.Tipo.ALTERACAO,
                    "Registro de documento removido",
                    matricula.getAluno().getNome() + " — " + tipo.getRotulo(), null);
        });
        return recalcularEMontar(matricula);
    }

    // ── Interno ──────────────────────────────────────────────────────────────

    private ChecklistDTO recalcularEMontar(Matricula matricula) {
        // O flush garante que o checklist recém-alterado seja o que a contagem lê.
        documentos.flush();
        return sincronizar(matricula);
    }

    /**
     * Monta o checklist e alinha {@code matricula.documentacao} com ele. A situação
     * devolvida é sempre a derivada da lista — nunca um valor guardado que já não
     * corresponde ao que está entregue.
     */
    private ChecklistDTO sincronizar(Matricula matricula) {
        ChecklistDTO dto = montar(matricula);
        Matricula.Documentacao derivada = situacaoDe(dto.obrigatoriosEntregues(), dto.entregues());

        if (matricula.getDocumentacao() != derivada) {
            matricula.setDocumentacao(derivada);
            matriculas.save(matricula);
        }
        return new ChecklistDTO(dto.matriculaId(), dto.aluno(), derivada.name(),
                dto.entregues(), dto.totalObrigatorios(), dto.obrigatoriosEntregues(),
                dto.faltantes(), dto.itens());
    }

    /**
     * INCOMPLETA quando nada chegou, COMPLETA quando todo obrigatório chegou,
     * PENDENTE no meio — que é onde mora a maior parte do trabalho da secretaria.
     */
    private static Matricula.Documentacao situacaoDe(int obrigatoriosEntregues, int entregues) {
        if (obrigatoriosEntregues >= TipoDocumento.totalObrigatorios()) return Matricula.Documentacao.COMPLETA;
        if (entregues == 0) return Matricula.Documentacao.INCOMPLETA;
        return Matricula.Documentacao.PENDENTE;
    }

    private ChecklistDTO montar(Matricula matricula) {
        Map<TipoDocumento, DocumentoEntregue> porTipo = documentos.findByMatriculaId(matricula.getId())
                .stream().collect(Collectors.toMap(DocumentoEntregue::getTipo, Function.identity(), (a, b) -> a));

        List<ItemChecklistDTO> itens = java.util.Arrays.stream(TipoDocumento.values())
                .map(tipo -> {
                    DocumentoEntregue doc = porTipo.get(tipo);
                    return new ItemChecklistDTO(tipo.name(), tipo.getRotulo(), tipo.isObrigatorio(),
                            doc != null,
                            doc != null ? doc.getEntregueEm() : null,
                            doc != null ? doc.getRecebidoPor() : null,
                            doc != null ? doc.getObservacao() : null);
                })
                .toList();

        // A lista de faltantes é o roteiro da ligação para o responsável.
        List<String> faltantes = itens.stream()
                .filter(i -> i.obrigatorio() && !i.entregue())
                .map(ItemChecklistDTO::rotulo)
                .toList();

        int entregues = (int) itens.stream().filter(ItemChecklistDTO::entregue).count();
        int obrigatoriosEntregues = (int) itens.stream()
                .filter(i -> i.obrigatorio() && i.entregue()).count();

        return new ChecklistDTO(matricula.getId(), matricula.getAluno().getNome(),
                matricula.getDocumentacao().name(), entregues,
                (int) TipoDocumento.totalObrigatorios(), obrigatoriosEntregues, faltantes, itens);
    }

    private Matricula exigirMatricula(Long id) {
        return matriculas.findById(id)
                .orElseThrow(() -> ApiException.notFound("Matrícula não encontrada."));
    }
}
