package com.nexo.config;

import com.nexo.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
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
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${nexo.cors.allowed-origins}")
    private List<String> allowedOrigins;

    /**
     * O index.html carrega Bootstrap/Bootstrap Icons via CDN (ver index.html) e
     * Angular
     * injeta <style> por componente sem nonce — daí o 'unsafe-inline' em style-src
     * e o
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
                .cors(cors -> cors.disable())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin()) // h2-console
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // CSP fora do /h2-console: o console do H2 usa inline script/style próprios
                        // e só existe em dev — fica desligado em produção
                        // (spring.h2.console.enabled=false).
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
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins); // Usa a sua lista do application.properties
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(true); // Garante o tráfego do cookie do Refresh Token

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        // Registra o CorsFilter globalmente, FORA do Spring Security
        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));

        // A mágica acontece aqui: Prioridade máxima.
        // Ele intercepta a requisição antes de tudo e garante os cabeçalhos na saída,
        // mesmo que o Controller ou o Security lancem exceções.
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return bean;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
