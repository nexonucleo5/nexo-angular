package com.nexo.api.dto;

import com.nexo.domain.Usuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(@NotBlank String login, @NotBlank String senha) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record UsuarioDTO(Long id, String nome, String cargo, String foto, String role) {
        public static UsuarioDTO of(Usuario u) {
            return new UsuarioDTO(u.getId(), u.getNome(), u.getCargo(), u.getFoto(), u.getRole().name());
        }
    }

    public record TokenResponse(String token, String refreshToken, UsuarioDTO usuario) {}

    public record TrocaSenhaRequest(@NotBlank String senhaAtual,
                                    @NotBlank @Size(min = 8, message = "A nova senha deve ter ao menos 8 caracteres")
                                    String novaSenha) {}

    public record AtualizarPerfilRequest(String nome, String foto) {}
}
