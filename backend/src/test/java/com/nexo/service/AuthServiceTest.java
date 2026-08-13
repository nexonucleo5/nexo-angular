package com.nexo.service;

import com.nexo.api.ApiException;
import com.nexo.api.dto.AuthDtos.SessaoEmitida;
import com.nexo.domain.RefreshToken;
import com.nexo.domain.Role;
import com.nexo.domain.Usuario;
import com.nexo.repository.RefreshTokenRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.AccessTokensRevogados;
import com.nexo.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Repositórios mockados — nada de banco nem de Docker. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-caracteres-abcdef";
    private static final String SENHA_CORRETA = "senha-correta";
    private static final String IP = "127.0.0.1";
    /** Outra origem, para separar quem ataca de quem é dono da conta. */
    private static final String IP_ATACANTE = "203.0.113.7";

    @Mock private UsuarioRepository usuarios;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private AccessTokensRevogados accessTokensRevogados;
    @Mock private AuditoriaService auditoria;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private AuthService authService;
    private Usuario usuario;

    @BeforeEach
    void montar() {
        authService = new AuthService(usuarios, refreshTokens, new JwtService(SEGREDO, 15),
                accessTokensRevogados, encoder, auditoria, 7);

        usuario = new Usuario();
        usuario.setId(1L);
        usuario.setLogin("ana");
        usuario.setNome("Ana");
        usuario.setRole(Role.ALUNO);
        usuario.setAtivo(true);
        usuario.setSenhaHash(encoder.encode(SENHA_CORRETA));
    }

    private void usuarioExiste() {
        when(usuarios.findByLoginIgnoreCase("ana")).thenReturn(Optional.of(usuario));
    }

    // ── Login ────────────────────────────────────────────────────────────────

    @Test
    void loginComSenhaCorretaDevolveOsTokens() {
        usuarioExiste();
        when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        SessaoEmitida sessao = authService.login("ana", SENHA_CORRETA, IP);

        assertThat(sessao.resposta().token()).isNotBlank();
        // O refresh vai em claro só na resposta; o que fica gravado é o hash dele.
        assertThat(sessao.refreshTokenPlano()).isNotBlank();
        assertThat(sessao.resposta().usuario().nome()).isEqualTo("Ana");
        assertThat(sessao.resposta().usuario().role()).isEqualTo("ALUNO");
    }

    @Test
    void senhaErradaNaoAutentica() {
        usuarioExiste();

        assertThatThrownBy(() -> authService.login("ana", "senha-errada", IP))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Credenciais inválidas");

        // Nenhum refresh token pode ser emitido numa tentativa que falhou.
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void usuarioInativoNaoAutenticaMesmoComSenhaCerta() {
        usuario.setAtivo(false);
        usuarioExiste();

        assertThatThrownBy(() -> authService.login("ana", SENHA_CORRETA, IP))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void usuarioInexistenteDaAMesmaMensagemDeSenhaErrada() {
        // Mensagens diferentes permitiriam enumerar quais logins existem.
        when(usuarios.findByLoginIgnoreCase("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("fantasma", "qualquer", IP))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Credenciais inválidas");
    }

    // ── Bloqueio por tentativas ──────────────────────────────────────────────

    @Test
    void bloqueiaDepoisDeCincoTentativasErradas() {
        usuarioExiste();

        for (int i = 1; i <= 5; i++) {
            int tentativa = i;
            assertThatThrownBy(() -> authService.login("ana", "senha-errada", IP))
                    .as("tentativa %d ainda deve ser recusada por credencial, não por bloqueio", tentativa)
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Credenciais inválidas");
        }

        // A sexta nem chega a comparar a senha: cai no bloqueio.
        assertThatThrownBy(() -> authService.login("ana", SENHA_CORRETA, IP))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Muitas tentativas")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void loginBemSucedidoZeraAsTentativas() {
        usuarioExiste();
        when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> authService.login("ana", "senha-errada", IP))
                    .isInstanceOf(ApiException.class);
        }
        authService.login("ana", SENHA_CORRETA, IP);

        // Sem o reset, mais duas falhas passariam do limite e bloqueariam a conta.
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> authService.login("ana", "senha-errada", IP))
                    .hasMessageContaining("Credenciais inválidas");
        }
    }

    @Test
    void oBloqueioEhPorLoginENaoDerrubaOsOutrosUsuarios() {
        usuarioExiste();
        Usuario outro = new Usuario();
        outro.setId(2L);
        outro.setLogin("bruno");
        outro.setNome("Bruno");
        outro.setRole(Role.PROFESSOR);
        outro.setAtivo(true);
        outro.setSenhaHash(encoder.encode(SENHA_CORRETA));
        when(usuarios.findByLoginIgnoreCase("bruno")).thenReturn(Optional.of(outro));
        when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login("ana", "senha-errada", IP))
                    .isInstanceOf(ApiException.class);
        }

        assertThat(authService.login("bruno", SENHA_CORRETA, IP).resposta().token()).isNotBlank();
    }

    /**
     * O ponto do contador ser por (conta, origem) e não só por login. Antes, quem soubesse
     * um nome adivinhável — e {@code diretor} é adivinhável — trancava a conta alheia por
     * quinze minutos com cinco requisições. A defesa contra força bruta virava arma.
     */
    @Test
    void oBloqueioNaoAlcancaAContaVindaDeOutraOrigem() {
        usuarioExiste();
        when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login("ana", "senha-errada", IP_ATACANTE))
                    .isInstanceOf(ApiException.class);
        }

        // Quem errou trava a si mesmo — inclusive com a senha certa.
        assertThatThrownBy(() -> authService.login("ana", SENHA_CORRETA, IP_ATACANTE))
                .hasMessageContaining("Muitas tentativas")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // E a dona da conta continua entrando de onde sempre entrou.
        assertThat(authService.login("ana", SENHA_CORRETA, IP).resposta().token()).isNotBlank();
    }

    /**
     * O /refresh é público e consulta o banco a cada chamada: sem teto, um laço prendia as
     * dez conexões do pool sem apresentar credencial nenhuma. O limite é por origem e vale
     * para os endpoints de autenticação como um todo.
     */
    @Test
    void limitaATaxaDeRequisicoesDeUmaMesmaOrigem() {
        for (int i = 1; i <= 120; i++) {
            int chamada = i;
            assertThatThrownBy(() -> authService.refresh("inexistente", IP))
                    .as("chamada %d ainda deve ser recusada pelo token, não pela taxa", chamada)
                    .hasMessageContaining("Refresh token inválido");
        }

        assertThatThrownBy(() -> authService.refresh("inexistente", IP))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Muitas requisições")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    // ── Rotação do refresh token ─────────────────────────────────────────────

    // O que fica gravado é o hash do token, não ele mesmo — por isso as buscas são
    // casadas por anyString(): o hash é calculado dentro do serviço.

    @Test
    void refreshRevogaOTokenAnteriorEEmiteUmNovo() {
        RefreshToken antigo = new RefreshToken();
        antigo.setTokenHash("hash-do-refresh-antigo");
        antigo.setUsuario(usuario);
        antigo.setExpiraEm(Instant.now().plus(7, ChronoUnit.DAYS));
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(antigo));
        when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));

        SessaoEmitida sessao = authService.refresh("refresh-antigo", IP);

        assertThat(antigo.isRevogado()).as("o token usado precisa ser invalidado").isTrue();
        assertThat(sessao.refreshTokenPlano()).isNotEqualTo("refresh-antigo");

        ArgumentCaptor<RefreshToken> salvos = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens, org.mockito.Mockito.times(2)).save(salvos.capture());
        List<RefreshToken> capturados = salvos.getAllValues();
        assertThat(capturados.get(0)).isSameAs(antigo);
        assertThat(capturados.get(1).isRevogado()).isFalse();
    }

    @Test
    void refreshComTokenJaRevogadoDerrubaAsSessoesDoUsuario() {
        // Token roubado sendo reusado depois da rotação. Como não dá para saber qual das
        // duas partes é a legítima, todas as sessões caem e o usuário refaz o login —
        // antes o replay levava só 401 e a cópia paralela seguia valendo.
        RefreshToken revogado = new RefreshToken();
        revogado.setTokenHash("hash-ja-usado");
        revogado.setUsuario(usuario);
        revogado.setExpiraEm(Instant.now().plus(7, ChronoUnit.DAYS));
        revogado.setRevogado(true);
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(revogado));

        assertThatThrownBy(() -> authService.refresh("ja-usado", IP))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Sessão encerrada por segurança");

        verify(refreshTokens).revogarTodosDoUsuario(usuario.getId());
    }

    @Test
    void refreshComTokenExpiradoEhRecusado() {
        RefreshToken expirado = new RefreshToken();
        expirado.setTokenHash("hash-vencido");
        expirado.setUsuario(usuario);
        expirado.setExpiraEm(Instant.now().minus(1, ChronoUnit.DAYS));
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(expirado));

        assertThatThrownBy(() -> authService.refresh("vencido", IP))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void refreshComTokenDesconhecidoEhRecusado() {
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("nunca-existiu", IP))
                .isInstanceOf(ApiException.class);
    }
}
