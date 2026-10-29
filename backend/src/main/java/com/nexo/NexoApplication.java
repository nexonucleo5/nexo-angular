package com.nexo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

// A autenticação é por JWT (ver JwtAuthFilter) e não há UserDetailsService próprio, então o
// Spring Boot criava um usuário "user" com senha aleatória e a imprimia no log. Ninguém a usa,
// mas ela ia parar no console e na tela de logs — uma credencial a menos para vazar.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableScheduling
public class NexoApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexoApplication.class, args);
    }
}
