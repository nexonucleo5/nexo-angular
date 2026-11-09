package com.nexo.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Access tokens invalidados antes de expirarem — hoje, os de quem fez logout.
 *
 * <p>O JWT é stateless: uma vez assinado, vale até o {@code exp} chegar. Isso significava
 * que sair da conta não encerrava o acesso de imediato — o token na mão do cliente
 * continuava aceito por até 15 minutos, que é a janela em que um token copiado de uma
 * máquina compartilhada ainda abriria a conta depois de o dono ter saído. O refresh token
 * já era revogado na hora; faltava o access token.
 *
 * <p>Guarda-se o {@code jti} (identidade do token, emitida em
 * {@link JwtService#gerarAccessToken}) e não o usuário: assim o logout de um dispositivo
 * não invalida o access token dos outros, o que desfaria o logout por sessão.
 *
 * <p>A entrada só precisa viver enquanto o token viveria — passado o TTL, ele é recusado
 * pela validação normal de expiração e a linha aqui vira peso morto. Daí a poda.
 *
 * <p><b>Limite conhecido</b>: o registro é em memória. Num reinício a lista se perde e um
 * token cujo logout ocorreu antes volta a ser aceito pelo tempo que restar dele (no máximo
 * o TTL). Para uma instância só, como é o caso, o custo disso é menor que o de consultar
 * um armazenamento externo a cada requisição; com várias instâncias seria preciso mover a
 * lista para algo compartilhado (Redis), porque cada uma teria a sua.
 */
@Component
public class AccessTokensRevogados {

    /** A partir daqui uma nova revogação varre as entradas já vencidas. */
    private static final int LIMITE_PODA = 1_000;

    private final Duration janela;
    private final Map<String, Instant> revogadoEm = new ConcurrentHashMap<>();

    /** uid → instante a partir do qual os tokens emitidos antes dele deixam de valer. */
    private final Map<Long, Instant> usuarioRevogadoEm = new ConcurrentHashMap<>();

    public AccessTokensRevogados(@Value("${nexo.jwt.access-token-minutes}") long accessTokenMinutes) {
        this.janela = Duration.ofMinutes(accessTokenMinutes);
    }

    /** Marca o token como recusado pelo resto da vida útil dele. */
    public void revogar(String jti) {
        if (jti == null || jti.isBlank()) return;
        if (revogadoEm.size() >= LIMITE_PODA) {
            podarVencidos();
        }
        revogadoEm.put(jti, Instant.now());
    }

    /**
     * Derruba <b>todos</b> os access tokens já emitidos para o usuário, de qualquer sessão.
     * Usado quando o que o token afirma deixa de ser verdade — troca de papel e desativação
     * da conta —, casos em que esperar os 15 minutos do TTL deixaria um rebaixado agindo com
     * o papel antigo. Quem entra de novo depois recebe um token emitido após este instante e
     * não é afetado.
     */
    public void revogarUsuario(Long usuarioId) {
        if (usuarioId == null) return;
        if (usuarioRevogadoEm.size() >= LIMITE_PODA) {
            Instant limite = Instant.now().minus(janela);
            usuarioRevogadoEm.values().removeIf(quando -> quando.isBefore(limite));
        }
        usuarioRevogadoEm.put(usuarioId, Instant.now());
    }

    /**
     * O {@code iat} do JWT tem precisão de segundos, então a comparação é por segundo e
     * inclusiva: um token emitido no mesmo segundo da revogação também cai. Errar para esse
     * lado só custa um novo login; o outro lado deixaria passar um token anterior à troca.
     */
    public boolean usuarioRevogado(Long usuarioId, Instant emitidoEm) {
        Instant quando = usuarioRevogadoEm.get(usuarioId);
        if (quando == null) return false;
        if (quando.plus(janela).isBefore(Instant.now())) {
            usuarioRevogadoEm.remove(usuarioId);
            return false;
        }
        return emitidoEm == null || emitidoEm.getEpochSecond() <= quando.getEpochSecond();
    }

    public boolean revogado(String jti) {
        Instant quando = revogadoEm.get(jti);
        if (quando == null) return false;

        // Passada a janela o próprio token já expirou, então a entrada não decide mais
        // nada — aproveita a consulta para removê-la.
        if (quando.plus(janela).isBefore(Instant.now())) {
            revogadoEm.remove(jti);
            return false;
        }
        return true;
    }

    private void podarVencidos() {
        Instant limite = Instant.now().minus(janela);
        revogadoEm.values().removeIf(quando -> quando.isBefore(limite));
    }
}
