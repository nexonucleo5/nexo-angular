package com.nexo.config;

import com.nexo.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${nexo.cors.allowed-origins}")
    private List<String> allowedOrigins;

    /**
     * O index.html carrega Bootstrap/Bootstrap Icons via CDN (ver index.html) e Angular
     * injeta <style> por componente sem nonce — daí o 'unsafe-inline' em style-src e o
     * host do jsdelivr. Fora isso, tudo (JS, fotos, API, WebSocket) é same-origin.
     */
    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net",
            "font-src 'self' https://cdn.jsdelivr.net",
            "img-src 'self' data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'self'");

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
            // A API se autentica por header Bearer, que o navegador não anexa sozinho —
            // então não há superfície de CSRF nos endpoints protegidos. O único cookie do
            // sistema é o do refresh token, e ele é SameSite=Strict: requisição partida de
            // outro site não o carrega, logo /api/auth/refresh também não é forjável.
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin()) // h2-console
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                // CSP fora do /h2-console: o console do H2 usa inline script/style próprios
                // e só existe em dev — fica desligado em produção (spring.h2.console.enabled=false).
                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                        new NegatedRequestMatcher(
                                PathPatternRequestMatcher.withDefaults().matcher("/h2-console/**")),
                        new ContentSecurityPolicyHeaderWriter(CSP))))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(eh -> eh.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers("/h2-console/**", "/error").permitAll()
                .requestMatchers("/ws/**").permitAll() // handshake autenticado por ticket de uso único na query
                // <img src> não envia header Authorization; o id da foto é um UUID
                // aleatório, então não dá para enumerar as fotos dos usuários.
                .requestMatchers(HttpMethod.GET, "/api/fotos/*").permitAll()

                // API protegida; todo o resto (index.html, .js, .css, assets e as
                // rotas do Angular) é público — é o frontend servido pelo monolito
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        // O refresh token viaja em cookie HttpOnly; sem isto o navegador não o envia
        // para /api/auth/refresh quando a origem difere da API — que é o caso do
        // dev-server em :4200 falando com a API em :8080. Só é permitido porque
        // allowedOrigins é uma lista explícita (com "*" o navegador recusaria).
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
