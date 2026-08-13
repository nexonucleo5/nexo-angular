package com.nexo.api.dto;

import com.nexo.domain.Usuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(@NotBlank String login, @NotBlank String senha) {}

    public record UsuarioDTO(Long id, String nome, String cargo, String foto, String role) {
        public static UsuarioDTO of(Usuario u) {
            return new UsuarioDTO(u.getId(), u.getNome(), u.getCargo(), u.getFoto(), u.getRole().name());
        }
    }

    /**
     * O que o cliente recebe no corpo. De propósito <b>sem</b> o refresh token: ele viaja
     * em cookie HttpOnly (ver {@code RefreshTokenCookie}) justamente para ficar fora do
     * alcance do JavaScript da página. O access token continua no corpo porque o cliente
     * precisa montar o header {@code Authorization} — mas vive só na memória da aba e
     * expira em 15 minutos.
     */
    public record TokenResponse(String token, UsuarioDTO usuario) {}

    /**
     * Resultado interno de um login ou de uma rotação: o corpo da resposta mais o refresh
     * token em claro, que o controller converte em cookie e não deixa chegar ao JSON.
     */
    public record SessaoEmitida(TokenResponse resposta, String refreshTokenPlano) {}

    public record TrocaSenhaRequest(@NotBlank String senhaAtual,
                                    @NotBlank @Size(min = 8, message = "A nova senha deve ter ao menos 8 caracteres")
                                    String novaSenha) {}

    public record AtualizarPerfilRequest(String nome, String foto) {}

    /**
     * Só o login. De propósito não pede o e-mail junto: conferir os dois permitiria
     * descobrir qual endereço está cadastrado para uma conta, testando um de cada vez.
     */
    public record EsqueciSenhaRequest(@NotBlank String login) {}

    /**
     * O mesmo mínimo de 8 caracteres da troca autenticada — a senha que sai daqui vale
     * tanto quanto a outra, e não faria sentido a porta de recuperação aceitar uma mais
     * fraca do que a porta da frente.
     */
    public record RedefinirSenhaRequest(@NotBlank String token,
                                        @NotBlank @Size(min = 8, message = "A nova senha deve ter ao menos 8 caracteres")
                                        String novaSenha) {}
}
