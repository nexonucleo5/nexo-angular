/** Produção: a API e o WebSocket são servidos sob o mesmo domínio (reverse proxy). */
export const environment = {
  production: true,
  apiUrl: '/api',
  // wss quando servido via HTTPS; o path /ws é encaminhado ao Spring pelo reverse proxy
  wsUrl: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`,
};
