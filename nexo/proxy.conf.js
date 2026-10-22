/**
 * Proxy do `ng serve`: a API é uma só para o front ('/api'), mas atrás dela há três
 * backends. A ordem importa — vale a PRIMEIRA regra que casar, então as mais específicas
 * vêm antes das genéricas (ex.: /api/professores antes de /api/professor).
 *
 *   usuario-api   :8081  login, perfil, contas, alunos e professores (cadastro/listas)
 *   pedagogico-api :8082  turmas, matérias, notas, frequência, avaliações, relatórios…
 *   monólito      :8080  o que ainda não foi separado: comunicação (chat, avisos, dúvidas,
 *                         mensagens), gamificação (dashboard do aluno, desafios) e o WebSocket
 */
const USUARIO = { target: 'http://localhost:8081', secure: false };
const PEDAGOGICO = { target: 'http://localhost:8082', secure: false };
const MONOLITO = { target: 'http://localhost:8080', secure: false };

module.exports = {
  // ── usuario-api ──
  '/api/auth': USUARIO,
  '/api/usuarios': USUARIO,
  '/api/fotos': USUARIO,
  '/api/configuracoes': USUARIO,
  '/api/admin/contas': USUARIO,
  '/api/professores': USUARIO,
  // /api/alunos e /api/alunos/{id}: lista, detalhe e cadastro. As notas e observações do
  // aluno (/api/alunos/{id}/notas…) são do pedagógico e casam mais abaixo.
  '^/api/alunos(/[0-9]+)?/?([?].*)?$': USUARIO,

  // ── ainda no monólito (gamificação) — antes das regras genéricas do pedagógico ──
  '^/api/admin/catalogo/desafios': MONOLITO,
  '^/api/aluno/(dashboard|desafios)': MONOLITO,

  // ── pedagogico-api ──
  '^/api/alunos/[0-9]+/(notas|observacoes)': PEDAGOGICO,
  '^/api/aluno/(notas|materias|conteudos)': PEDAGOGICO,
  '/api/professor': PEDAGOGICO,
  '/api/turmas': PEDAGOGICO,
  '/api/materias': PEDAGOGICO,
  '/api/inscricoes': PEDAGOGICO,
  '/api/avaliacoes': PEDAGOGICO,
  '/api/questoes': PEDAGOGICO,
  '/api/evasao': PEDAGOGICO,
  '/api/relatorios': PEDAGOGICO,
  '/api/auditoria': PEDAGOGICO,
  '/api/monitoramento': PEDAGOGICO,
  '/api/admin': PEDAGOGICO,

  // ── tudo o mais ainda é do monólito ──
  '/api': MONOLITO,
};
