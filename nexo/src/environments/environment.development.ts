/**
 * Desenvolvimento: a API é relativa ('/api') e o `ng serve` a encaminha conforme o
 * `proxy.conf.json` — contas, login, perfil, alunos e professores vão para o
 * usuario-api (:8081); o restante, para o monólito Spring (:8080). Mesma origem do
 * front, então o cookie do refresh token (SameSite=Strict) funciona sem CORS.
 * O WebSocket do chat segue direto no monólito.
 */
export const environment = {
  production: false,
  apiUrl: '/api',
  wsUrl: 'ws://localhost:8080/ws',
};
