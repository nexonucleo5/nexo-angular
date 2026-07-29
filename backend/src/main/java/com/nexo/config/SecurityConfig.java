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
     * A liberação do /h2-console segue a mesma flag que liga o console. Antes o
     * permitAll era incondicional e só não vazava porque o perfil de produção
     * desliga o console em outro arquivo — bastava mexer no yml errado pra abrir.
     */
    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleHabilitado;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())) // h2-console
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(eh -> eh.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> {
                // Com o console desligado o path é negado explicitamente: sem isso ele
                // cairia no anyRequest().permitAll() lá embaixo e a flag não teria efeito
                // nenhum sobre a autorização.
                if (h2ConsoleHabilitado) {
                    auth.requestMatchers("/h2-console/**").permitAll();
                } else {
                    auth.requestMatchers("/h2-console/**").denyAll();
                }

                auth.requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/ws/**").permitAll() // handshake autenticado por ticket de uso único na query
                    // <img src> não envia header Authorization; o id da foto é um UUID
                    // aleatório, então não dá para enumerar as fotos dos usuários.
                    .requestMatchers(HttpMethod.GET, "/api/fotos/*").permitAll()

                    // API protegida; todo o resto (index.html, .js, .css, assets e as
                    // rotas do Angular) é público — é o frontend servido pelo monolito
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll();
            })
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

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
