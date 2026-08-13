package com.nexo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Saída de e-mail do sistema.
 *
 * <p><b>Fora da thread da requisição.</b> Uma conversa SMTP leva de centenas de
 * milissegundos a alguns segundos, e o Tomcat tem 50 threads: enviar em linha faria o
 * usuário esperar o servidor de e-mail responder, e um provedor lento viraria lentidão do
 * sistema inteiro. O envio é entregue ao executor da aplicação e a requisição segue.
 *
 * <p>Há um motivo de segurança além do desempenho: em
 * {@code RecuperacaoSenhaService.solicitar} a resposta precisa levar o mesmo tempo
 * existindo ou não a conta. Se o envio fosse síncrono, o pedido para uma conta real
 * demoraria o SMTP a mais — e o tempo de resposta diria quais logins existem, que é
 * justamente o que aquele fluxo evita.
 *
 * <p><b>Sem configuração, não quebra.</b> Se {@code spring.mail.host} não estiver definido
 * não existe {@link JavaMailSender} no contexto, e o envio vira registro no log. É o que
 * mantém o ambiente de desenvolvimento e a suíte de testes funcionando sem credencial de
 * provedor — e, em produção, o que faz um e-mail não entregue degradar em vez de derrubar.
 */
@Service
public class EnviadorDeEmail {

    private static final Logger log = LoggerFactory.getLogger(EnviadorDeEmail.class);

    private final ObjectProvider<JavaMailSender> remetenteSmtp;
    private final Executor executor;
    private final String de;

    public EnviadorDeEmail(ObjectProvider<JavaMailSender> remetenteSmtp,
                           @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
                           Executor executor,
                           @Value("${nexo.email.remetente:}") String de) {
        this.remetenteSmtp = remetenteSmtp;
        this.executor = executor;
        this.de = de;
    }

    /** Há provedor configurado? Usado por quem precisa decidir antes de prometer envio. */
    public boolean configurado() {
        return remetenteSmtp.getIfAvailable() != null && !de.isBlank();
    }

    /**
     * Enfileira o envio. Não devolve sucesso de propósito: a essa altura a resposta HTTP já
     * foi decidida, e o que acontece com o provedor não pode mudá-la.
     */
    public void enviar(String destino, String assunto, String corpo) {
        if (destino == null || destino.isBlank()) return;

        if (!configurado()) {
            // Em desenvolvimento é assim que se lê o link de recuperação: ele sai no log.
            log.info("E-mail não enviado (sem provedor configurado). Para: {} | Assunto: {}\n{}",
                    destino, assunto, corpo);
            return;
        }

        try {
            executor.execute(() -> entregar(destino, assunto, corpo));
        } catch (RejectedExecutionException e) {
            // Fila cheia: perder o e-mail é ruim, derrubar a requisição por causa dele é pior.
            log.warn("E-mail para {} descartado: a fila de envio está cheia.", destino, e);
        }
    }

    private void entregar(String destino, String assunto, String corpo) {
        try {
            SimpleMailMessage mensagem = new SimpleMailMessage();
            mensagem.setFrom(de);
            mensagem.setTo(destino);
            mensagem.setSubject(assunto);
            mensagem.setText(corpo);
            remetenteSmtp.getObject().send(mensagem);
            log.info("E-mail enviado para {} | Assunto: {}", destino, assunto);
        } catch (Exception e) {
            // Endereço inválido, provedor fora do ar, cota estourada: registra e segue. Quem
            // pediu já recebeu resposta, e repetir aqui não ajudaria.
            log.error("Falha ao enviar e-mail para {} | Assunto: {}", destino, assunto, e);
        }
    }
}
