# Nexo - Gestão Escolar — Planejamento de Arquitetura (Angular ↔ Java/Spring)

> Documento gerado na **Task 2** (Planejamento de Arquitetura), com base nos levantamentos das Task 0 (`requisitos_funcionais.md`) e Task 1 (`possiveis_alteracoes.md`). Define a separação de responsabilidades entre o frontend Angular atual e um backend Java/Spring a ser introduzido.

---

## Diagrama de Responsabilidades

### O que fica no Angular (Frontend)
- Renderização de UI e navegação (rotas, menus por perfil, layout).
- Estado de interação local e efêmero: abas ativas, filtros abertos/fechados, valores de formulário antes do submit, estado de loading/erro por request.
- Validações de formulário em tempo real (feedback imediato ao usuário) — sempre **duplicadas** no backend, nunca como única fonte de verdade.
- Máscaras de input (CPF, telefone) e formatação de exibição (datas, moeda, percentuais).
- Aplicação de tema (dark/white mode) e preferências puramente visuais (animações ligadas/desligadas).
- Cache client-side de curto prazo (ex.: resposta de listagem já carregada) para evitar refetch desnecessário durante a mesma sessão.
- Cache local (localStorage) para uso *offline-first* de itens não críticos — token de sessão, última tela de preferências renderizada — nunca como fonte única de dados de negócio.

### O que vai para Java/Spring (Backend)
- Toda a persistência de dados de negócio (alunos, professores, turmas, matrículas, frequência, avaliações, notas, mensagens, avisos, observações pedagógicas).
- Autenticação real (verificação de credenciais, emissão/validação de token) e autorização por perfil.
- Toda regra de negócio que hoje está "fake" ou incompleta no Angular (ver seção *Funcionalidades a Migrar*).
- Cálculo de métricas agregadas (KPIs de dashboard, taxa de evasão, taxa de aprovação, engajamento, ranking) — hoje calculadas manualmente em arrays hardcoded no `.ts`, no futuro calculadas no servidor a partir do banco.
- Geração de relatórios (PDF/Excel) e exportações — hoje botões sem handler.
- Auditoria de acessos e alterações críticas (log de eventos) — hoje mockado em `auditoria/`.
- Envio de e-mails/notificações reais.

### O que é compartilhado/sincronizado
- **Configurações de usuário** (`configuracoes/`): hoje 100% `localStorage`. Já identificado na Task 3 como candidato a sincronização entre dispositivos (Task 2 deveria decidir isso — decisão abaixo).
  - **Decisão proposta**: sincronizar via API, mantendo `localStorage` como cache local para aplicar o tema imediatamente no carregamento da página (antes do primeiro round-trip), com resolução "servidor vence" em caso de conflito. O `effect()` que hoje salva direto no `localStorage` passa a debouncar e enviar `PATCH` para a API; a leitura inicial faz `GET` e só cai para o `localStorage` se a API estiver indisponível.
- **Sessão/token**: o token fica em memória/`localStorage` no cliente, mas sua validade é sempre decidida pelo servidor (nenhuma regra de expiração client-side é fonte de verdade).
- **Role do usuário**: hoje inferido pelo texto digitado no login (`AuthService`); passa a vir do backend no payload de autenticação e ser a fonte de verdade para os guards de rota (client-side) e para a autorização real (server-side).

---

## APIs e Contratos

### Autenticação e autorização
```
POST   /api/auth/login          { login, senha }              → { token, refreshToken, usuario: { id, nome, cargo, foto, role } }
POST   /api/auth/refresh        { refreshToken }               → { token, refreshToken }
POST   /api/auth/logout         (Bearer token)                 → 204
GET    /api/auth/me             (Bearer token)                 → { id, nome, cargo, foto, role }
```
- **Autenticação**: JWT Bearer no header `Authorization`. Access token de curta duração (ex.: 15 min) + refresh token de duração maior, rotacionado a cada uso.
- **Autorização**: role (`ALUNO`, `PROFESSOR`, `DIRETOR`) embutida no token (claim), validada em todo endpoint sensível via anotação (`@PreAuthorize` em Spring Security). O frontend usa a mesma role para os guards de rota, mas nunca é a única barreira — todo endpoint reforça a checagem no servidor.

### Usuários / Perfil
```
GET    /api/usuarios/me                       → dados de perfil do usuário logado
PATCH  /api/usuarios/me                       → edição de perfil (nome, foto)
POST   /api/usuarios/me/senha                 { senhaAtual, novaSenha } → troca de senha (tela hoje inexistente, ver Funcionalidades a Migrar)
```

