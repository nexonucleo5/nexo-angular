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
            jwtService.validar(token).ifPresent(this::autenticar);
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Só popula o SecurityContext se as claims estiverem completas e coerentes.
     *
     * A assinatura válida garante que o token saiu daqui, não que o conteúdo
     * faça sentido: um token sem "role" produziria a authority literal
     * "ROLE_null", e um sem "uid" daria um principal com id nulo que só
     * quebraria lá na frente, dentro de algum controller. Sem claim válida,
     * a requisição segue anônima e o Spring Security devolve 401.
     */
    private void autenticar(Claims claims) {
        String login = claims.getSubject();
        String role = claims.get("role", String.class);
        Long uid = claims.get("uid", Long.class);

        if (login == null || login.isBlank() || uid == null || !roleConhecida(role)) {
            return;
        }

        var principal = new UsuarioAutenticado(uid, login, claims.get("nome", String.class), role);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** A role precisa ser uma das do enum — não basta ser uma string qualquer. */
    private boolean roleConhecida(String role) {
        if (role == null) return false;
        for (Role conhecida : Role.values()) {
            if (conhecida.name().equals(role)) return true;
        }
        return false;
    }
}
