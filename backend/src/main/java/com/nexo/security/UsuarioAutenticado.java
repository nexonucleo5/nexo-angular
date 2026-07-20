package com.nexo.security;

/** Principal leve extraído do JWT — evita ida ao banco a cada request. */
public record UsuarioAutenticado(Long id, String login, String nome, String role) {
}
