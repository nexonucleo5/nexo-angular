package com.nexo.service;

import com.nexo.api.ApiException;
import com.nexo.domain.EventoAuditoria;
import com.nexo.domain.RedefinicaoSenha;
import com.nexo.domain.Usuario;
import com.nexo.repository.RedefinicaoSenhaRepository;
import com.nexo.repository.RefreshTokenRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.JanelaDeTentativas;
import com.nexo.security.PoliticaSenha;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * Fluxo de "esqueci minha senha", no mesmo desenho do refresh token: valor aleatório
 * (256 bits), só o hash no banco, TTL curto, uso único, e ao concluir todas as sessões
 * do usuário caem.
 *
 * <p><b>Sem enumeração de contas.</b> {@link #solicitar} responde igual — e sem erro —
 * exista o login ou não, tenha a conta e-mail de recuperação ou não. Quem quer saber se
 * "maria.silva" existe não descobre por aqui. O que sobra de diferença é o tempo de uma
 * escrita no banco; o e-mail em si é enviado de forma assíncrona (ver {@link EnvioEmail}).
 */
@Service
public class RecuperacaoSenhaService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration JANELA = Duration.ofMinutes(15);

    private final UsuarioRepository usuarios;
    private final RedefinicaoSenhaRepository redefinicoes;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final PoliticaSenha politicaSenha;
    private final AuditoriaService auditoria;
    private final EnvioEmail email;
    private final Duration ttl;
    private final String urlBase;

    /** Pedidos por origem: cada pedido custa uma escrita e um e-mail, e o e-mail é de terceiro. */
    private final JanelaDeTentativas pedidosPorOrigem;
    /** Pedidos por conta: impede encher a caixa de entrada de uma pessoa. */
    private final JanelaDeTentativas pedidosPorLogin;
    /** Links inválidos por origem — o valor tem 256 bits, o limite existe só para frear o custo. */
    private final JanelaDeTentativas invalidosPorOrigem;

    public RecuperacaoSenhaService(UsuarioRepository usuarios,
                                   RedefinicaoSenhaRepository redefinicoes,
                                   RefreshTokenRepository refreshTokens,
                                   PasswordEncoder passwordEncoder,
                                   PoliticaSenha politicaSenha,
                                   AuditoriaService auditoria,
                                   EnvioEmail email,
                                   @Value("${nexo.recuperacao.ttl-minutos:30}") long ttlMinutos,
                                   @Value("${nexo.recuperacao.url-base:http://localhost:4200}") String urlBase,
                                   @Value("${nexo.recuperacao.max-pedidos-por-ip:10}") int maxPedidosPorIp,
                                   @Value("${nexo.recuperacao.max-pedidos-por-login:3}") int maxPedidosPorLogin) {
        this.usuarios = usuarios;
        this.redefinicoes = redefinicoes;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.politicaSenha = politicaSenha;
        this.auditoria = auditoria;
        this.email = email;
        this.ttl = Duration.ofMinutes(ttlMinutos);
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        this.pedidosPorOrigem = new JanelaDeTentativas(maxPedidosPorIp, JANELA);
        this.pedidosPorLogin = new JanelaDeTentativas(maxPedidosPorLogin, JANELA);
        this.invalidosPorOrigem = new JanelaDeTentativas(maxPedidosPorIp, JANELA);
    }

    /**
     * Pede o link. Nunca informa se a conta existe: o único erro possível é o 429, e ele
     * vale para qualquer texto digitado, existente ou não.
     */
    @Transactional
    public void solicitar(String loginDigitado, String ip) {
        String chave = loginDigitado.trim().toLowerCase();
        barrar(pedidosPorOrigem, ip, "RECUPERACAO_BLOQUEADA",
                "Muitos pedidos de redefinição vindos desta rede. Tente novamente em alguns minutos.");
        barrar(pedidosPorLogin, chave, "RECUPERACAO_BLOQUEADA",
                "Já enviamos pedidos demais para esta conta. Tente novamente em alguns minutos.");
        // Todo pedido conta, achando a conta ou não — senão o limite revelaria quais existem.
        pedidosPorOrigem.registrarFalha(ip);
        pedidosPorLogin.registrarFalha(chave);

        Usuario usuario = usuarios.findByLoginIgnoreCase(chave).orElse(null);
        if (usuario == null || !usuario.isAtivo() || usuario.getEmailRecuperacao() == null) {
            return;
        }

        redefinicoes.invalidarPendentes(usuario.getId());

        String valor = novoValor();
        RedefinicaoSenha pedido = new RedefinicaoSenha();
        pedido.setTokenHash(hash(valor));
        pedido.setUsuario(usuario);
        pedido.setExpiraEm(Instant.now().plus(ttl));
        redefinicoes.save(pedido);

        auditoria.registrar(usuario.getNome(), EventoAuditoria.Tipo.ALTERACAO,
                "Redefinição de senha solicitada", null, ip);

        String destino = usuario.getEmailRecuperacao();
        String corpo = montarCorpo(usuario.getNome(), urlBase + "/redefinir-senha?token=" + valor, ttl.toMinutes());
        // Só depois do commit: o link não pode chegar antes de o pedido existir no banco.
        aposCommit(() -> email.enviar(destino, "Redefinição de senha — Nexo", corpo));
    }

    /**
     * Conclui a redefinição. {@code noRollbackFor}: a contagem de links inválidos e a
     * auditoria precisam sobreviver ao 400.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void redefinir(String valor, String novaSenha, String ip) {
        barrar(invalidosPorOrigem, ip, "RECUPERACAO_BLOQUEADA",
                "Muitas tentativas inválidas vindas desta rede. Tente novamente em alguns minutos.");

        RedefinicaoSenha pedido = redefinicoes.findByTokenHash(hash(valor)).orElse(null);
        Instant agora = Instant.now();
        if (pedido == null || pedido.isUsado() || !pedido.getExpiraEm().isAfter(agora)) {
            invalidosPorOrigem.registrarFalha(ip);
            throw linkInvalido();
        }

        Usuario usuario = pedido.getUsuario();
        if (!usuario.isAtivo()) {
            invalidosPorOrigem.registrarFalha(ip);
            throw linkInvalido();
        }

        // Política antes de consumir o link: senha recusada não gasta o único uso.
        politicaSenha.validar(novaSenha, usuario.getLogin(), usuario.getNome());
        if (passwordEncoder.matches(novaSenha, usuario.getSenhaHash())) {
            final String motivo = "A nova senha é igual à atual. Escolha uma diferente.";
            throw new ApiException(HttpStatus.BAD_REQUEST, "SENHA_IGUAL_A_ATUAL", motivo,
                    Map.of("novaSenha", motivo));
        }

        // Uso único de verdade: se outra requisição já consumiu, 0 linhas e o link morre.
        if (redefinicoes.consumir(pedido.getId(), agora) == 0) {
            invalidosPorOrigem.registrarFalha(ip);
            throw linkInvalido();
        }

        usuario.setSenhaHash(passwordEncoder.encode(novaSenha));
        usuario.setPrecisaTrocarSenha(false);
        usuarios.save(usuario);

        redefinicoes.invalidarPendentes(usuario.getId());
        // Quem pediu a redefinição pode ser justamente quem perdeu o controle da conta:
        // todas as sessões caem, sem poupar nenhuma.
        int encerradas = refreshTokens.revogarTodosDoUsuario(usuario.getId());

        auditoria.registrar(usuario.getNome(), EventoAuditoria.Tipo.ALTERACAO, "Senha redefinida por link",
                encerradas > 0 ? encerradas + " sessão(ões) encerrada(s)" : null, ip);
    }

    /** Normaliza e valida o endereço; devolve {@code null} para "limpar o e-mail". */
    public static String normalizarEmail(String bruto) {
        if (bruto == null || bruto.isBlank()) return null;
        String email = bruto.trim().toLowerCase();
        if (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")) {
            throw ApiException.validation("E-mail inválido.",
                    Map.of("emailRecuperacao", "Informe um e-mail válido."));
        }
        return email;
    }

    private static ApiException linkInvalido() {
        return new ApiException(HttpStatus.BAD_REQUEST, "LINK_INVALIDO",
                "Este link é inválido ou expirou. Peça um novo.");
    }

    private static String montarCorpo(String nome, String link, long minutos) {
        return "Olá, " + nome + ".\n\n"
                + "Recebemos um pedido para redefinir a senha da sua conta no Nexo. Para escolher uma senha nova, "
                + "abra o link abaixo (vale por " + minutos + " minutos e só pode ser usado uma vez):\n\n"
                + link + "\n\n"
                + "Se você não fez esse pedido, ignore esta mensagem: sua senha continua a mesma.";
    }

    private static String novoValor() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }

    private static void barrar(JanelaDeTentativas janela, String chave, String codigo, String mensagem) {
        long faltam = janela.segundosDeBloqueio(chave);
        if (faltam > 0) {
            throw ApiException.tooManyRequests(codigo, mensagem, faltam);
        }
    }

    private static void aposCommit(Runnable acao) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    acao.run();
                }
            });
        } else {
            acao.run();
        }
    }
}