### Configurações
```
GET    /api/configuracoes                      → Settings do perfil logado (por perfil: aluno/professor/diretor)
PATCH  /api/configuracoes/{secao}               { ...campos }  → atualização parcial de uma seção (notificações, aparência, etc.)
```
- Um único recurso por usuário, particionado por seção — mantém a granularidade de update que já existe hoje no `ConfiguracoesService.updateSection`.

### Cadastro / Matrículas
```
POST   /api/alunos                              → cadastro de aluno (gera e-mail institucional e credenciais no backend, não mais no client)
GET    /api/matriculas?status=&turma=&busca=    → listagem paginada com filtros (hoje filtrado client-side sobre array fixo)
GET    /api/matriculas/{id}
PATCH  /api/matriculas/{id}/documentos          → atualização de status de documentação
```

### Diário de Classe / Frequência
```
GET    /api/turmas/{turmaId}/frequencia?data=   → lista de alunos + status de presença do dia
POST   /api/turmas/{turmaId}/frequencia         { data, presencas: [{ alunoId, presente }] } → salva frequência (hoje só `console.log`)
POST   /api/turmas/{turmaId}/conteudos          { titulo, descricao, observacoes, data } → registro de conteúdo ministrado
GET    /api/turmas/{turmaId}/conteudos          → histórico de aulas
```

### Avaliações e Notas
```
GET    /api/avaliacoes?turma=&status=
POST   /api/avaliacoes                          → criar avaliação
GET    /api/avaliacoes/fila-correcao
GET    /api/questoes
POST   /api/questoes
GET    /api/turmas/{turmaId}/notas?disciplina=&periodo=
PATCH  /api/alunos/{alunoId}/notas               { p1, p2, t1, participacao } → edição de notas (hoje só em memória)
```

### Comunicação
```
GET    /api/mensagens?caixa=entrada
POST   /api/mensagens/{conversaId}/responder     { texto }
GET    /api/avisos
POST   /api/avisos                               → publicar aviso (hoje sem handler)
GET    /api/duvidas?status=
POST   /api/duvidas/{id}/responder
POST   /api/alunos/{alunoId}/observacoes          { texto } → observação pedagógica
```

### Gestão de Evasão / Auditoria / Relatórios / Monitoramento (Diretor)
```
GET    /api/evasao/risco?risco=&turma=&busca=
GET    /api/auditoria/eventos?periodo=
GET    /api/relatorios/desempenho?periodo=&visao=
GET    /api/relatorios/desempenho/export?formato=pdf|xlsx   → exportação real (hoje decorativo)
GET    /api/monitoramento/professores
```

### Estrutura de requests/responses
- Envelope de resposta padrão para listagens paginadas:
```json
{ "content": [...], "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 }
```
- Envelope de erro padrão (para o interceptor de erro do Angular tratar de forma uniforme):
```json
{ "timestamp": "...", "status": 400, "error": "VALIDATION_ERROR", "message": "...", "fields": { "cpf": "CPF inválido" } }
```
- Todas as datas em ISO-8601 UTC; conversão de fuso e formatação ficam no Angular (pipe de data).

---

## Funcionalidades a Migrar para Backend

### Lógica de negócio que sai do TypeScript
- **Geração de e-mail institucional e senha provisória** (`cadastro/cadastro.ts`) — hoje montada no client a partir de nome+CPF; deve ser gerada e persistida no backend, evitando client decidir credenciais.
- **Mapeamento de login → role** (`services/auth.service.ts:36`) — hoje `if (loginLimpo === 'diretor' || loginLimpo === 'admin')`; vira consulta real de usuário/role no backend.
- **Cálculo de métricas de frequência** (`diario-classe-professor.ts` — `calcularMetricas()`) — pode continuar existindo no client para feedback imediato, mas o valor "oficial" salvo/exibido em relatórios passa a vir agregado do backend.
- **Cálculo de risco de evasão, KPIs institucionais, taxa de aprovação, engajamento médio** — hoje são números fixos nos `.ts` de `diretor-dashboard`, `gestao-evasao`, `relatorios-diretor`; passam a ser agregações calculadas no backend a partir dos dados reais.
- **Regras de gestão do perfil Diretor** já especificadas na Task 3 (bloco "Gestão"): "Aprovar professores automaticamente" e "Exigir aprovação manual de cadastro de aluno" — são regras de fluxo de aprovação que precisam de lógica e persistência no backend, não apenas um toggle de preferência visual.

