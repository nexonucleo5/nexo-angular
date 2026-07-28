package com.nexo.security;

import com.nexo.domain.Role;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            jwtService.validar(token).ifPresent(claims -> autenticar(claims, request));
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Só autentica com o conjunto completo de claims esperado, e com um papel que existe
     * de fato em {@link Role}. Sem essa checagem, um token assinado mas malformado passava
     * adiante com {@code uid} nulo (quebrando toda regra que compara o dono do recurso)
     * ou com a autoridade literal {@code ROLE_null}. Faltando qualquer claim, a requisição
     * segue sem autenticação e o endpoint protegido responde 401.
     */
    private void autenticar(Claims claims, HttpServletRequest request) {
        String login = claims.getSubject();
        String role = claims.get("role", String.class);
        Long uid = claims.get("uid", Long.class);
        if (login == null || uid == null || !papelConhecido(role)) return;

        var principal = new UsuarioAutenticado(uid, login, claims.get("nome", String.class), role);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static boolean papelConhecido(String role) {
        if (role == null) return false;
        for (Role r : Role.values()) {
            if (r.name().equals(role)) return true;
        }
        return false;
    }
}
