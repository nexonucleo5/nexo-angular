package com.nexo.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 em hexadecimal, para guardar token no banco sem guardar o token.
 *
 * <p>Vale para refresh token e para token de recuperação de senha: os dois são valores
 * aleatórios longos, e o que a tabela precisa é reconhecer o valor apresentado — não
 * conseguir reproduzi-lo. Quem ler o banco não sai de lá com credencial utilizável.
 *
 * <p>De propósito <b>não</b> é bcrypt, que é o certo para senha: senha é escolhida por
 * gente, tem pouca entropia e precisa de hash lento para resistir a força bruta. Estes
 * tokens têm 256 bits de aleatoriedade — não há dicionário que os alcance, e o custo do
 * bcrypt aqui só faria cada requisição demorar mais.
 */
public final class HashDeToken {

    private HashDeToken() {}

    public static String de(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }
}
