package com.nexo.service;

import com.nexo.api.ApiException;
import com.nexo.api.dto.AuthDtos.SessaoEmitida;
import com.nexo.api.dto.AuthDtos.TokenResponse;
import com.nexo.api.dto.AuthDtos.UsuarioDTO;
import com.nexo.domain.EventoAuditoria;
import com.nexo.domain.RefreshToken;
import com.nexo.domain.Usuario;
import com.nexo.repository.RefreshTokenRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private static final int MAX_TENTATIVAS = 5;
    private static final Duration JANELA_BLOQUEIO = Duration.ofMinutes(15);

    /**
     * A partir daqui uma nova falha dispara a varredura de entradas vencidas. A chave do
     * mapa é o login digitado — texto arbitrário vindo de fora —, então sem essa poda um
     * atacante enche o heap só errando logins diferentes; o mapa só encolhia no login
     * bem-sucedido ou quando a mesma chave era consultada de novo.
     */
    private static final int LIMITE_PODA = 1_000;

    private final UsuarioRepository usuarios;
    private final RefreshTokenRepository refreshTokens;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoria;
    private final Duration refreshTtl;

    /**
     * Hash descartável comparado quando o login não existe, para que a verificação de
     * senha custe o mesmo tempo nos dois casos (ver {@link #login}). É gerado de uma
     * senha aleatória no arranque: nenhuma senha real precisa casar com ele.
     */
    private final String hashFicticio;

    /** Controle de tentativas de login por usuário (bloqueio temporário após excesso). */
    private final Map<String, Tentativas> tentativasPorLogin = new ConcurrentHashMap<>();

    private record Tentativas(int quantidade, Instant primeiraFalha) {}

    public AuthService(UsuarioRepository usuarios,
                       RefreshTokenRepository refreshTokens,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       AuditoriaService auditoria,
                       @Value("${nexo.jwt.refresh-token-days}") long refreshTokenDays) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.auditoria = auditoria;
        this.refreshTtl = Duration.ofDays(refreshTokenDays);
        this.hashFicticio = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * {@code noRollbackFor}: o registro de auditoria da tentativa inválida é escrito e
     * logo em seguida a exceção sobe. Sem isso o Spring desfazia a transação junto com
     * a exceção e <b>nenhum login malsucedido chegava à trilha de auditoria</b> — a tela
     * do diretor mostrava só os acessos que deram certo.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public SessaoEmitida login(String login, String senha, String ip) {
        String chave = login.trim().toLowerCase();
        verificarBloqueio(chave);

        Usuario usuario = usuarios.findByLoginIgnoreCase(chave).orElse(null);

        // A verificação da senha roda sempre, inclusive para login inexistente ou inativo.
        // Antes o bcrypt era pulado nesses casos e a resposta voltava numa fração do tempo,
        // o que permitia descobrir quais logins existem só cronometrando as respostas.
        boolean senhaConfere = passwordEncoder.matches(
                senha, usuario != null ? usuario.getSenhaHash() : hashFicticio);

        if (usuario == null || !usuario.isAtivo() || !senhaConfere) {
            registrarFalha(chave);
            auditoria.registrar(chave, EventoAuditoria.Tipo.ERRO, "Tentativa de login inválida", null, ip);
            throw ApiException.unauthorized("Credenciais inválidas.");
        }

        tentativasPorLogin.remove(chave);
        auditoria.registrar(usuario.getNome(), EventoAuditoria.Tipo.LOGIN, "Login realizado", null, ip);
        return gerarTokens(usuario);
    }

    /** Ver {@link #login} — a revogação em cascata abaixo também precisa sobreviver ao throw. */
    @Transactional(noRollbackFor = ApiException.class)
    public SessaoEmitida refresh(String refreshToken) {
        RefreshToken atual = refreshTokens.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> ApiException.unauthorized("Refresh token inválido ou expirado."));

        // Reapresentação de um token já rotacionado: o legítimo dono já o trocou, então
        // quem está usando esta cópia a obteve de outro jeito. Como não dá para saber
        // qual das duas partes é a legítima, derruba a sessão inteira do usuário e força
        // um login novo. Antes o replay só levava 401 e o token roubado paralelo seguia
        // valendo pelos 7 dias restantes.
        if (atual.isRevogado()) {
            refreshTokens.revogarTodosDoUsuario(atual.getUsuario().getId());
            auditoria.registrar(atual.getUsuario().getNome(), EventoAuditoria.Tipo.ERRO,
                    "Refresh token reutilizado — sessões revogadas", null, null);
            throw ApiException.unauthorized("Sessão encerrada por segurança. Entre novamente.");
        }
        if (!atual.getExpiraEm().isAfter(Instant.now())) {
            throw ApiException.unauthorized("Refresh token inválido ou expirado.");
        }
        // Um usuário desativado mantinha acesso renovando o token por até 7 dias:
        // o vínculo só era checado no login.
        if (!atual.getUsuario().isAtivo()) {
            throw ApiException.unauthorized("Usuário inativo.");
        }

        // Rotação: o token usado é invalidado e um novo é emitido
        atual.setRevogado(true);
        refreshTokens.save(atual);
        return gerarTokens(atual.getUsuario());
    }

    /**
     * Encerra <b>apenas</b> a sessão que apresentou o refresh token — as outras continuam
     * de pé. Antes o logout chamava {@code deleteByUsuario} e derrubava todos os
     * dispositivos do usuário de uma vez: sair no celular deslogava o desktop no meio do
     * trabalho.
     *
     * <p>A linha é revogada, não apagada, para preservar o rastro de reuso: se a cópia
     * vazada desta sessão for reapresentada depois do logout, {@link #refresh} a reconhece
     * como token já revogado e dispara a cascata, em vez de tratá-la como desconhecida.
     *
     * <p>Sem cookie (sessão já encerrada, ou cliente que o perdeu) o logout ainda responde
     * normalmente e registra a auditoria — é idempotente de propósito.
     */
    @Transactional
    public void logout(Long usuarioId, String nome, String refreshToken, String ip) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokens.findByTokenHash(hash(refreshToken))
                    // Um cookie de outro usuário não encerra a sessão alheia.
                    .filter(t -> t.getUsuario().getId().equals(usuarioId))
                    .ifPresent(t -> {
                        t.setRevogado(true);
                        refreshTokens.save(t);
                    });
        }
        auditoria.registrar(nome, EventoAuditoria.Tipo.LOGOUT, "Logout realizado", null, ip);
    }

    public UsuarioDTO me(Long usuarioId) {
        return usuarios.findById(usuarioId)
                .map(UsuarioDTO::of)
                .orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));
    }

    private SessaoEmitida gerarTokens(Usuario usuario) {
        String access = jwtService.gerarAccessToken(usuario);

        // O valor em claro só existe aqui e no cookie enviado ao cliente — o banco
        // guarda o hash, e o corpo da resposta não carrega o refresh token.
        String refreshTokenPlano = UUID.randomUUID().toString();

        RefreshToken novo = new RefreshToken();
        novo.setTokenHash(hash(refreshTokenPlano));
        novo.setUsuario(usuario);
        novo.setExpiraEm(Instant.now().plus(refreshTtl));
        refreshTokens.save(novo);

        return new SessaoEmitida(new TokenResponse(access, UsuarioDTO.of(usuario)), refreshTokenPlano);
    }

    private static String hash(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }

    private void verificarBloqueio(String chave) {
        Tentativas t = tentativasPorLogin.get(chave);
        if (t == null) return;
        boolean dentroDaJanela = t.primeiraFalha().plus(JANELA_BLOQUEIO).isAfter(Instant.now());
        if (!dentroDaJanela) {
            tentativasPorLogin.remove(chave);
            return;
        }
        if (t.quantidade() >= MAX_TENTATIVAS) {
            long faltam = Duration.between(Instant.now(), t.primeiraFalha().plus(JANELA_BLOQUEIO)).toSeconds();
            throw ApiException.tooManyRequests("LOGIN_BLOQUEADO",
                    "Muitas tentativas de login. Tente novamente em alguns minutos.",
                    Math.max(1, faltam));
        }
    }

    private void registrarFalha(String chave) {
        if (tentativasPorLogin.size() >= LIMITE_PODA) {
            podarVencidas();
        }
        tentativasPorLogin.merge(chave,
                new Tentativas(1, Instant.now()),
                (antiga, ignorada) -> new Tentativas(antiga.quantidade() + 1, antiga.primeiraFalha()));
    }

    /** Remove as janelas já encerradas — elas não bloqueiam mais ninguém, só ocupam heap. */
    private void podarVencidas() {
        Instant limite = Instant.now().minus(JANELA_BLOQUEIO);
        tentativasPorLogin.values().removeIf(t -> t.primeiraFalha().isBefore(limite));
    }
}
