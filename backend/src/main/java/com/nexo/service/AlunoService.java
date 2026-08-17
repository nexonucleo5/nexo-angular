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

    /** A escola atende do 6º ano ao 3º do médio; a faixa acomoda distorção idade-série. */
    private static final int IDADE_MINIMA_ALUNO = 4;
    private static final int IDADE_MAXIMA_ALUNO = 100;

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
     * {@code endereco} é opcional — a escola matricula antes de ter a documentação
     * toda, e é justamente essa pendência que a secretaria acompanha na fila.
     */
    public record CadastroAluno(String nome, String dataNascimento, String sexo, Long turmaId,
                                EnderecoRequest endereco) {}

    public record EnderecoRequest(String cep, String logradouro, String numero, String complemento,
                                  String bairro, String cidade, String uf) {}

    public record AlunoCriado(Long id, String nome, String emailInstitucional, String senhaProvisoria,
                              Long matriculaId) {}

    @Transactional
    public AlunoCriado cadastrar(CadastroAluno dados, String operador) {
        Map<String, String> erros = new LinkedHashMap<>();

        if (dados.nome() == null || dados.nome().trim().length() < 3) {
            erros.put("nome", "Informe o nome completo.");
        }
        // Faixa larga de propósito (cabe repetência e EJA); o que ela barra é o
        // impossível — recém-nascido matriculado no 6º ano e ano digitado errado.
        LocalDate nascimento = DataNascimento.validar(dados.dataNascimento(),
                IDADE_MINIMA_ALUNO, IDADE_MAXIMA_ALUNO, erros);
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
        aplicarEndereco(aluno, dados.endereco());

        var acesso = credenciais.gerar(aluno.getNome());
        aluno.setEmailInstitucional(acesso.login());
        aluno.setUsuario(credenciais.criarUsuario(aluno.getNome(), turma.getNome(), Role.ALUNO, acesso));
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

    /**
     * Copia o endereço informado para o aluno. Requisição sem endereço, ou com todos
     * os campos em branco, deixa o aluno sem endereço em vez de gravar linhas vazias.
     * O CEP é normalizado (só dígitos, 8 posições) para a busca por CEP mais tarde
     * casar com o que está gravado; UF sobe para maiúsculo pelo mesmo motivo.
     */
    @Transactional
    public void aplicarEndereco(Aluno aluno, EnderecoRequest dados) {
        if (dados == null) return;

        Endereco endereco = aluno.getEndereco();
        endereco.setCep(dados.cep() == null || dados.cep().isBlank()
                ? null : ConsultaCep.normalizar(dados.cep()));
        endereco.setLogradouro(limpar(dados.logradouro()));
        endereco.setNumero(limpar(dados.numero()));
        endereco.setComplemento(limpar(dados.complemento()));
        endereco.setBairro(limpar(dados.bairro()));
        endereco.setCidade(limpar(dados.cidade()));

        String uf = limpar(dados.uf());
        if (uf != null) {
            uf = uf.toUpperCase();
            if (uf.length() != 2) {
                throw ApiException.validation("Endereço inválido.",
                        Map.of("uf", "A UF tem duas letras (ex.: SP)."));
            }
        }
        endereco.setUf(uf);
    }

    private static String limpar(String valor) {
        if (valor == null) return null;
        String s = valor.trim();
        return s.isEmpty() ? null : s;
    }

    /** Atualiza só o endereço de um aluno que já existe. */
    @Transactional
    public Endereco atualizarEndereco(Long alunoId, EnderecoRequest dados, String operador) {
        Aluno aluno = alunos.findById(alunoId)
                .orElseThrow(() -> ApiException.notFound("Aluno não encontrado."));
        aplicarEndereco(aluno, dados);
        alunos.save(aluno);
        auditoria.registrar(operador, EventoAuditoria.Tipo.ALTERACAO,
                "Endereço de aluno atualizado", "Aluno: " + aluno.getNome(), null);
        return aluno.getEndereco();
    }
}
