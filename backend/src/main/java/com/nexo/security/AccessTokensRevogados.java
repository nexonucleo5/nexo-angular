package com.nexo.security;

import com.nexo.domain.AccessTokenRevogado;
import com.nexo.repository.AccessTokenRevogadoRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Access tokens invalidados antes de expirarem: o logout de uma sessão e a derrubada de
 * todas as sessões de um usuário.
 *
 * <p>O JWT é stateless: uma vez assinado, vale até o {@code exp} chegar. Isso significava
 * que sair da conta não encerrava o acesso de imediato — o token na mão do cliente
 * continuava aceito por até 15 minutos, que é a janela em que um token copiado de uma
 * máquina compartilhada ainda abriria a conta depois de o dono ter saído. O refresh token
 * já era revogado na hora; faltava o access token.
 *
 * <p>Há duas granularidades:
 * <ul>
 *   <li><b>Por {@code jti}</b> ({@link #revogar}) — identidade do token, emitida em
 *       {@link JwtService#gerarAccessToken}. O logout de um dispositivo não invalida o
 *       access token dos outros, o que desfaria o logout por sessão.</li>
 *   <li><b>Por usuário</b> ({@link #revogarUsuario}) — recusa todo token emitido até o
 *       momento da chamada. Para quando a conta inteira deixa de valer: desativação,
 *       senha provisória redefinida pelo administrador, refresh token reapresentado
 *       (sessão roubada). Sem isto, o refresh token morria na hora mas o access token já
 *       emitido seguia aberto por até 15 minutos — o suficiente para a conta "desativada"
 *       continuar lendo e escrevendo. Um token emitido <i>depois</i> da revogação (login
 *       novo, conta reativada) não é atingido.</li>
 * </ul>
 *
 * <p>A entrada só precisa viver enquanto o token viveria — passado o TTL, ele é recusado
 * pela validação normal de expiração e a linha aqui vira peso morto. Daí a poda.
 *
 * <p><b>Persistência</b>: as revogações vão para o banco no ato e são relidas na subida.
 * Antes o registro era só um mapa em memória, e um reinício ou redeploy — que acontece no
 * Render a cada deploy e a cada cold start do plano gratuito — reabria por até 15 minutos
 * todo token cujo logout já tinha ocorrido. A consulta de cada requisição continua sendo
 * ao mapa em memória: o banco só é tocado ao revogar e ao iniciar.
 *
 * <p><b>Limite conhecido</b>: com mais de uma instância, cada uma só enxerga as revogações
 * que ela mesma fez (o banco é relido apenas no arranque). Para instância única, o caso
 * atual, está completo; ao escalar horizontalmente é preciso reler {@code
 * access_tokens_revogados} periodicamente ou mover a consulta para um cache compartilhado
 * (Redis).
 */
@Component
public class AccessTokensRevogados {

    /** A partir daqui uma nova revogação varre as entradas já vencidas. */
    private static final int LIMITE_PODA = 1_000;

    private static final String PREFIXO_JTI = "jti:";
    private static final String PREFIXO_USUARIO = "uid:";

    /** Um logout/derrubada: as sessões abertas dessa emissão (ex.: o WebSocket) precisam fechar. */
    public record SessaoRevogada(String jti) {}

    /** Todas as sessões do usuário caíram: o WebSocket dele precisa fechar. */
    public record UsuarioRevogado(Long usuarioId) {}

    private final Duration janela;
    private final AccessTokenRevogadoRepository repositorio;
    private final ApplicationEventPublisher eventos;
    private final Map<String, Instant> revogadoEm = new ConcurrentHashMap<>();
    private final Map<Long, Instant> usuarioRevogadoEm = new ConcurrentHashMap<>();

    public AccessTokensRevogados(@Value("${nexo.jwt.access-token-minutes}") long accessTokenMinutes,
                                 AccessTokenRevogadoRepository repositorio,
                                 ApplicationEventPublisher eventos) {
        this.janela = Duration.ofMinutes(accessTokenMinutes);
        this.repositorio = repositorio;
        this.eventos = eventos;
    }

    /** Repovoa a memória com o que o banco guardou de antes do reinício. */
    @PostConstruct
    public void carregarDoBanco() {
        for (AccessTokenRevogado r : repositorio.findByRevogadoEmAfter(Instant.now().minus(janela))) {
            String chave = r.getChave();
            if (chave.startsWith(PREFIXO_JTI)) {
                revogadoEm.put(chave.substring(PREFIXO_JTI.length()), r.getRevogadoEm());
            } else if (chave.startsWith(PREFIXO_USUARIO)) {
                try {
                    usuarioRevogadoEm.put(Long.valueOf(chave.substring(PREFIXO_USUARIO.length())), r.getRevogadoEm());
                } catch (NumberFormatException ignorada) {
                    // linha que este código não escreveu: não há o que aplicar
                }
            }
        }
    }

    /** Marca o token como recusado pelo resto da vida útil dele. */
    public void revogar(String jti) {
        if (jti == null || jti.isBlank()) return;
        if (revogadoEm.size() >= LIMITE_PODA) {
            podarVencidos();
        }
        Instant agora = Instant.now();
        revogadoEm.put(jti, agora);
        repositorio.save(new AccessTokenRevogado(PREFIXO_JTI + jti, agora));
        eventos.publishEvent(new SessaoRevogada(jti));
    }

    /**
     * Recusa todo access token já emitido para o usuário. Os que ele obtiver depois (novo
     * login) valem normalmente — a comparação é pelo {@code iat} do token.
     */
    public void revogarUsuario(Long usuarioId) {
        if (usuarioId == null) return;
        if (usuarioRevogadoEm.size() >= LIMITE_PODA) {
            podarVencidos();
        }
        Instant agora = Instant.now();
        usuarioRevogadoEm.put(usuarioId, agora);
        repositorio.save(new AccessTokenRevogado(PREFIXO_USUARIO + usuarioId, agora));
        eventos.publishEvent(new UsuarioRevogado(usuarioId));
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

    /**
     * O token foi emitido antes de o usuário ser derrubado?
     *
     * <p>Só é aceito o que foi emitido <i>estritamente depois</i> da revogação — o empate
     * recusa, e sem instante de emissão não há como provar que o token é posterior, então
     * também recusa. O chamador passa o instante em milissegundos quando o token o traz
     * (ver {@link JwtService#CLAIM_EMITIDO_EM_MS}); para um token de antes dessa versão só
     * resta o {@code iat}, que é o início do segundo e por isso erra para o lado seguro:
     * um login feito no mesmo segundo da revogação cai uma vez, o cliente renova e segue.
     */
    public boolean usuarioRevogado(Long usuarioId, Instant emitidoEm) {
        if (usuarioId == null) return false;
        Instant quando = usuarioRevogadoEm.get(usuarioId);
        if (quando == null) return false;

        if (quando.plus(janela).isBefore(Instant.now())) {
            usuarioRevogadoEm.remove(usuarioId);
            return false;
        }
        return emitidoEm == null || !emitidoEm.isAfter(quando);
    }

    private void podarVencidos() {
        Instant limite = Instant.now().minus(janela);
        revogadoEm.values().removeIf(quando -> quando.isBefore(limite));
        usuarioRevogadoEm.values().removeIf(quando -> quando.isBefore(limite));
    }

    /** Apaga do banco o que já não decide nada — a memória se poda sozinha, a tabela não. */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void limparBanco() {
        repositorio.removerAnterioresA(Instant.now().minus(janela));
    }
}
