package com.nexo.service;

import com.nexo.api.ApiException;
import com.nexo.domain.*;
import com.nexo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cadastro de aluno: dados básicos + ano/turma do ensino básico. O acesso ao
 * sistema é gerado pelo CredenciaisService (client não decide credenciais).
 */
@Service
public class AlunoService {

    private final AlunoRepository alunos;
    private final MatriculaRepository matriculas;
    private final TurmaRepository turmas;
    private final CredenciaisService credenciais;
    private final AuditoriaService auditoria;

    public AlunoService(AlunoRepository alunos, MatriculaRepository matriculas, TurmaRepository turmas,
                        CredenciaisService credenciais, AuditoriaService auditoria) {
        this.alunos = alunos;
        this.matriculas = matriculas;
        this.turmas = turmas;
        this.credenciais = credenciais;
        this.auditoria = auditoria;
    }

    /**
     * {@code emailContato}: endereço do responsável, para onde vai o convite de primeiro
     * acesso. Opcional — sem ele o cadastro funciona igual e a senha provisória continua
     * saindo na resposta, para o diretor repassar.
     */
    public record CadastroAluno(String nome, String dataNascimento, String sexo, Long turmaId,
                                String emailContato) {}

    public record AlunoCriado(Long id, String nome, String emailInstitucional, String senhaProvisoria,
                              Long matriculaId) {}

    @Transactional
    public AlunoCriado cadastrar(CadastroAluno dados, String operador) {
        Map<String, String> erros = new LinkedHashMap<>();

        if (dados.nome() == null || dados.nome().trim().length() < 3) {
            erros.put("nome", "Informe o nome completo.");
        }
        LocalDate nascimento = null;
        if (dados.dataNascimento() == null || dados.dataNascimento().isBlank()) {
            erros.put("dataNascimento", "Informe a data de nascimento.");
        } else {
            try {
                nascimento = LocalDate.parse(dados.dataNascimento());
                if (nascimento.isAfter(LocalDate.now())) {
                    erros.put("dataNascimento", "A data de nascimento não pode ser futura.");
                }
            } catch (java.time.format.DateTimeParseException e) {
                erros.put("dataNascimento", "Data de nascimento inválida.");
            }
        }
        if (dados.sexo() == null || dados.sexo().isBlank()) {
            erros.put("sexo", "Selecione o sexo.");
        }
        if (dados.turmaId() == null) {
            erros.put("turmaId", "Selecione o ano do ensino básico.");
        }
        if (!erros.isEmpty()) {
            throw ApiException.validation("Dados de cadastro inválidos.", erros);
        }

        Turma turma = turmas.findById(dados.turmaId())
                .orElseThrow(() -> ApiException.badRequest("Turma inexistente."));

        Aluno aluno = new Aluno();
        aluno.setNome(dados.nome().trim());
        aluno.setDataNascimento(nascimento);
        aluno.setSexo(dados.sexo());
        aluno.setTurma(turma);

        var acesso = credenciais.gerar(aluno.getNome());
        aluno.setEmailInstitucional(acesso.login());
        aluno.setUsuario(credenciais.criarUsuario(aluno.getNome(), turma.getNome(), Role.ALUNO, acesso,
                dados.emailContato()));
        alunos.save(aluno);

        Matricula matricula = new Matricula();
        matricula.setAluno(aluno);
        matricula.setTurma(turma);
        matricula.setStatus(Matricula.Status.PENDENTE);
        matricula.setDocumentacao(Matricula.Documentacao.PENDENTE);
        matriculas.save(matricula);

        auditoria.registrar(operador, EventoAuditoria.Tipo.ALTERACAO,
                "Aluno cadastrado", "Aluno: " + aluno.getNome(), null);

        return new AlunoCriado(aluno.getId(), aluno.getNome(), acesso.login(), acesso.senhaProvisoria(),
                matricula.getId());
    }
}
