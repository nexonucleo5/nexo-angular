package com.nexo.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conta ocorrências por chave dentro de uma janela de tempo e diz quando a chave
 * passou do teto. Serve tanto para "falhas de login desta conta nesta origem"
 * quanto para "requisições vindas deste IP no último minuto" — muda só a chave,
 * o limite e o tamanho da janela.
 *
 * <p>A janela é fixa, não deslizante: a primeira ocorrência marca o início e tudo
 * é esquecido quando ela vence. É menos preciso que uma janela deslizante nas
 * bordas, e em troca cada chave ocupa uma linha de mapa em vez de uma lista de
 * instantes — o que importa quando a chave vem de fora e um atacante escolhe
 * quantas chaves distintas criar.
 *
 * <p><b>Limite conhecido</b>: a contagem é em memória, por instância. Com duas
 * réplicas atrás de um balanceador, o teto efetivo dobra e um reinício zera tudo.
 * Para uma instância — que é o caso hoje — sai bem mais barato que consultar um
 * armazenamento externo em todo login. Ver {@link AccessTokensRevogados}, que tem
 * a mesma limitação pelo mesmo motivo.
 */
public class JanelaDeTentativas {

    /** A partir daqui um novo registro varre as janelas já vencidas. */
    private static final int LIMITE_PODA = 1_000;

    private final int limite;
    private final Duration janela;
    private final Map<String, Contagem> contagens = new ConcurrentHashMap<>();

    private record Contagem(int quantidade, Instant inicio) {}

    public JanelaDeTentativas(int limite, Duration janela) {
        this.limite = limite;
        this.janela = janela;
    }

    /**
     * Segundos que faltam até a chave voltar a ser aceita, ou {@code 0} se ela ainda
     * está dentro do limite. Consulta pura — não conta a ocorrência.
     */
    public long bloqueioRestante(String chave) {
        Contagem atual = contagens.get(chave);
        if (atual == null) return 0;

        Instant fim = atual.inicio().plus(janela);
        Instant agora = Instant.now();
        if (!fim.isAfter(agora)) {
            // Janela vencida: a linha não decide mais nada, aproveita a consulta para removê-la.
            contagens.remove(chave, atual);
            return 0;
        }
        if (atual.quantidade() < limite) return 0;
        return Math.max(1, Duration.between(agora, fim).toSeconds());
    }

    /**
     * Conta mais uma ocorrência para a chave.
     *
     * <p>Registrar não decide nada: quem chama consulta {@link #bloqueioRestante} <b>antes</b>,
     * e só então faz o trabalho e conta. A ordem importa e é a mesma nos dois usos — é ela
     * que faz {@code limite} significar "quantas passam" em vez de "quantas menos uma".
     * Contar primeiro e olhar o resultado depois deixaria a última ocorrência permitida
     * sendo recusada, e os dois lugares que usam esta classe discordariam por um.
     */
    public void registrar(String chave) {
        if (contagens.size() >= LIMITE_PODA) {
            podarVencidas();
        }
        Instant agora = Instant.now();
        contagens.merge(chave, new Contagem(1, agora), (antiga, primeira) ->
                antiga.inicio().plus(janela).isAfter(agora)
                        ? new Contagem(antiga.quantidade() + 1, antiga.inicio())
                        // Janela vencida entre um registro e outro: recomeça a contagem
                        // em vez de somar para sempre sobre um início antigo.
                        : primeira);
    }

    /** Zera a contagem da chave — usado quando a tentativa legítima dá certo. */
    public void limpar(String chave) {
        contagens.remove(chave);
    }

    /** Remove as janelas encerradas: elas não bloqueiam mais ninguém, só ocupam heap. */
    private void podarVencidas() {
        Instant limiteTempo = Instant.now().minus(janela);
        contagens.values().removeIf(contagem -> contagem.inicio().isBefore(limiteTempo));
    }
}
