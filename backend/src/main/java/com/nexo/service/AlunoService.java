package com.nexo.service;

import com.nexo.api.ApiException;
import com.nexo.domain.*;
import com.nexo.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cadastro de aluno: geração de e-mail institucional e senha provisória
 * migrada do cadastro.ts para o servidor (client não decide credenciais).
 */
@Service
public class AlunoService {

    private static final String DOMINIO = "nexo.escola.com";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AlunoRepository alunos;
    private final UsuarioRepository usuarios;
    private final MatriculaRepository matriculas;
    private final TurmaRepository turmas;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoria;

    public AlunoService(AlunoRepository alunos, UsuarioRepository usuarios, MatriculaRepository matriculas,
                        TurmaRepository turmas, PasswordEncoder passwordEncoder, AuditoriaService auditoria) {
        this.alunos = alunos;
        this.usuarios = usuarios;
        this.matriculas = matriculas;
        this.turmas = turmas;
        this.passwordEncoder = passwordEncoder;
        this.auditoria = auditoria;
    }

    public record CadastroAluno(String nome, String cpf, String sexo, String telefone, String dataNascimento,
                                String emailResponsavel, String cpfResponsavel, String telefoneResponsavel,
                                String endereco, String complemento, Long turmaId) {}

    public record AlunoCriado(Long id, String nome, String emailInstitucional, String senhaProvisoria,
                              Long matriculaId) {}

    @Transactional
    public AlunoCriado cadastrar(CadastroAluno dados, String operador) {
        Map<String, String> erros = new LinkedHashMap<>();
        String cpf = dados.cpf() == null ? "" : dados.cpf().replaceAll("\\D", "");

        if (dados.nome() == null || dados.nome().trim().length() < 3) {
            erros.put("nome", "Informe o nome completo.");
        }
        if (!cpfValido(cpf)) {
            erros.put("cpf", "CPF inválido.");
        } else if (alunos.existsByCpf(cpf)) {
            erros.put("cpf", "CPF já cadastrado.");
        }
        if (dados.emailResponsavel() == null || !dados.emailResponsavel().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            erros.put("emailResponsavel", "E-mail do responsável inválido.");
        }
        if (!erros.isEmpty()) {
            throw ApiException.validation("Dados de cadastro inválidos.", erros);
        }

        Aluno aluno = new Aluno();
        aluno.setNome(dados.nome().trim());
        aluno.setCpf(cpf);
        aluno.setSexo(dados.sexo());
        aluno.setTelefone(dados.telefone());
        if (dados.dataNascimento() != null && !dados.dataNascimento().isBlank()) {
            aluno.setDataNascimento(LocalDate.parse(dados.dataNascimento()));
        }
        aluno.setEmailResponsavel(dados.emailResponsavel());
        aluno.setCpfResponsavel(dados.cpfResponsavel());
        aluno.setTelefoneResponsavel(dados.telefoneResponsavel());
        aluno.setEndereco(dados.endereco());
        aluno.setComplemento(dados.complemento());
        if (dados.turmaId() != null) {
            aluno.setTurma(turmas.findById(dados.turmaId())
                    .orElseThrow(() -> ApiException.badRequest("Turma inexistente.")));
        }

        String email = gerarEmailInstitucional(dados.nome());
        String senha = gerarSenhaProvisoria();
        aluno.setEmailInstitucional(email);

        Usuario usuario = new Usuario();
        usuario.setLogin(email);
        usuario.setSenhaHash(passwordEncoder.encode(senha));
        usuario.setNome(aluno.getNome());
        usuario.setCargo(aluno.getTurma() != null ? aluno.getTurma().getNome() : "Aluno");
        usuario.setRole(Role.ALUNO);
        usuarios.save(usuario);
        aluno.setUsuario(usuario);
        alunos.save(aluno);

        Matricula matricula = new Matricula();
        matricula.setAluno(aluno);
        matricula.setTurma(aluno.getTurma());
        matricula.setStatus(Matricula.Status.PENDENTE);
        matricula.setDocumentacao(Matricula.Documentacao.PENDENTE);
        matriculas.save(matricula);

        auditoria.registrar(operador, EventoAuditoria.Tipo.ALTERACAO,
                "Aluno cadastrado", "Aluno: " + aluno.getNome(), null);

        return new AlunoCriado(aluno.getId(), aluno.getNome(), email, senha, matricula.getId());
    }

    /** primeiro-nome.sobrenome@dominio, com sufixo numérico em caso de colisão. */
    private String gerarEmailInstitucional(String nomeCompleto) {
        String[] partes = normalizar(nomeCompleto).split("\\s+");
        String base = partes.length > 1 ? partes[0] + "." + partes[partes.length - 1] : partes[0];
        String email = base + "@" + DOMINIO;
        int sufixo = 1;
        while (alunos.existsByEmailInstitucional(email) || usuarios.existsByLoginIgnoreCase(email)) {
            email = base + (++sufixo) + "@" + DOMINIO;
        }
        return email;
    }

    private String gerarSenhaProvisoria() {
        String alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder("Nexo@");
        for (int i = 0; i < 6; i++) {
            sb.append(alfabeto.charAt(RANDOM.nextInt(alfabeto.length())));
        }
        return sb.toString();
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z\\s]", "");
    }

    /** Validação real dos dígitos verificadores do CPF. */
    static boolean cpfValido(String cpf) {
        if (cpf == null || !cpf.matches("\\d{11}") || cpf.chars().distinct().count() == 1) return false;
        for (int j = 9; j <= 10; j++) {
            int soma = 0;
            for (int i = 0; i < j; i++) {
                soma += (cpf.charAt(i) - '0') * (j + 1 - i);
            }
            int digito = (soma * 10) % 11 % 10;
            if (digito != cpf.charAt(j) - '0') return false;
        }
        return true;
    }
}
