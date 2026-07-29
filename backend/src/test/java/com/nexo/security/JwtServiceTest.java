package com.nexo.security;

import com.nexo.domain.Role;
import com.nexo.domain.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testes puros: sem contexto Spring, sem banco. */
class JwtServiceTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-caracteres-abcdef";
    private static final String OUTRO_SEGREDO = "outro-segredo-completamente-diferente-0123456789";

    private final JwtService jwtService = new JwtService(SEGREDO, 15);

    private static Usuario usuario() {
        Usuario u = new Usuario();
        u.setId(42L);
        u.setLogin("ana.professora");
        u.setNome("Ana");
        u.setRole(Role.PROFESSOR);
        return u;
    }

    @Test
    void tokenValidoCarregaAsClaims() {
        String token = jwtService.gerarAccessToken(usuario());

        Optional<Claims> claims = jwtService.validar(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("ana.professora");
        assertThat(claims.get().get("uid", Long.class)).isEqualTo(42L);
        assertThat(claims.get().get("role", String.class)).isEqualTo("PROFESSOR");
        assertThat(claims.get().get("nome", String.class)).isEqualTo("Ana");
    }

    @Test
    void tokenAssinadoComOutraChaveEhRejeitado() {
        // Mesmo payload, chave diferente: a assinatura não confere.
        String token = new JwtService(OUTRO_SEGREDO, 15).gerarAccessToken(usuario());

        assertThat(jwtService.validar(token)).isEmpty();
    }

    @Test
    void tokenExpiradoEhRejeitado() {
        assertThat(jwtService.validar(tokenExpirado(SEGREDO))).isEmpty();
    }

    @Test
    void tokenAdulteradoEhRejeitado() {
        String token = jwtService.gerarAccessToken(usuario());
        // Troca um caractere do payload sem reassinar.
        String[] partes = token.split("\\.");
        String payloadAdulterado = partes[1].substring(0, partes[1].length() - 2)
                + (partes[1].endsWith("A") ? "B" : "A");

        assertThat(jwtService.validar(partes[0] + "." + payloadAdulterado + "." + partes[2])).isEmpty();
    }

    @Test
    void lixoNoLugarDoTokenNaoExplode() {
        assertThat(jwtService.validar("nem-parece-um-jwt")).isEmpty();
        assertThat(jwtService.validar("")).isEmpty();
    }

    @Test
    void segredoCurtoDerrubaAConstrucao() {
        // Sem esta checagem o app subiria com uma chave fraca demais para HMAC-SHA.
        assertThatThrownBy(() -> new JwtService("curto-demais", 15))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nexo.jwt.secret")
                .hasMessageContaining("32");
    }

    @Test
    void segredoAusenteDerrubaAConstrucao() {
        assertThatThrownBy(() -> new JwtService(null, 15))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Gera um token já vencido — o JwtService só emite tokens no futuro. */
    static String tokenExpirado(String segredo) {
        SecretKey key = Keys.hmacShaKeyFor(segredo.getBytes(StandardCharsets.UTF_8));
        Instant ontem = Instant.now().minus(1, ChronoUnit.DAYS);
        return Jwts.builder()
                .subject("ana.professora")
                .claim("uid", 42L)
                .claim("role", "PROFESSOR")
                .claim("nome", "Ana")
                .issuedAt(Date.from(ontem))
                .expiration(Date.from(ontem.plus(15, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }
}
