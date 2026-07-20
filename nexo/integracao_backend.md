# Nexo - Gestão Escolar — Integração com Backend

> Documento da implementação da arquitetura definida em `arquitetura_java.md` (Task 2). Cobre o backend Java/Spring criado em `backend/` e as mudanças de integração no Angular (antecipando os entregáveis técnicos da Task 5).

---

## Visão geral

```
Angular 21 (nexo/)                          Spring Boot 3.5 (backend/)
┌────────────────────────────┐   HTTP/JSON  ┌─────────────────────────────┐
│ Components (signals)       │ ───────────► │ Controllers (@PreAuthorize)  │
│ Services de domínio (api/) │              │ Services (regras de negócio) │
│ Interceptors (auth/erro)   │ ◄─────────── │ JPA/H2 (16 entidades)        │
│ Guards por role            │   JWT Bearer │ JWT + refresh rotacionado    │
└────────────────────────────┘              └─────────────────────────────┘
```

- **Dev:** Angular em `http://localhost:4200` (usa `environment.development.ts` → `http://localhost:8080/api`; CORS liberado no backend).
- **Prod:** `environment.ts` aponta para `/api` (reverse proxy no mesmo domínio).

## Backend criado (`backend/`)

- **Auth:** `POST /api/auth/login|refresh|logout`, `GET /api/auth/me`. Access token JWT de 15 min + refresh token de 7 dias **rotacionado a cada uso**; bloqueio temporário após 5 tentativas falhas; eventos de login/logout auditados.
- **Contrato completo** de `arquitetura_java.md`: usuários/perfil (inclui `POST /api/usuarios/me/senha`), configurações por seção, cadastro de aluno (credenciais geradas no servidor, CPF validado com dígitos verificadores e unicidade), matrículas paginadas com filtro server-side, frequência/conteúdos por turma, avaliações/questões/notas (média ponderada oficial no servidor), mensagens/avisos/dúvidas/observações, evasão (risco **calculado** de frequência+notas+engajamento), auditoria, relatórios agregados com **exportação real PDF/XLSX**, monitoramento docente.
- **Envelopes padrão:** paginação `{content, page, size, totalElements, totalPages}` e erro `{timestamp, status, error, message, fields}` via `@RestControllerAdvice`.
- **Seed:** dados equivalentes aos mocks do frontend (3 usuários, 4 turmas, 12 alunos com notas/frequência de 6 semanas, avaliações, conversas, avisos, dúvidas). Logins dev: `aluno` / `professor` / `diretor`, senha `123456`.

## Mudanças no Angular

### Infraestrutura HTTP (novo)
| Arquivo | Papel |
|---|---|
| `src/environments/environment*.ts` | `apiUrl` por ambiente (fileReplacements no `angular.json`) |
| `src/app/core/api.models.ts` | tipos do contrato + `PageEnvelope`, `ApiErro` e o `KpiStat` único (substitui as 8 interfaces `Stat*`) |
| `src/app/core/auth.interceptor.ts` | anexa Bearer; em 401 tenta **um** refresh e refaz a request; falhou → logout + `/login` |
| `src/app/core/error.interceptor.ts` | normaliza o envelope de erro do backend para a UI |
| `src/app/core/guards.ts` | `authGuard` e `roleGuard(...roles)` usando a role vinda do backend |

### Autenticação real
- `AuthService` reescrito: `login()` chama `POST /api/auth/login`; o mapeamento login→role hardcoded foi removido; a role do payload do backend é a fonte de verdade (armazenada em minúsculo para compatibilidade com os templates).
- `alternarPerfil()` e o botão "Mudar para..." foram removidos — trocar de perfil agora exige logar com outro usuário.
- `UserService` (BehaviorSubject) removido, como indicado na Task 1.

### Rotas
- `app.routes.ts` convertido para **lazy loading** (`loadComponent`) com guards por perfil. O bundle inicial caiu para ~316 kB com todas as telas em chunks lazy.
- `app.ts` deixou de importar componentes de tela (eram carregados eager sem necessidade).

