package com.nexo;

import com.nexo.domain.Role;
import com.nexo.domain.Usuario;
import com.nexo.repository.UsuarioRepository;
import com.nexo.service.EnviadorDeEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recuperação de senha ponta a ponta, do pedido ao login com a senha nova.
 *
 * <p>O {@link EnviadorDeEmail} é substituído por um dublê para que o teste consiga ler o
 * link que sairia na mensagem — é a única forma de obter o token, já que o banco guarda
 * apenas o hash dele (que é justamente a propriedade que se quer preservar).
 *
 * <p>Cada teste cria a <b>própria conta</b>. Não é preciosismo: o serviço limita pedidos por
 * conta por hora, e esse contador vive no bean, que é o mesmo para a classe inteira.
 * Reaproveitando um login, os últimos testes esbarrariam no limite deixado pelos primeiros
 * e falhariam por um motivo que não tem nada a ver com o que eles verificam.
 */
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:teste-recuperacao;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "nexo.seed.enabled=true"
})
class RecuperacaoSenhaTest extends TesteApiBase {

    private static final String SENHA_NOVA = "senha-nova-bem-longa";
    private static final Pattern TOKEN_NO_LINK = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private static final AtomicInteger SEQUENCIA = new AtomicInteger();

    @MockitoBean
    private EnviadorDeEmail email;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private com.nexo.repository.MateriaRepository materias;

    /** O cadastro de docente exige ao menos uma matéria existente; usa a primeira do seed. */
    private Long algumaMateria() {
        return materias.findAll().getFirst().getId();
    }

    private String login;
    private String contato;

    @BeforeEach
    void criarContaDoTeste() {
        reset(email);

        int n = SEQUENCIA.incrementAndGet();
        login = "professor.teste" + n + "@nexo.escola.com";
        contato = "responsavel" + n + "@exemplo.com";

        Usuario usuario = new Usuario();
        usuario.setLogin(login);
        usuario.setNome("Professor de Teste " + n);
        usuario.setRole(Role.PROFESSOR);
        usuario.setSenhaHash(encoder.encode(SENHA_PADRAO));
        usuario.setEmailContato(contato);
        usuarios.save(usuario);
    }

    // ── Pedido ───────────────────────────────────────────────────────────────

    @Test
    void enviaOLinkParaOEnderecoDeContato() throws Exception {
        pedir(login);

        verify(email, atLeastOnce()).enviar(eq(contato), anyString(), anyString());
    }

    /**
     * Endpoint público respondendo "usuário não encontrado" entregaria, a quem perguntasse,
     * a lista de quem estuda e trabalha na escola. A resposta é a mesma nos dois casos, e a
     * diferença fica só no que acontece (ou não) depois dela.
     */
    @Test
    void naoRevelaSeOLoginExiste() throws Exception {
        pedir("fantasma-que-nao-existe");

        verify(email, never()).enviar(anyString(), anyString(), anyString());
    }

    /** Conta real sem endereço de contato também responde igual — e nada é enviado. */
    @Test
    void naoEnviaNadaParaContaSemEmailDeContato() throws Exception {
        Usuario usuario = usuarios.findByLoginIgnoreCase(login).orElseThrow();
        usuario.setEmailContato(null);
        usuarios.save(usuario);

        pedir(login);

        verify(email, never()).enviar(anyString(), anyString(), anyString());
    }

    // ── Redefinição ──────────────────────────────────────────────────────────

    @Test
    void redefineASenhaEDerrubaAAntiga() throws Exception {
        redefinirCom(tokenDoEmail()).andExpect(status().isNoContent());

        assertThat(loginHttp(login, SENHA_PADRAO).getStatus())
                .as("a senha antiga deixa de valer")
                .isEqualTo(401);
        assertThat(loginHttp(login, SENHA_NOVA).getStatus()).isEqualTo(200);
    }

    @Test
    void oTokenSoServeUmaVez() throws Exception {
        String token = tokenDoEmail();
        redefinirCom(token).andExpect(status().isNoContent());

        redefinirCom(token).andExpect(status().isBadRequest());
    }

    /**
     * Pedir um link novo aposenta o anterior: senão um e-mail de recuperação esquecido na
     * caixa de entrada continua valendo pelos 30 minutos dele.
     */
    @Test
    void pedirUmLinkNovoInvalidaOAnterior() throws Exception {
        String primeiro = tokenDoEmail();
        String segundo = tokenDoEmail();

        assertThat(primeiro).isNotEqualTo(segundo);
        redefinirCom(primeiro).andExpect(status().isBadRequest());
        redefinirCom(segundo).andExpect(status().isNoContent());
    }

