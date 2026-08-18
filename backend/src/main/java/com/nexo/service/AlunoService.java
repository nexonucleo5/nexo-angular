package com.nexo.service;

import com.nexo.api.ApiException;
import com.nexo.domain.*;
import com.nexo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cadastro de aluno neste sistema: nome, turma e um acesso.
 *
 * <p>Não se pede nascimento, sexo nem endereço. A ficha do aluno vive no sistema
 * de aula da escola; aqui o cadastro existe só para dar a alguém uma conta e a
 * turma cujo conteúdo ele vai estudar. Menos campo guardado é menos coisa a
 * vazar num incidente — e nenhum deles era usado para nada além de preencher tela.
 *
 * <p>O acesso é gerado pelo {@link CredenciaisService}: o client não decide
 * credencial.
 */
@Service
public class AlunoService {

    private final AlunoRepository alunos;
    private final InscricaoRepository inscricoes;
    private final TurmaRepository turmas;
    private final CredenciaisService credenciais;
    private final AuditoriaService auditoria;

    public AlunoService(AlunoRepository alunos, InscricaoRepository inscricoes, TurmaRepository turmas,
                        CredenciaisService credenciais, AuditoriaService auditoria) {
        this.alunos = alunos;
        this.inscricoes = inscricoes;
        this.turmas = turmas;
        this.credenciais = credenciais;
        this.auditoria = auditoria;
    }

    public record CadastroAluno(String nome, Long turmaId) {}

    public record AlunoCriado(Long id, String nome, String emailInstitucional, String senhaProvisoria,
                              Long inscricaoId) {}

    @Transactional
    public AlunoCriado cadastrar(CadastroAluno dados, String operador) {
        Map<String, String> erros = new LinkedHashMap<>();

        if (dados.nome() == null || dados.nome().trim().length() < 3) {
            erros.put("nome", "Informe o nome completo.");
        }
        if (dados.turmaId() == null) {
            erros.put("turmaId", "Selecione a turma.");
        }
        if (!erros.isEmpty()) {
            throw ApiException.validation("Dados de cadastro inválidos.", erros);
        }

        Turma turma = turmas.findById(dados.turmaId())
                .orElseThrow(() -> ApiException.badRequest("Turma inexistente."));

        Aluno aluno = new Aluno();
        aluno.setNome(dados.nome().trim());
        aluno.setTurma(turma);

        var acesso = credenciais.gerar(aluno.getNome());
        aluno.setEmailInstitucional(acesso.login());
        aluno.setUsuario(credenciais.criarUsuario(aluno.getNome(), turma.getNome(), Role.ALUNO, acesso));
        alunos.save(aluno);

        Inscricao inscricao = new Inscricao();
        inscricao.setAluno(aluno);
        inscricao.setTurma(turma);
        inscricoes.save(inscricao);

        // O nome basta na trilha: o que interessa auditar é quem criou a conta,
        // não repetir dado do aluno numa segunda tabela.
        auditoria.registrar(operador, EventoAuditoria.Tipo.ALTERACAO,
                "Aluno cadastrado", "Aluno: " + aluno.getNome(), null);

        return new AlunoCriado(aluno.getId(), aluno.getNome(), acesso.login(), acesso.senhaProvisoria(),
                inscricao.getId());
    }
}
