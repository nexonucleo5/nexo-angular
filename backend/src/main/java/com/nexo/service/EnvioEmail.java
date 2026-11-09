package com.nexo.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Envio de e-mail transacional, sempre <b>fora da thread da requisição</b>: um SMTP
 * lento (ou fora do ar) não pode segurar o Tomcat nem tornar a resposta de "esqueci
 * minha senha" mais demorada para contas que existem do que para as que não existem.
 *
 * <p>Sem {@code spring.mail.host} não há {@link JavaMailSender}; nesse caso o envio vira
 * log. O corpo só aparece no log com {@code nexo.recuperacao.log-link=true} (padrão em
 * dev, desligado em produção) — o link é uma credencial por 30 minutos e não deve ir
 * para o coletor de logs.
 */
@Component
public class EnvioEmail {

    private static final Logger log = LoggerFactory.getLogger(EnvioEmail.class);

    private final JavaMailSender sender;
    private final String remetente;
    private final boolean logarCorpo;

    /** Poucas threads e daemon: o volume é de recuperações de senha, não de newsletter. */
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "envio-email");
        t.setDaemon(true);
        return t;
    });

    public EnvioEmail(ObjectProvider<JavaMailSender> sender,
                      @Value("${spring.mail.host:}") String hostSmtp,
                      @Value("${nexo.email.remetente:nexo@localhost}") String remetente,
                      @Value("${nexo.recuperacao.log-link:false}") boolean logarCorpo) {
        // Host vazio (SMTP_HOST sem valor no perfil prod) também cria o bean; trata como ausente.
        this.sender = hostSmtp.isBlank() ? null : sender.getIfAvailable();
        this.remetente = remetente;
        this.logarCorpo = logarCorpo;
    }

    public void enviar(String para, String assunto, String corpo) {
        executor.execute(() -> {
            if (sender == null) {
                log.info("SMTP não configurado (spring.mail.host): e-mail '{}' não enviado.{}",
                        assunto, logarCorpo ? "\n--- para: " + para + "\n" + corpo : "");
                return;
            }
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(remetente);
                msg.setTo(para);
                msg.setSubject(assunto);
                msg.setText(corpo);
                sender.send(msg);
            } catch (Exception e) {
                // Sem o destinatário no log: é dado pessoal e a causa não depende dele.
                log.warn("Falha ao enviar e-mail '{}': {}", assunto, e.toString());
            }
        });
    }

    @PreDestroy
    void encerrar() {
        executor.shutdown();
    }
}
