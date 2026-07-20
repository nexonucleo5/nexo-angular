package com.nexo.web;

import com.nexo.api.dto.AuthDtos.LoginRequest;
import com.nexo.api.dto.AuthDtos.RefreshRequest;
import com.nexo.api.dto.AuthDtos.TokenResponse;
import com.nexo.api.dto.AuthDtos.UsuarioDTO;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request.login(), request.senha(), http.getRemoteAddr());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UsuarioAutenticado usuario, HttpServletRequest http) {
        authService.logout(usuario.id(), usuario.nome(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UsuarioDTO me(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return authService.me(usuario.id());
    }
}
