package com.nexo.security;

import com.nexo.domain.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /**
     * O default de {@code application.yml}. Está versionado no repositório, então quem
     * o conhece assina qualquer token: dá para forjar um acesso de DIRETOR sem senha.
     */
    private static final String SEGREDO_DEV =
            "nexo-gestao-escolar-chave-de-desenvolvimento-nao-usar-em-producao-0123456789";

    private final SecretKey key;
    private final Duration accessTokenTtl;

    public JwtService(@Value("${nexo.jwt.secret}") String secret,
                      @Value("${nexo.jwt.access-token-minutes}") long accessTokenMinutes,
                      Environment environment) {
        // Antes a aplicação subia calada com a chave de desenvolvimento se
        // NEXO_JWT_SECRET não estivesse definida — falha silenciosa e total da
        // autenticação. Agora ela recusa arrancar no perfil de produção e avisa alto
        // fora dele.
        if (SEGREDO_DEV.equals(secret)) {
            if (List.of(environment.getActiveProfiles()).contains("prod")) {
                throw new IllegalStateException(
                        "nexo.jwt.secret está com a chave de desenvolvimento no perfil prod. "
                        + "Defina a variável de ambiente NEXO_JWT_SECRET com um segredo próprio.");
            }
            log.warn("Usando a chave JWT de desenvolvimento (pública no repositório). "
                    + "Defina NEXO_JWT_SECRET antes de expor esta instância.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(accessTokenMinutes);
    }

    public String gerarAccessToken(Usuario usuario) {
        Instant agora = Instant.now();
        return Jwts.builder()
                // jti: identidade própria deste token, para que o logout consiga invalidar
                // esta emissão específica sem tocar nas outras sessões do mesmo usuário
                // (ver AccessTokensRevogados).
                .id(UUID.randomUUID().toString())
                .subject(usuario.getLogin())
                .claim("uid", usuario.getId())
                .claim("role", usuario.getRole().name())
                .claim("nome", usuario.getNome())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    /** Retorna as claims se o token for válido e não expirado; vazio caso contrário. */
    public Optional<Claims> validar(String token) {
        try {
            return Optional.of(Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
