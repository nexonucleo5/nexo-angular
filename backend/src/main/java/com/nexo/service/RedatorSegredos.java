package com.nexo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Tira segredos de um texto antes de ele ser gravado ou exibido nos logs.
 *
 * <p>O log da aplicação é lido por uma tela, e quem lê é um administrador — ou quem quer que
 * tenha a conta dele. Mensagens de framework e stack traces são escritas por quem não pensou
 * nisso: o Spring já chegou a registrar a senha que ele mesmo gerou, e um erro de conexão
 * pode trazer a URL do banco com usuário e senha. Sem esta etapa, "ver os logs" equivale a
 * "ver as credenciais".
 *
 * <p>Duas camadas, porque nenhuma sozinha basta:
 * <ul>
 *   <li><b>Valores conhecidos</b> — o segredo do JWT e a senha do banco, lidos da própria
 *       configuração, são trocados onde quer que apareçam, em qualquer formato. É a que pega
 *       o que nenhum padrão previu.</li>
 *   <li><b>Padrões</b> — {@code senha=...}, {@code token: ...}, {@code Bearer ...}, JWTs, hashes
 *       bcrypt, {@code usuario:senha@} em URL, e-mails e CPFs. Pega o que não está na
 *       configuração: o token de um usuário, a senha digitada num campo errado.</li>
 * </ul>
 *
 * <p>É aplicado na gravação ({@link LogAplicacaoAppender}) <b>e</b> na leitura
 * ({@code LogsSistemaController}): na leitura para cobrir registros anteriores a uma regra
 * nova, sem precisar reescrever a tabela. Mascarar é idempotente.
 *
 * <p>Não é uma garantia absoluta — nenhum filtro por padrão é. Por isso o código não deve
 * logar segredo em primeiro lugar; isto é a rede de segurança.
 */
@Component
public class RedatorSegredos {

    static final String MASCARA = "[REDACTED]";

    /** Valores literais só valem a pena acima disto: um segredo curto casaria com texto comum. */
    private static final int TAMANHO_MINIMO_LITERAL = 6;

    private static final Pattern BEARER =
            Pattern.compile("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9._~+/=-]{8,}");

    private static final Pattern JWT =
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");

    private static final Pattern BCRYPT =
            Pattern.compile("\\$2[abxy]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    /** {@code esquema://usuario:senha@host} — cobre jdbc:postgresql://, https://, redis:// etc. */
    private static final Pattern CREDENCIAL_EM_URL =
            Pattern.compile("(?i)(\\b[a-z][a-z0-9+.-]*://[^/\\s:@]+:)[^@\\s/]+(@)");

    /**
     * {@code chave = valor} ou {@code chave: valor}, com o valor entre aspas ou até o próximo
     * separador. A chave pode ser parte de um nome maior ({@code db.password},
     * {@code spring.datasource.password}, {@code refreshToken}).
     */
    private static final Pattern CHAVE_VALOR = Pattern.compile(
            "(?i)(senha|password|passwd|pwd|secret|segredo|token|api[_-]?key|authorization|"
                    + "cookie|credential|credencial)(\"?\\s*[=:]\\s*)(\\[REDACTED]|\"[^\"]*\"|'[^']*'|[^\\s,;&\"')\\]}]+)");

    private static final Pattern EMAIL =
            Pattern.compile("\\b[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\b");

    private static final Pattern CPF =
            Pattern.compile("\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b");

    private final String[] literais;

    public RedatorSegredos(@Value("${nexo.jwt.secret:}") String segredoJwt,
                           @Value("${spring.datasource.password:}") String senhaBanco) {
        this.literais = Stream.of(segredoJwt, senhaBanco)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(v -> v.length() >= TAMANHO_MINIMO_LITERAL)
                .distinct()
                .toArray(String[]::new);
    }

    public String mascarar(String texto) {
        if (texto == null || texto.isEmpty()) return texto;
        String r = texto;
        for (String literal : literais) {
            r = r.replace(literal, MASCARA);
        }
        r = CREDENCIAL_EM_URL.matcher(r).replaceAll("$1" + MASCARA + "$2");
        r = BEARER.matcher(r).replaceAll("$1 " + MASCARA);
        r = JWT.matcher(r).replaceAll(MASCARA);
        r = BCRYPT.matcher(r).replaceAll(MASCARA);
        r = CHAVE_VALOR.matcher(r).replaceAll("$1$2" + MASCARA);
        r = CPF.matcher(r).replaceAll("***.***.***-**");
        r = EMAIL.matcher(r).replaceAll("***@$1");
        return r;
    }
}
