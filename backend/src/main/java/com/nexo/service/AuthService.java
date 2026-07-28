package com.nexo.service;

import com.nexo.api.ApiException;
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

    private final UsuarioRepository usuarios;
    private final RefreshTokenRepository refreshTokens;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoria;
    private final Duration refreshTtl;

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
    }

    @Transactional
    public TokenResponse login(String login, String senha, String ip) {
        String chave = login.trim().toLowerCase();
        verificarBloqueio(chave);

        Usuario usuario = usuarios.findByLoginIgnoreCase(chave)
                .filter(Usuario::isAtivo)
                .filter(u -> passwordEncoder.matches(senha, u.getSenhaHash()))
                .orElseThrow(() -> {
                    registrarFalha(chave);
                    auditoria.registrar(chave, EventoAuditoria.Tipo.ERRO, "Tentativa de login inválida", null, ip);
                    return ApiException.unauthorized("Credenciais inválidas.");
                });

        tentativasPorLogin.remove(chave);
        auditoria.registrar(usuario.getNome(), EventoAuditoria.Tipo.LOGIN, "Login realizado", null, ip);
        return gerarTokens(usuario);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        RefreshToken atual = refreshTokens.findByTokenHash(hash(refreshToken))
                .filter(t -> !t.isRevogado())
                .filter(t -> t.getExpiraEm().isAfter(Instant.now()))
                .orElseThrow(() -> ApiException.unauthorized("Refresh token inválido ou expirado."));

        // Rotação: o token usado é invalidado e um novo é emitido
        atual.setRevogado(true);
        refreshTokens.save(atual);
        return gerarTokens(atual.getUsuario());
    }

    @Transactional
    public void logout(Long usuarioId, String nome, String ip) {
        usuarios.findById(usuarioId).ifPresent(refreshTokens::deleteByUsuario);
        auditoria.registrar(nome, EventoAuditoria.Tipo.LOGOUT, "Logout realizado", null, ip);
    }

    public UsuarioDTO me(Long usuarioId) {
        return usuarios.findById(usuarioId)
                .map(UsuarioDTO::of)
                .orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));
    }

    private TokenResponse gerarTokens(Usuario usuario) {
        String access = jwtService.gerarAccessToken(usuario);

        // O valor em claro só existe aqui e na resposta ao cliente — o banco guarda o hash.
        String refreshTokenPlano = UUID.randomUUID().toString();

        RefreshToken novo = new RefreshToken();
        novo.setTokenHash(hash(refreshTokenPlano));
        novo.setUsuario(usuario);
        novo.setExpiraEm(Instant.now().plus(refreshTtl));
        refreshTokens.save(novo);

        return new TokenResponse(access, refreshTokenPlano, UsuarioDTO.of(usuario));
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
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "LOGIN_BLOQUEADO", "Muitas tentativas de login. Tente novamente em alguns minutos.");
        }
    }

    private void registrarFalha(String chave) {
        tentativasPorLogin.merge(chave,
                new Tentativas(1, Instant.now()),
                (antiga, ignorada) -> new Tentativas(antiga.quantidade() + 1, antiga.primeiraFalha()));
    }
}
