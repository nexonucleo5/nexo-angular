package com.nexo.security;

import com.nexo.domain.Role;
import com.nexo.domain.Usuario;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * O filtro é a última barreira antes dos controllers. A validação de claims
 * aqui parece redundante com a assinatura do token, mas não é: assinatura
 * válida só prova a origem, não que o conteúdo faça sentido. Estes testes
 * existem para que ninguém remova essa checagem achando que é sobra.
 */
class JwtAuthFilterTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-caracteres-abcdef";

    private final JwtService jwtService = new JwtService(SEGREDO, 15);
    private final AccessTokensRevogados revogados = new AccessTokensRevogados(15);
    private final JwtAuthFilter filtro = new JwtAuthFilter(jwtService, revogados);

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void limparDepois() {
        SecurityContextHolder.clearContext();
    }

    private Authentication executar() throws Exception {
        filtro.doFilter(request, response, chain);
        // A cadeia segue sempre: quem barra o anônimo é o Spring Security, não o filtro.
        verify(chain).doFilter(request, response);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private void comBearer(String token) {
        request.addHeader("Authorization", "Bearer " + token);
    }

    /**
     * Monta um token assinado com a chave certa mas com as claims que eu quiser.
     *
     * <p>O {@code jti} entra sempre: o filtro também exige essa claim, e sem ela todo
     * teste daqui passaria por falta de jti em vez de pela claim que ele quer isolar.
     */
    private static String tokenAssinado(String subject, Object uid, Object role) {
        SecretKey key = Keys.hmacShaKeyFor(SEGREDO.getBytes(StandardCharsets.UTF_8));
        Instant agora = Instant.now();
        // Tipo explícito: com `var`, o .id() vem de um supertipo genérico do JJWT e a
        // inferência cai para Object, quebrando o encadeamento seguinte.
        JwtBuilder builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(15, ChronoUnit.MINUTES)));
        if (uid != null) builder.claim("uid", uid);
        if (role != null) builder.claim("role", role);
        return builder.signWith(key).compact();
    }

    @Test
    void tokenValidoAutenticaComOPapelDoUsuario() throws Exception {
        Usuario u = new Usuario();
        u.setId(7L);
        u.setLogin("carlos.diretor");
        u.setNome("Carlos");
        u.setRole(Role.DIRETOR);
        comBearer(jwtService.gerarAccessToken(u));

        Authentication auth = executar();

        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(UsuarioAutenticado.class);
        UsuarioAutenticado principal = (UsuarioAutenticado) auth.getPrincipal();
        assertThat(principal.id()).isEqualTo(7L);
        assertThat(principal.login()).isEqualTo("carlos.diretor");
        assertThat(principal.role()).isEqualTo("DIRETOR");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_DIRETOR");
    }

    @Test
    void requisicaoSemHeaderSegueAnonima() throws Exception {
        assertThat(executar()).isNull();
    }

    @Test
    void headerSemPrefixoBearerEhIgnorado() throws Exception {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setLogin("ana");
        u.setNome("Ana");
        u.setRole(Role.ALUNO);
        request.addHeader("Authorization", jwtService.gerarAccessToken(u)); // sem "Bearer "

        assertThat(executar()).isNull();
    }

    @Test
    void tokenDeOutraChaveNaoAutentica() throws Exception {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setLogin("ana");
        u.setNome("Ana");
        u.setRole(Role.ALUNO);
        comBearer(new JwtService("outro-segredo-completamente-diferente-0123456789", 15).gerarAccessToken(u));

        assertThat(executar()).isNull();
    }

    @Test
    void tokenExpiradoNaoAutentica() throws Exception {
        comBearer(JwtServiceTest.tokenExpirado(SEGREDO));

        assertThat(executar()).isNull();
    }

    // ── Claims incompletas: assinatura boa, conteúdo ruim ────────────────────

    @Test
    void tokenSemRoleNaoAutentica() throws Exception {
        // Sem esta checagem a authority viraria a string literal "ROLE_null".
        comBearer(tokenAssinado("ana", 1L, null));

        assertThat(executar()).isNull();
    }

    @Test
    void tokenSemUidNaoAutentica() throws Exception {
        // Sem esta checagem o principal teria id nulo e só quebraria num controller.
        comBearer(tokenAssinado("ana", null, "ALUNO"));

        assertThat(executar()).isNull();
    }

    @Test
    void tokenSemSubjectNaoAutentica() throws Exception {
        comBearer(tokenAssinado(null, 1L, "ALUNO"));

        assertThat(executar()).isNull();
    }

    @Test
    void tokenComSubjectEmBrancoNaoAutentica() throws Exception {
        comBearer(tokenAssinado("   ", 1L, "ALUNO"));

        assertThat(executar()).isNull();
    }

    @Test
    void roleForaDoEnumNaoAutentica() throws Exception {
        // Impede que uma role inventada vire authority e case com algum @PreAuthorize.
        comBearer(tokenAssinado("ana", 1L, "SUPERADMIN"));

        assertThat(executar()).isNull();
    }
}