    @Test
    void tokenDesconhecidoNaoRedefineNada() throws Exception {
        redefinirCom("token-que-nunca-existiu").andExpect(status().isBadRequest());

        assertThat(loginHttp(login, SENHA_PADRAO).getStatus())
                .as("a senha continua a mesma")
                .isEqualTo(200);
    }

    /**
     * Quem redefine a senha ou esqueceu, ou desconfia que alguém entrou. Nos dois casos a
     * sessão que estiver de pé precisa cair — inclusive a de quem não deveria estar lá.
     */
    @Test
    void redefinirEncerraAsSessoesJaAbertas() throws Exception {
        MockHttpServletResponse sessao = loginHttp(login, SENHA_PADRAO);

        redefinirCom(tokenDoEmail()).andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/refresh").cookie(cookieDeRefresh(sessao)))
                .andExpect(status().isUnauthorized());
    }

    // ── Primeiro acesso ──────────────────────────────────────────────────────

    /**
     * O cadastro feito pelo diretor manda um convite com <b>link</b>, e não com a senha
     * provisória. Senha em texto claro num e-mail fica na caixa de entrada para sempre e
     * continua valendo, porque ninguém troca uma senha que está funcionando.
     *
     * <p>O teste segue o caminho inteiro: o diretor cadastra, o convite sai, o link define a
     * senha, e o docente entra com ela — sem a senha provisória ter saído do servidor.
     */
    @Test
    void cadastroEnviaConviteDePrimeiroAcessoEOLinkFunciona() throws Exception {
        String contatoNovo = "novo.professor@exemplo.com";

        String resposta = mvc.perform(post("/api/professores")
                        .header("Authorization", bearer("diretor"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Marina Torres","dataNascimento":"1990-04-12","sexo":"F",
                                 "materiaIds":[%d],"emailContato":"%s"}
                                """.formatted(algumaMateria(), contatoNovo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String loginCriado = json.readTree(resposta).get("emailInstitucional").asText();
        String senhaProvisoria = json.readTree(resposta).get("senhaProvisoria").asText();

        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        verify(email, atLeastOnce()).enviar(eq(contatoNovo), anyString(), corpo.capture());
        String convite = corpo.getAllValues().getLast();

        assertThat(convite)
                .as("o convite traz o login, mas nunca a senha provisória")
                .contains(loginCriado)
                .doesNotContain(senhaProvisoria);

        Matcher achado = TOKEN_NO_LINK.matcher(convite);
        assertThat(achado.find()).isTrue();
        redefinirCom(achado.group(1)).andExpect(status().isNoContent());

        assertThat(loginHttp(loginCriado, SENHA_NOVA).getStatus())
                .as("o docente entra com a senha que ele mesmo escolheu")
                .isEqualTo(200);
    }

    /** Sem endereço de contato o cadastro segue igual — só não há convite para mandar. */
    @Test
    void cadastroSemEmailDeContatoNaoTentaEnviarNada() throws Exception {
        mvc.perform(post("/api/professores")
                        .header("Authorization", bearer("diretor"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Paulo Ramos","dataNascimento":"1988-09-03","sexo":"M","materiaIds":[%d]}
                                """.formatted(algumaMateria())))
                .andExpect(status().isCreated());

        verify(email, never()).enviar(anyString(), anyString(), anyString());
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    /** O pedido em si. Responde 204 sempre — é o ponto do fluxo. */
    private void pedir(String qualLogin) throws Exception {
        mvc.perform(post("/api/auth/senha/esqueci")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"" + qualLogin + "\"}"))
                .andExpect(status().isNoContent());
    }

    /** Pede o link e extrai do corpo do e-mail o token que o usuário receberia. */
    private String tokenDoEmail() throws Exception {
        pedir(login);

        ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
        verify(email, atLeastOnce()).enviar(eq(contato), anyString(), corpo.capture());

        Matcher achado = TOKEN_NO_LINK.matcher(corpo.getAllValues().getLast());
        assertThat(achado.find()).as("o corpo do e-mail traz o link com o token").isTrue();
        return achado.group(1);
    }

    private ResultActions redefinirCom(String token) throws Exception {
        return mvc.perform(post("/api/auth/senha/redefinir")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"novaSenha\":\"" + SENHA_NOVA + "\"}"));
    }
}