### Configurações sincronizadas (decisão da Task 2)
- `ConfiguracoesService`: leitura inicial via `GET /api/configuracoes` com **servidor-vence**; `localStorage` mantido como cache para aplicar o tema antes do primeiro round-trip; cada `updateSection` agenda `PATCH /api/configuracoes/{secao}` com debounce de 600 ms; API indisponível → segue com cache local.
- No backend as configurações são particionadas por seção e por perfil (defaults do `configuracao.md`), com a validação `início < fim` da Disponibilidade reforçada no servidor.

### Cadastro de aluno
- `cadastro.ts` não gera mais e-mail/senha no client: o formulário é enviado a `POST /api/alunos` e as credenciais retornam na resposta (botão "Gerar Acesso" removido do template).

### Camada de dados (`src/app/api/`)
Services tipados prontos para as telas consumirem (substituindo os arrays hardcoded):
`AlunosService`, `MatriculasService`, `TurmasService` (frequência/conteúdos/notas), `AvaliacoesService` (+questões), `ComunicacaoService` (mensagens/avisos/dúvidas), `GestaoDiretorService` (evasão/relatórios+export/monitoramento).

## Migração das telas (em andamento)

Política decidida com o time: quando o DTO do backend é mais enxuto que a tela, **estende-se o backend** (entidade + DTO + seed) para preservar a UI rica — em vez de simplificar a tela. Rebuild do backend via Maven wrapper (`~/.m2/wrapper/.../mvn.cmd` + JDK 25); banco H2 re-semeado apagando `backend/data/nexo.mv.db`.

| Tela | Status | Observações |
|---|---|---|
| **Gestão de Evasão** (`gestao-evasao`) | ✅ migrada | Consome `GET /api/evasao/risco`. `Aluno` ganhou `foto`, `ultimoAcessoEm`, `intervencoes`, `ultimaIntervencaoEm`; DTO passou a trazer `matricula` (derivada), `motivoPrincipal` (derivado dos fatores de risco no servidor), contato do responsável e datas ISO (formatadas no client como "Há X dias"). KPIs e lista de turmas passaram a ser derivados da resposta real (antes eram fixos no template). |
| **Relatórios** (`relatorios-diretor`) | ✅ migrada | KPIs passaram a ser os agregados reais (`taxaAprovacao`, `mediaGeral`, `frequenciaMedia`, `engajamentoMedio`); barras por turma e "turmas gargalo" derivam da série `turmas[]`. Os KPIs sem origem no domínio (NPS, egressos, ouvidoria) foram substituídos por métricas reais. **Botões Exportar PDF/Excel agora baixam arquivos de verdade** (`GET /api/relatorios/desempenho/export`, antes sem handler). |
| **Monitoramento** (`monitoramento-docente`) | ✅ migrada | Consome `GET /api/monitoramento/professores`. `Professor` ganhou `foto`, `turmas` e métricas (`correcoesPendentes`, `tempoRespostaDias`, `interacoesSemana`, `avaliacao`, `tarefasConcluidas/Total`); seed passou de 2 para 5 docentes. Status derivado da avaliação no servidor; KPIs do topo e "Top Performers" derivados da lista no client. |
| **Dashboard Diretor** (`diretor-dashboard`) | ✅ migrada | Combina `desempenho` + `evasao/risco` + `monitoramento` (forkJoin). KPIs, lista de alunos em risco e alertas críticos passaram a ser reais; o gráfico virou barras por turma (média×10 e frequência) vindas do backend. |
| **Dashboard Aluno** (`dashboards`) | ✅ migrada | **Subsistema de gamificação criado**: `Aluno` ganhou XP/ofensiva/meta/tarefas; nova entidade `AtividadeAluno` (feed) + repositório; `AlunoDashboardService` + `GET /api/aluno/dashboard` (XP, ranking calculado, atividades). Frontend: `AlunoDashboardService` + rewrite do componente. |
| **Dashboard Professor** (`dashboard-professor`) | ✅ migrada | **Subsistema criado**: FK `Turma.professor`; entidades `AulaAgendada` (grade de horários) e `AtividadeProfessor` (feed) + repositórios; `ProfessorDashboardService` + `GET /api/professor/dashboard`. KPIs (turmas/alunos/correções/avaliações do mês), minhas turmas com média, próximas aulas, alunos em atenção (via `EvasaoService`) e feed — tudo real. Frontend reescrito. |

