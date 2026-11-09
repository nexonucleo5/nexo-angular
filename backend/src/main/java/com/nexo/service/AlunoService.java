package com.nexo.service;

import com.nexo.api.ApiException;
import com.nexo.domain.*;
import com.nexo.repository.*;
import com.nexo.security.UsuarioAutenticado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final CadastroPendenteRepository pendentes;
    private final ConfiguracaoService configuracoes;

    public AlunoService(AlunoRepository alunos, InscricaoRepository inscricoes, TurmaRepository turmas,
                        CredenciaisService credenciais, AuditoriaService auditoria,
                        CadastroPendenteRepository pendentes, ConfiguracaoService configuracoes) {
        this.pendentes = pendentes;
        this.configuracoes = configuracoes;
        this.alunos = alunos;
        this.inscricoes = inscricoes;
        this.turmas = turmas;
        this.credenciais = credenciais;
        this.auditoria = auditoria;
    }

    public record CadastroAluno(String nome, Long turmaId) {}

    /** O que um cadastro devolve: a conta criada, ou o pedido que ficou na fila de aprovação. */
    public sealed interface ResultadoCadastro permits AlunoCriado, CadastroSolicitado {}

    public record AlunoCriado(Long id, String nome, String emailInstitucional, String senhaProvisoria,
                              Long inscricaoId) implements ResultadoCadastro {}

    /**
     * Cadastro que aguarda aprovação: sem login e sem senha, porque a conta ainda não
     * existe. {@code pendente} é sempre {@code true} — é o campo que o cliente lê para
     * distinguir esta resposta da de {@link AlunoCriado}.
     */
    public record CadastroSolicitado(Long id, String nome, Long turmaId, String turma, boolean pendente)
            implements ResultadoCadastro {}

    /**
     * Cadastra o aluno — ou, se o operador é diretor e a configuração "Exigir aprovação
     * manual de cadastro de aluno" está ligada, só registra o pedido na fila.
     *
     * <p>O administrador nunca passa pela fila: ele é quem aprova, e a opção mora na
     * configuração do diretor (o ADMIN não tem essa seção).
     */
    @Transactional
    public ResultadoCadastro cadastrar(CadastroAluno dados, UsuarioAutenticado operador) {
        Turma turma = validar(dados);
        boolean diretor = Role.DIRETOR.name().equals(operador.role());
        if (diretor && configuracoes.exigeAprovacaoManualDeAluno(operador.id())) {
            CadastroPendente pedido = new CadastroPendente();
            pedido.setNome(dados.nome().trim());
            pedido.setTurma(turma);
            pedido.setSolicitadoPor(operador.nome());
            pendentes.save(pedido);
            auditoria.registrar(operador.nome(), EventoAuditoria.Tipo.ALTERACAO,
                    "Cadastro de aluno aguardando aprovação", "Aluno: " + pedido.getNome(), null);
            return new CadastroSolicitado(pedido.getId(), pedido.getNome(), turma.getId(), turma.getNome(), true);
        }
        return criarAluno(dados.nome().trim(), turma, operador.nome());
    }

    /** Pedidos ainda sem decisão, do mais antigo para o mais novo. */
    @Transactional(readOnly = true)
    public List<CadastroPendente> listarPendentes() {
        return pendentes.listarPorSituacao(CadastroPendente.Situacao.PENDENTE);
    }

    @Transactional(readOnly = true)
    public long contarPendentes() {
        return pendentes.countBySituacao(CadastroPendente.Situacao.PENDENTE);
    }

    /** Aprova o pedido: é aqui que a conta, a inscrição e a senha provisória passam a existir. */
    @Transactional
    public AlunoCriado aprovar(Long pedidoId, String operador) {
        CadastroPendente pedido = pedidoPendente(pedidoId);
        String nome = pedido.getNome();
        String solicitante = pedido.getSolicitadoPor();
        Long turmaId = pedido.getTurma().getId();
        decidir(pedidoId, CadastroPendente.Situacao.APROVADO, operador);
        // O update em lote limpa o contexto de persistência: a turma é relida, não reaproveitada.
        Turma turma = turmas.findById(turmaId)
                .orElseThrow(() -> ApiException.badRequest("A turma do pedido não existe mais."));
        AlunoCriado criado = criarAluno(nome, turma, operador);
        auditoria.registrar(operador, EventoAuditoria.Tipo.ALTERACAO,
                "Cadastro de aluno aprovado", "Aluno: " + nome + " (solicitado por " + solicitante + ")", null);
        return criado;
    }

    @Transactional
    public CadastroPendente rejeitar(Long pedidoId, String operador) {
        CadastroPendente pedido = pedidoPendente(pedidoId);
        decidir(pedidoId, CadastroPendente.Situacao.REJEITADO, operador);
        auditoria.registrar(operador, EventoAuditoria.Tipo.ALTERACAO,
                "Cadastro de aluno rejeitado", "Aluno: " + pedido.getNome()
                        + " (solicitado por " + pedido.getSolicitadoPor() + ")", null);
        return pedido;
    }

    private CadastroPendente pedidoPendente(Long id) {
        CadastroPendente pedido = pendentes.findById(id)
                .orElseThrow(() -> ApiException.notFound("Pedido de cadastro não encontrado."));
        if (pedido.getSituacao() != CadastroPendente.Situacao.PENDENTE) {
            throw jaDecidido();
        }
        return pedido;
    }

    /** O {@code update} condicional é a trava de verdade: o teste acima só dá a mensagem cedo. */
    private void decidir(Long id, CadastroPendente.Situacao nova, String operador) {
        if (pendentes.decidir(id, nova, operador, Instant.now()) == 0) {
            throw jaDecidido();
        }
    }

    private static ApiException jaDecidido() {
        return new ApiException(org.springframework.http.HttpStatus.CONFLICT, "CONFLICT",
                "Este pedido de cadastro já foi decidido.");
    }

    private Turma validar(CadastroAluno dados) {
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

        return turmas.findById(dados.turmaId())
                .orElseThrow(() -> ApiException.badRequest("Turma inexistente."));
    }

    private AlunoCriado criarAluno(String nome, Turma turma, String operador) {
        Aluno aluno = new Aluno();
        aluno.setNome(nome);
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