### Validações que passam para servidor
- Validação de CPF, e-mail e telefone (hoje só regex client-side em `cadastro.ts`) — reforçar unicidade (CPF/e-mail já cadastrado) e formato no backend.
- Validação de horário de disponibilidade do Professor (`início < fim`, já prevista na Task 3, bloco "Disponibilidade") — validação client-side para feedback imediato, mas reforçada no backend antes de persistir.
- Validação de credenciais de login (força de senha, bloqueio após tentativas) — inexistente hoje, deve nascer no backend.
- Unicidade de matrícula/documentos no cadastro de aluno.

### Processamento de dados
- Agregação de dados para os gráficos hoje "feitos à mão" (`meus-niveis-notas`, `auditoria`, `relatorios-diretor`, `monitoramento-docente`, `notas-engajamento`) — o backend passa a devolver as séries já calculadas; o Angular só renderiza (idealmente via Chart.js/ng2-charts, já usado em `diretor-dashboard`, ver Task 1).
- Geração de exportações (PDF/Excel) — hoje botões sem handler em `relatorios-diretor` e `diario-classe-professor`.
- Envio de notificações reais (hoje só a permissão do browser é solicitada, sem conteúdo de fato enviado).

### Acesso ao banco de dados
- Todas as entidades hoje representadas por arrays hardcoded nos componentes viram tabelas: `Usuario`, `Aluno`, `Professor`, `Turma`, `Matricula`, `Frequencia`, `Avaliacao`, `Questao`, `Nota`, `Mensagem`, `Aviso`, `Duvida`, `ObservacaoPedagogica`, `EventoAuditoria`, `ConfiguracaoUsuario`.
- Como a Task 1 identificou 6 formas diferentes de representar "Aluno" no frontend (`AlunoRisco`, `AlunoAtencao`, `AlunoNota`, `AlunoFrequencia`, `AlunoDetalhe`, `NotaAluno`), o desenho do backend deve nascer com **uma entidade `Aluno` única**, e os DTOs de cada endpoint retornam apenas os campos relevantes por contexto — evitando que a fragmentação hoje existente no frontend se repita no banco.

---

## Melhorias no Angular

### Componentes que podem ser simplificados
- Substituir as **8 interfaces `Stat*` duplicadas** (ver Task 1) por um único `KpiStat` compartilhado, populado pela resposta da API em vez de hardcoded — simplifica tanto o tipo quanto o template (um único componente de "card de KPI" reutilizável).
- **`comunicacao.ts`** (290 linhas, 9 interfaces) passa a ser dividido em subcomponentes (chat, avisos, dúvidas, ficha do aluno) já como parte da migração para consumo de API — cada aba busca seus próprios dados de forma independente.
- **`avaliacoes.ts`** e **`notas-engajamento.ts`** — mesma lógica: separar por aba/responsabilidade ao mesmo tempo em que a busca de dados migra de array fixo para chamada HTTP.

### Remoção de lógica pesada do frontend
- Remover os cálculos de KPI/agregação hoje feitos em arrays estáticos client-side, substituindo por consumo direto da resposta já agregada da API (ver *Processamento de dados* acima).
- Remover a geração de e-mail/senha no `cadastro.ts` (passa a ser resposta da API).
- Remover os `console.log` que hoje simulam persistência (`avaliacoes.ts`, `cadastro.ts`, `diario-classe-professor.ts`, `notas-engajamento.ts`) e substituir por chamadas reais via `HttpClient`, com estado de loading/erro tratado nos componentes.

### Otimização de state management
- Padronizar em **signals** (já é o padrão mais moderno usado em `AuthService`/`ConfiguracoesService`) para estado alimentado por API, usando `resource()`/`httpResource()` (Angular 21) para requests com estado de loading/erro built-in — substitui a mistura atual de `ngModel` solto, `BehaviorSubject` (`UserService`, a remover) e arrays mutados diretamente.
- Implementar **interceptors HTTP** para: anexar o Bearer token automaticamente, tratar erros de forma centralizada (mapeando o envelope de erro padrão para mensagens de UI) e disparar refresh de token quando o access token expira.
- Implementar **guards de rota** (`CanActivateFn`) usando a role vinda do backend, aproveitando a organização por perfil que já existe em `app.routes.ts` — pré-requisito, junto da autenticação real, para que o controle de acesso deixe de ser só visual (apontado como Must Have na Task 0).
- Converter `app.routes.ts` para **lazy loading** (`loadComponent`) por perfil, reduzindo o bundle inicial (~2140 linhas de TS hoje carregadas eager, conforme Task 1) — natural de fazer junto da introdução de guards, já que as rotas serão reorganizadas de qualquer forma.
