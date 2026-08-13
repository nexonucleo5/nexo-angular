package com.nexo.service;

import com.nexo.api.ApiException;
import com.nexo.domain.EventoAuditoria;
import com.nexo.domain.TokenRecuperacao;
import com.nexo.domain.Usuario;
import com.nexo.repository.RefreshTokenRepository;
import com.nexo.repository.TokenRecuperacaoRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.HashDeToken;
import com.nexo.security.JanelaDeTentativas;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Redefinição de senha por quem não consegue entrar.
 *
 * <p>Antes disto, aluno que esquecia a senha dependia do diretor gerar outra e repassá-la
 * na mão — o que, com centenas de alunos, é o maior gerador de trabalho manual do sistema,
 * e um caminho em que a senha nova trafega por WhatsApp.
 *
 * <p>As decisões que importam aqui:
 *
 * <ul>
 *   <li><b>A resposta nunca diz se a conta existe.</b> {@link #solicitar} responde igual
 *       para login cadastrado, login inexistente e conta sem e-mail de contato. Um endpoint
 *       público que respondesse "usuário não encontrado" entregaria a lista de quem estuda
 *       e trabalha na escola a quem perguntasse.</li>
 *   <li><b>O banco guarda o hash do token</b>, como já é feito com o refresh token.</li>
 *   <li><b>Pedir um link novo aposenta o anterior</b> — senão um e-mail antigo esquecido na
 *       caixa de entrada continua valendo.</li>
 *   <li><b>Redefinir encerra todas as sessões.</b> Quem redefine a senha ou esqueceu, ou
 *       desconfia que alguém entrou; nos dois casos as sessões abertas precisam cair.</li>
 * </ul>
 */
@Service
public class RecuperacaoSenhaService {

    /** Curto porque o link chega por e-mail e o e-mail fica guardado. */
    private static final Duration VALIDADE = Duration.ofMinutes(30);

    /**
     * O link de primeiro acesso vale bem mais: quem o recebe é responsável por aluno recém
     * matriculado, pode abrir a mensagem no fim de semana, e não tem senha nenhuma para
     * pedir um link novo caso o primeiro vença. Trinta minutos aqui seria uma armadilha.
     */
    private static final Duration VALIDADE_PRIMEIRO_ACESSO = Duration.ofDays(7);

    /** 32 bytes de aleatoriedade: não há como adivinhar nem enumerar. */
    private static final int BYTES_DO_TOKEN = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Pedidos tolerados por conta numa hora. Sem teto, o endpoint vira ferramenta de
     * importunação: qualquer um dispara centenas de e-mails para a caixa de outra pessoa, e
     * a escola aparece como remetente de spam para o provedor dela.
     *
     * <p>Cinco, e não menos: quem não recebeu a mensagem tende a pedir de novo — o e-mail
     * caiu no spam, o endereço cadastrado está errado, a pessoa desistiu e voltou depois.
     * Um teto apertado demais transformaria essa insistência normal em porta fechada.
     */
    private static final int MAX_PEDIDOS_POR_CONTA = 5;

    /**
     * O mesmo por origem, somando todas as contas. Folgado pelo motivo de sempre neste
     * sistema: escola sai por um IP só, e no primeiro dia de aula muita gente esquece a
     * senha ao mesmo tempo. Cem por hora é ruído alto para um atacante e teto distante para
     * uma escola inteira — o limite por conta acima é quem faz o trabalho fino.
     */
    private static final int MAX_PEDIDOS_POR_ORIGEM = 100;

    private static final Duration JANELA_PEDIDOS = Duration.ofHours(1);

    private final UsuarioRepository usuarios;
    private final TokenRecuperacaoRepository tokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final EnviadorDeEmail email;
    private final AuditoriaService auditoria;
    private final String urlBase;

    private final JanelaDeTentativas pedidosPorConta =
            new JanelaDeTentativas(MAX_PEDIDOS_POR_CONTA, JANELA_PEDIDOS);
    private final JanelaDeTentativas pedidosPorOrigem =
            new JanelaDeTentativas(MAX_PEDIDOS_POR_ORIGEM, JANELA_PEDIDOS);

    public RecuperacaoSenhaService(UsuarioRepository usuarios,
                                   TokenRecuperacaoRepository tokens,
                                   RefreshTokenRepository refreshTokens,
                                   PasswordEncoder passwordEncoder,
                                   EnviadorDeEmail email,
                                   AuditoriaService auditoria,
                                   @Value("${nexo.email.url-base}") String urlBase) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.auditoria = auditoria;
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
    }

    /**
     * Dispara o e-mail com o link, <b>se</b> houver conta ativa com endereço de contato.
     * Não devolve nada e não sinaliza nada: quem chamou recebe a mesma resposta nos três
     * casos possíveis.
     */
    @Transactional
    public void solicitar(String login, String ip) {
        String chave = login == null ? "" : login.trim().toLowerCase();
        String origem = ip == null || ip.isBlank() ? "origem-desconhecida" : ip;

        // O teto de origem é o único que pode recusar em voz alta: ele não revela nada
        // sobre conta nenhuma, e calar aqui só faria o atacante repetir mais rápido.
        long faltamOrigem = pedidosPorOrigem.bloqueioRestante(origem);
        if (faltamOrigem > 0) {
            throw ApiException.tooManyRequests("MUITAS_REQUISICOES",
                    "Muitos pedidos de recuperação. Tente novamente mais tarde.", faltamOrigem);
        }
        pedidosPorOrigem.registrar(origem);

        // Daqui para baixo nada pode virar resposta diferente: o teto por conta é aplicado
        // em silêncio, porque "esta conta pediu demais" já contaria que ela existe.
        if (pedidosPorConta.bloqueioRestante(chave) > 0) return;

        Usuario usuario = usuarios.findByLoginIgnoreCase(chave).orElse(null);
        if (usuario == null || !usuario.isAtivo()) return;

        String destino = usuario.getEmailContato();
        if (destino == null || destino.isBlank()) {
            // Conta real sem endereço de contato: não há para onde mandar. Fica registrado
            // para o diretor entender por que a pessoa diz que não recebeu nada.
            auditoria.registrar(usuario.getNome(), EventoAuditoria.Tipo.ERRO,
                    "Recuperação de senha pedida sem e-mail de contato cadastrado", null, ip);
            return;
        }

        pedidosPorConta.registrar(chave);
        String valor = emitirToken(usuario, VALIDADE);

        email.enviar(destino, "Nexo — redefinição de senha",
                corpoDeRedefinicao(usuario.getNome(), valor, VALIDADE));
        auditoria.registrar(usuario.getNome(), EventoAuditoria.Tipo.ALTERACAO,
                "Link de redefinição de senha enviado", null, ip);
    }

    /**
     * Convite de primeiro acesso, disparado quando o diretor cadastra aluno ou professor.
     *
     * <p>De propósito manda um <b>link</b>, e não a senha provisória que o cadastro gera.
     * Senha em texto claro num e-mail fica na caixa de entrada para sempre, é indexada por
     * cliente de e-mail e vaza junto se aquela conta for comprometida — e continuaria valendo,
     * porque ninguém troca uma senha que está funcionando. Com o link, o que trafega serve uma
     * vez só, e a senha que passa a valer nunca existiu fora da cabeça de quem a escolheu.
     *
     * <p>A senha provisória continua sendo devolvida ao diretor na resposta do cadastro: é o
     * caminho para quem não tem endereço de contato, e o de sempre quando o e-mail não chega.
     */
    @Transactional
    public void enviarPrimeiroAcesso(Usuario usuario) {
        String destino = usuario.getEmailContato();
        if (destino == null || destino.isBlank()) return;

        String valor = emitirToken(usuario, VALIDADE_PRIMEIRO_ACESSO);
        email.enviar(destino, "Nexo — seu acesso ao sistema da escola",
                corpoDePrimeiroAcesso(usuario.getNome(), usuario.getLogin(), valor));
    }

    /** Emite o token e aposenta os pendentes do usuário. Devolve o valor em claro. */
    private String emitirToken(Usuario usuario, Duration validade) {
        tokens.invalidarPendentesDoUsuario(usuario.getId(), Instant.now());

        String valor = gerarValor();
        TokenRecuperacao token = new TokenRecuperacao();
        token.setTokenHash(HashDeToken.de(valor));
        token.setUsuario(usuario);
        token.setExpiraEm(Instant.now().plus(validade));
        tokens.save(token);
        return valor;
    }

    /** Consome o token e troca a senha. Encerra todas as sessões abertas do usuário. */
    @Transactional
    public void redefinir(String valor, String novaSenha, String ip) {
        TokenRecuperacao token = tokens.findByTokenHash(HashDeToken.de(valor == null ? "" : valor))
                .orElseThrow(RecuperacaoSenhaService::linkInvalido);

        // Token já gasto reaparecendo é sinal de que o e-mail foi lido por quem não devia —
        // ao contrário de um token desconhecido, que é só um link velho ou digitado errado.
        if (token.isUsado()) {
            auditoria.registrar(token.getUsuario().getNome(), EventoAuditoria.Tipo.ERRO,
                    "Link de redefinição reapresentado depois de usado", null, ip);
            throw linkInvalido();
        }
        if (token.isExpirado() || !token.getUsuario().isAtivo()) {
            throw linkInvalido();
        }

        Usuario usuario = token.getUsuario();
        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuarios.save(usuario);

        token.setUsadoEm(Instant.now());
        tokens.save(token);

        // Quem redefine a senha ou esqueceu, ou desconfia que alguém entrou. Nos dois casos
        // as sessões que estiverem de pé precisam cair — inclusive a de quem não deveria
        // estar lá. O access token de até 15 minutos é o resíduo aceito aqui.
        refreshTokens.revogarTodosDoUsuario(usuario.getId());

        auditoria.registrar(usuario.getNome(), EventoAuditoria.Tipo.ALTERACAO,
                "Senha redefinida por link de recuperação", null, ip);
    }

    /**
     * A mesma exceção para token inexistente, vencido, já usado ou de conta inativa. Separar
     * os casos diria a quem tem um link velho se a conta continua existindo.
     */
    private static ApiException linkInvalido() {
        return ApiException.badRequest("Link de redefinição inválido ou expirado. Peça um novo.");
    }

    private static String gerarValor() {
        byte[] bytes = new byte[BYTES_DO_TOKEN];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String corpoDeRedefinicao(String nome, String valor, Duration validade) {
        return """
                Olá, %s.

                Recebemos um pedido para redefinir a sua senha do Nexo. Para escolher uma
                senha nova, acesse o endereço abaixo:

                %s

                O link vale por %d minutos e só pode ser usado uma vez.

                Se não foi você que pediu, ignore esta mensagem: a sua senha continua a
                mesma, e ninguém consegue trocá-la sem este link.
                """.formatted(nome, link(valor), validade.toMinutes());
    }

    private String corpoDePrimeiroAcesso(String nome, String login, String valor) {
        return """
                Olá, %s.

                A sua conta no Nexo, o sistema da escola, foi criada. O seu login é:

                %s

                Para escolher a sua senha e entrar pela primeira vez, acesse:

                %s

                O link vale por %d dias e só pode ser usado uma vez. Depois disso, se ainda
                não tiver entrado, peça uma nova senha na tela de acesso ou fale com a
                secretaria da escola.
                """.formatted(nome, login, link(valor), VALIDADE_PRIMEIRO_ACESSO.toDays());
    }

    private String link(String valor) {
        return urlBase + "/redefinir-senha?token=" + valor;
    }
}
