package com.nexo.service;

import com.nexo.api.ApiException;
import com.nexo.domain.Aluno;
import com.nexo.domain.Matricula;
import com.nexo.domain.Nota;
import com.nexo.repository.AlunoRepository;
import com.nexo.repository.MatriculaRepository;
import com.nexo.repository.NotaRepository;
import com.nexo.repository.ObservacaoPedagogicaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Prontuário do aluno: a ficha completa numa resposta só.
 *
 * <p>Os dados sempre existiram, mas espalhados — cadastro numa tela, endereço em
 * outra, matrícula numa terceira, notas numa quarta. Quando o responsável liga
 * perguntando "como está meu filho?", a secretária precisava abrir quatro telas e
 * juntar na cabeça. Aqui é uma leitura só, na ordem em que a conversa acontece.
 */
@Service
public class ProntuarioService {

    private final AlunoRepository alunos;
    private final MatriculaRepository matriculas;
    private final NotaRepository notas;
    private final ObservacaoPedagogicaRepository observacoes;
    private final DocumentacaoService documentacao;

    public ProntuarioService(AlunoRepository alunos, MatriculaRepository matriculas, NotaRepository notas,
                             ObservacaoPedagogicaRepository observacoes, DocumentacaoService documentacao) {
        this.alunos = alunos;
        this.matriculas = matriculas;
        this.notas = notas;
        this.observacoes = observacoes;
        this.documentacao = documentacao;
    }

    public record IdentificacaoDTO(Long id, String nome, String emailInstitucional, String sexo,
                                   LocalDate dataNascimento, Integer idade, String turma, String etapa) {}

    public record EnderecoResumoDTO(String cep, String resumo) {}

    public record MatriculaResumoDTO(Long id, String status, String documentacao, LocalDate dataMatricula) {}

    public record DesempenhoDTO(String disciplina, String periodo, Double media) {}

    public record ProntuarioDTO(IdentificacaoDTO identificacao,
                                EnderecoResumoDTO endereco,
                                MatriculaResumoDTO matricula,
                                DocumentacaoService.ChecklistDTO documentos,
                                List<DesempenhoDTO> desempenho,
                                Double mediaGeral,
                                int observacoesPedagogicas) {}

    /**
     * Não é readOnly porque o checklist reconcilia a situação da documentação de
     * matrículas antigas (ver DocumentacaoService.checklist). Numa transação
     * readOnly o Hibernate não faz flush, então a correção seria calculada e
     * descartada em silêncio — o prontuário mostraria o valor certo e o banco
     * continuaria com o antigo.
     */
    @Transactional
    public ProntuarioDTO montar(Long alunoId) {
        Aluno aluno = alunos.findById(alunoId)
                .orElseThrow(() -> ApiException.notFound("Aluno não encontrado."));

        var turma = aluno.getTurma();
        Integer idade = aluno.getDataNascimento() == null ? null
                : java.time.Period.between(aluno.getDataNascimento(), LocalDate.now()).getYears();

        var identificacao = new IdentificacaoDTO(aluno.getId(), aluno.getNome(),
                aluno.getEmailInstitucional(), aluno.getSexo(), aluno.getDataNascimento(), idade,
                turma != null ? turma.getNome() : null,
                turma != null ? turma.getSegmento().name() : null);

        var end = aluno.getEndereco();
        EnderecoResumoDTO endereco = end == null || end.estaVazio()
                ? null
                : new EnderecoResumoDTO(end.getCep(), end.resumo());

        // A matrícula mais recente é a que vale para atendimento; as antigas
        // interessam ao histórico, não a quem está com o responsável na linha.
        Matricula matricula = matriculas.findAll().stream()
                .filter(m -> m.getAluno().getId().equals(alunoId))
                .max(Comparator.comparing(Matricula::getDataMatricula,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);

        MatriculaResumoDTO resumoMatricula = matricula == null ? null
                : new MatriculaResumoDTO(matricula.getId(), matricula.getStatus().name(),
                        matricula.getDocumentacao().name(), matricula.getDataMatricula());

        var checklist = matricula == null ? null : documentacao.checklist(matricula.getId());

        List<Nota> doAluno = notas.findByAlunoId(alunoId);
        List<DesempenhoDTO> desempenho = doAluno.stream()
                .sorted(Comparator.comparing(Nota::getDisciplina, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(n -> new DesempenhoDTO(n.getDisciplina(), n.getPeriodo(), n.getMedia()))
                .toList();

        Double mediaGeral = doAluno.stream()
                .map(Nota::getMedia)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream().boxed().findFirst()
                .map(m -> Math.round(m * 10) / 10.0)
                .orElse(null);

        return new ProntuarioDTO(identificacao, endereco, resumoMatricula, checklist,
                desempenho, mediaGeral,
                observacoes.findByAlunoIdOrderByCriadaEmDesc(alunoId).size());
    }
}
