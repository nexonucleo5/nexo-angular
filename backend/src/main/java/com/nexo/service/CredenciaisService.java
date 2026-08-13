package com.nexo.service;

import com.nexo.domain.Role;
import com.nexo.domain.Usuario;
import com.nexo.repository.AlunoRepository;
import com.nexo.repository.ProfessorRepository;
import com.nexo.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.text.Normalizer;

/**
 * Geração do acesso ao sistema (e-mail institucional + senha provisória),
 * compartilhada pelos cadastros de aluno e de professor. A regra é a mesma que
 * vivia no AlunoService: o client nunca decide credenciais.
 */
@Service
public class CredenciaisService {

    private static final String DOMINIO = "nexo.escola.com";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final AlunoRepository alunos;
    private final ProfessorRepository professores;
    private final PasswordEncoder passwordEncoder;
    private final RecuperacaoSenhaService recuperacao;

    public CredenciaisService(UsuarioRepository usuarios, AlunoRepository alunos,
                              ProfessorRepository professores, PasswordEncoder passwordEncoder,
                              RecuperacaoSenhaService recuperacao) {
        this.usuarios = usuarios;
        this.alunos = alunos;
        this.professores = professores;
        this.passwordEncoder = passwordEncoder;
        this.recuperacao = recuperacao;
    }

    /** Credenciais recém-geradas; a senha só existe em claro aqui e na resposta do cadastro. */
    public record Acesso(String login, String senhaProvisoria) {}

    public Acesso gerar(String nomeCompleto) {
        return new Acesso(gerarEmailInstitucional(nomeCompleto), gerarSenhaProvisoria());
    }

    /**
     * Cria e persiste o Usuario de login com as credenciais geradas.
     *
     * <p>Havendo endereço de contato, dispara o convite de primeiro acesso — um link para a
     * pessoa escolher a própria senha. A senha provisória continua indo na resposta do
     * cadastro para o diretor: é o caminho de quem não informou endereço, e o de sempre
     * quando a mensagem não chega.
     *
     * @param emailContato endereço que recebe de fato a mensagem; para aluno menor de idade,
     *                     o do responsável. Nulo é normal e só significa que não há convite.
     */
    public Usuario criarUsuario(String nome, String cargo, Role role, Acesso acesso, String emailContato) {
        Usuario usuario = new Usuario();
        usuario.setLogin(acesso.login());
        usuario.setSenhaHash(passwordEncoder.encode(acesso.senhaProvisoria()));
        usuario.setNome(nome);
        usuario.setCargo(cargo);
        usuario.setRole(role);
        usuario.setEmailContato(emailContato == null || emailContato.isBlank()
                ? null : emailContato.trim().toLowerCase());

        Usuario salvo = usuarios.save(usuario);
        recuperacao.enviarPrimeiroAcesso(salvo);
        return salvo;
    }

    /** primeiro-nome.sobrenome@dominio, com sufixo numérico em caso de colisão. */
    private String gerarEmailInstitucional(String nomeCompleto) {
        String[] partes = normalizar(nomeCompleto).split("\\s+");
        String base = partes.length > 1 ? partes[0] + "." + partes[partes.length - 1] : partes[0];
        String email = base + "@" + DOMINIO;
        int sufixo = 1;
        while (emailEmUso(email)) {
            email = base + (++sufixo) + "@" + DOMINIO;
        }
        return email;
    }

    private boolean emailEmUso(String email) {
        return usuarios.existsByLoginIgnoreCase(email)
                || alunos.existsByEmailInstitucional(email)
                || professores.existsByEmail(email);
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
}