### Bug crítico de encoding corrigido (afetava TODAS as telas)
As respostas JSON vinham com **double-encoding de UTF-8** (`á` → `Ã¡`, `º` → `Âº`) — afetava todo dado acentuado de toda a API, não só o seed. A causa era a serialização JSON do Spring/Jackson no runtime (não o banco: o dado persistido estava correto). **Correção:** `spring.jackson.generator.escape-non-ascii: true` no `application.yml` — o Jackson passa a emitir `\uXXXX` (ASCII puro, imune a double-encoding) e o client decodifica de volta. Também forcei `project.build.sourceEncoding=UTF-8` no `pom.xml` (o build via Maven wrapper compilava fontes como Cp1252). Sempre rebuildar com `JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8`.
| **Diário de Classe** (`diario-classe-professor`) | ✅ migrada | Turmas reais; frequência do dia carregada e **salva** (`POST /turmas/{id}/frequencia`); conteúdo ministrado **registrado** e histórico carregado (`/turmas/{id}/conteudos`). Fim dos `console.log`. |
| **Avaliações** (`avaliacoes`) | ✅ migrada | Abas Ativas/Fila/Questões carregam de `/avaliacoes`, `/avaliacoes/fila-correcao`, `/questoes`; criação real via `POST /avaliacoes` (turma real). |
| **Notas-Engajamento** (`notas-engajamento`) | ✅ migrada | Tabela de notas real e editável, **salva** via `PATCH /alunos/{id}/notas`; KPIs e distribuição derivados. Seções de evolução/engajamento (sem dados no endpoint de notas) removidas. |
| **Comunicação** (`comunicacao`) | ✅ migrada | Mensagens (responder), avisos (publicar), dúvidas (responder) e perfil do aluno (notas + observações reais com criação) via `ComunicacaoService`/`AlunosService`/`MatriculasService`. |
| **Matrículas** (`matriculas-diretor`) | ✅ migrada | Lista paginada de `/matriculas` com KPIs derivados; status e documentação reais. Campos de compliance sem origem no domínio (contrato/MEC/checklist/responsável) foram substituídos por status+documentação. |

### Bugs corrigidos durante a migração
- **Mojibake nos dados semeados**: o `data/nexo.mv.db` havia sido semeado por um build antigo com encoding errado (`João` → `JoÃ£o`, `9º` → `9Âº`). O jar atual semeia em UTF-8 corretamente — bastou apagar o banco e deixar re-semear. Registrado aqui para o deploy: garantir `-Dfile.encoding=UTF-8`/UTF-8 no build de produção.
- **Tela de Auditoria removida** a pedido do time (funcionalidade sem dono/definição). Saíram: componente `auditoria/`, rota, link no `menu-diretor`, método `eventosAuditoria` do `GestaoDiretorService` e o `EventoAuditoriaDTO`. O **registro** de eventos de login/logout no backend permanece (usado pela autenticação); o endpoint `GET /api/auditoria/eventos` ficou órfão mas inofensivo — pode ser removido no futuro se não for reaproveitado.

## Próximos passos (pendências conscientes)
- Concluir a migração das telas restantes da tabela acima.
- Dividir `comunicacao.ts`, `avaliacoes.ts` e `notas-engajamento.ts` em subcomponentes por aba, junto da migração para HTTP (recomendação da Task 2).
- ~~Task 4 (telas de configuração por perfil)~~ — **feita**: `configuracao-aluno/`, `configuracao-professor/` e `configuracao-diretor/` consomem `GET/PATCH /api/configuracoes` via o utilitário `configuracoes/settings-store.ts` (ver `configuracao.md`, seção "Ajustes realizados").
- Trocar H2 por banco gerenciado e externalizar `NEXO_JWT_SECRET` no deploy (Task 6).
