# Nexo - Gestão Escolar — Requisitos Funcionais

> Documento gerado na **Task 0** (Descoberta de Requisitos), a partir de levantamento factual do código Angular atual (`src/app`). Reflete o estado do sistema em 16/07/2026.

---

## ✅ Atualização de status — requisitos implementados (19/07/2026)

O levantamento abaixo é de 16/07. Depois dele, o backend Java/Spring e a integração foram concluídos (Task 2/5) e os requisitos foram atendidos:

**Must Have — todos concluídos:**
- ✅ **Autenticação real** — JWT contra base de usuários (`AuthService` + backend); senha com hash BCrypt, sessão com access+refresh token.
- ✅ **Guards de rota** por autenticação e por perfil (`authGuard`/`roleGuard`), reforçados no backend por `@PreAuthorize`.
- ✅ **Persistência real** de cadastro, frequência/diário, avaliações, notas, mensagens, avisos, dúvidas, observações — tudo via API.
- ✅ **Logout da tela de Configurações** — agora chama `AuthService.logout()` nos três componentes por perfil.
- ✅ **Bug `onEstudosChange`** corrigido (handlers padronizados `(key, checked)`).
- ✅ **Configurações segmentadas por perfil** (Aluno/Professor/Diretor) — Task 4.
- ✅ **Tela dedicada de troca de senha** (`trocar-senha/` → `POST /api/usuarios/me/senha`).
- ✅ **Persistência do cadastro de aluno** (`POST /api/alunos`, credenciais geradas no servidor).

**Should Have — concluídos:**
- ✅ **Edição de perfil** (`perfil/` → `PATCH /api/usuarios/me`).
- ✅ **Handlers de ação** antes ausentes: Publicar Aviso, Responder (mensagem/dúvida), criar Avaliação, salvar Frequência/Notas, Exportar Relatório (PDF/XLSX real), Acessar Disciplina.
- ✅ **Código morto removido**: `menu-perfil/`, `UserService` (`services/user.ts`), campo `eventos` do dashboard do diretor.
- ✅ **Painéis decorativos enganosos removidos**: Auditoria (tela inteira, a pedido do time) e "Sincronizado com API MEC"/"BI" em Relatórios (substituídos por exportação real).
- ✅ **Sincronização de preferências entre dispositivos** via API (`SettingsStore`).

**Telas antes ⚪ hardcoded — agora com dados reais:** dashboards (aluno/professor/diretor), matérias (+ detalhe da disciplina), **desafios** (iniciar/concluir com crédito de XP), **meus níveis e notas**, gestão de evasão, monitoramento docente, relatórios, matrículas, avaliações, notas-engajamento, comunicação, diário de classe.

**Gráficos padronizados para Chart.js/ng2-charts** (antes `<div>`/CSS/SVG feitos à mão): dashboard do diretor (já era), **relatórios** (desempenho por turma), **monitoramento docente** (produtividade por docente), **notas-engajamento** (distribuição de notas). Os SVG estáticos de "Meus Níveis e Notas" foram removidos; a tela de Auditoria (também citada) foi removida por inteiro.

**Handlers de ação implementados:** Exportar (Relatórios PDF/XLSX via backend; **Evasão, Matrículas e Diário via CSV client-side**), **Contatar** (mailto do responsável na Evasão), **Nova Questão** (formulário + `POST /api/questoes`), "Notas" no dropdown de usuário (link real para o aluno; "Calendário" removido).

**Últimas pendências — concluídas:**
- ✅ **"Ver Detalhes"** (Evasão, Matrículas, Avaliações) — modais de detalhe com todos os dados do item, reaproveitando os DTOs já carregados.
- ✅ **Editar/excluir questão** — `PUT`/`DELETE /api/questoes/{id}` no backend + formulário de edição e exclusão com confirmação no banco de questões.

**Status: 100% dos requisitos funcionais mapeados foram atendidos.** Ver detalhes por tela em `integracao_backend.md`.

---

## Requisitos Já Implementados

Levantamento por tela/módulo, com **status de cobertura** segundo 4 categorias:

- 🟢 **Funcional real** — funciona de ponta a ponta, inclusive persistência.
- 🟡 **Funcional em memória** — a interação funciona durante a sessão, mas não persiste (perdida ao recarregar a página) e/ou tem `TODO` explícito de integração futura com API.
- ⚪ **Mockado / somente leitura** — tela exibe dados fixos (hardcoded no `.ts`), sem nenhuma ação real.
- 🔴 **Incompleto / quebrado** — funcionalidade presente na UI mas com bug ou sem handler implementado.

### Autenticação e Sessão
| Funcionalidade | Módulo | Status |
|---|---|---|
| Login com usuário/senha | `login/` | 🔴 — qualquer usuário/senha não vazios autentica; o *role* (aluno/professor/diretor) é inferido do texto digitado, não há verificação de credenciais real |
| Persistência de sessão | `services/auth.service.ts` | 🟡 — via `localStorage` (`usuario_nexo`), sem token, sem expiração |
| Logout | `menu-usuario/` | 🟢 — funciona corretamente (chama `AuthService.logout()` e navega para `/login`) |
| Logout (via tela de Configurações) | `configuracoes/` | 🔴 — botão "Encerrar sessão" navega para `/login` mas **não chama** `AuthService.logout()`; sessão não é limpa |
| Troca de perfil sem novo login (`alternarPerfil`) | `services/auth.service.ts` | 🟢 — funcional, claramente uma feature de demonstração/prototipagem, não de produção |
| Proteção de rotas por autenticação/role | `app.routes.ts` | 🔴 — **não existe nenhum guard**; qualquer rota é acessível digitando a URL, mesmo sem login ou com role incompatível |

### Cadastro
| Funcionalidade | Módulo | Status |
|---|---|---|
| Formulário de cadastro de aluno (dados pessoais, responsável, endereço) | `cadastro/` | 🟡 — formulário reativo completo, com máscaras de CPF/telefone |
| Geração de e-mail institucional e senha provisória | `cadastro/` | 🟢 — lógica local funcional (não depende de backend) |
| Persistência do cadastro | `cadastro/` | 🔴 — `finalizarCadastro()` apenas faz `console.log`; dado é perdido ao recarregar |

### Perfil do Usuário
| Funcionalidade | Módulo | Status |
|---|---|---|
| Exibição de dados do usuário logado | `perfil/` | 🟢 — nome/foto/cargo vêm do `AuthService` |
| Progresso, conquistas, estatísticas (visão Aluno) | `perfil/` | ⚪ — 100% hardcoded |
| Visão de Diretor | `perfil/` | ⚪ — apenas mensagem informativa, sem conteúdo próprio |
| Visão de Professor | `perfil/` | 🔴 — sem tratamento específico no template |
| Edição de perfil | `perfil/`, `configuracoes/` | 🔴 — botão "Editar perfil" apenas navega para `/perfil`, não existe formulário de edição |

### Configurações
| Funcionalidade | Módulo | Status |
|---|---|---|
| Toggles de Notificações, Gamificação, Estudos, Aparência, Privacidade | `configuracoes/` | 🟢 — persistem em `localStorage` (`user_settings`) com efeitos reais (tema escuro, classes CSS, permissão de `Notification`) |
| Bug no bloco Estudos (`onEstudosChange`) | `configuracoes/` | 🔴 — dois toggles passam `boolean` mas o método espera `Event` (já documentado na Task 3) |
| Alterar senha | `configuracoes/` | 🔴 — não existe tela dedicada; navega para `/perfil` |
| Telas segmentadas por perfil (Aluno/Professor/Diretor) | `configuracoes/` | 🔴 — hoje é uma **única tela genérica**, com conteúdo 100% voltado ao perfil Aluno (endereçado na Task 3/4) |

### Área do Aluno
| Funcionalidade | Módulo | Status |
|---|---|---|
| Dashboard (KPIs, atividades, ranking) | `dashboards/` | ⚪ — hardcoded; nome de boas-vindas fixo ("Gabriel"), não usa o usuário logado |
| Listagem/busca/filtro de matérias | `materias/` | ⚪ — hardcoded (3 matérias); botão "Acessar Disciplina" sem destino |
| Desafios (busca, filtro, progresso) | `desafios/` | ⚪ — hardcoded; botões "Iniciar/Continuar/Ver Resultado" sem ação |
| Meus Níveis e Notas (gráficos, desempenho por matéria) | `meus-niveis-notas/` | ⚪ — hardcoded; gráficos são SVG estático, não usam biblioteca de charts |

### Área do Professor
| Funcionalidade | Módulo | Status |
|---|---|---|
| Dashboard do professor (turmas, alunos em atenção, atividades) | `dashboard-professor/` | ⚪ — hardcoded, somente leitura |
| Diário de classe: filtros de turma/disciplina/data | `diario-classe-professor/` | 🔴 — trocar filtro não busca dados novos (`TODO: buscar via API`) |
| Diário de classe: marcar presença/falta, "Marcar Todos Presentes" | `diario-classe-professor/` | 🟡 — funciona em memória, recalcula métricas, mas não persiste |
| Diário de classe: registrar conteúdo ministrado | `diario-classe-professor/` | 🟡 — adiciona ao histórico em memória, não persiste |
| Diário de classe: "Exportar Diário" | `diario-classe-professor/` | 🔴 — na prática chama a mesma função de salvar frequência, não exporta nada |
| Avaliações: criar avaliação | `avaliacoes/` | 🟡 — adiciona ao array em memória, `TODO: integrar com API` |
| Avaliações: fila de correção, banco de questões, editar/excluir | `avaliacoes/` | ⚪🔴 — listagens hardcoded; ações sem handler |
| Notas e Engajamento: edição de notas (P1/P2/T1/Participação) | `notas-engajamento/` | 🟡 — edição funciona via `ngModel` em memória; "Salvar Alterações" não persiste (`TODO`) |
| Notas e Engajamento: gráficos de distribuição/evolução | `notas-engajamento/` | 🔴 — divs com valores parcialmente fixos, não refletem os dados reais dos alunos |
| Comunicação: chat com aluno | `comunicacao/` | 🟡 — resposta é adicionada ao histórico em memória, não persiste |
| Comunicação: avisos, fila de dúvidas | `comunicacao/` | ⚪🔴 — hardcoded; "Publicar Novo Aviso" e "Responder" sem handler |
| Comunicação: observações pedagógicas no perfil do aluno | `comunicacao/` | 🟡 — adiciona ao array em memória, não persiste |

### Área do Diretor
| Funcionalidade | Módulo | Status |
|---|---|---|
| Dashboard institucional (KPIs, gráfico de desempenho) | `diretor-dashboard/` | ⚪ — hardcoded; único módulo do sistema com gráfico real (Chart.js); campo `eventos` existe no `.ts` mas não é renderizado (código morto) |
| Gestão de Evasão (busca, filtros, lista de risco) | `gestao-evasao/` | ⚪🔴 — hardcoded; "Contatar", "Exportar Relatório", "Ver Detalhes" sem handler |
| Auditoria (KPIs, log de eventos, status de integrações) | `auditoria/` | ⚪ — hardcoded; painel "Google Workspace / Azure AD: Operacional" é decorativo, não reflete integração real |
| Relatórios (exportar PDF/Excel, filtros de período) | `relatorios-diretor/` | 🔴 — filtros de UI não alteram os dados exibidos; botões de exportação sem handler; texto "Sincronizado com API MEC" é enganoso (não há integração real) |
| Monitoramento Docente (corpo docente, top performers) | `monitoramento-docente/` | ⚪ — hardcoded; gráfico explicitamente nomeado `chart-container-mock` no próprio código |
| Matrículas (busca, filtros, documentação) | `matriculas-diretor/` | ⚪🔴 — hardcoded; "Exportar" e "Ver Detalhes" sem handler |

### Estrutural / Navegação
| Funcionalidade | Módulo | Status |
|---|---|---|
| Sidebars por perfil (Aluno/Professor/Diretor) | `menu/`, `menu-professor/`, `menu-diretor/` | 🟢 — funcionais, puramente de navegação |
| Dropdown de usuário (avatar) | `menu-usuario/` | 🟡 — itens "Notas" e "Calendário" desabilitados/placeholder |
| `menu-perfil/` | `menu-perfil/` | 🔴 — componente stub gerado pelo Angular CLI, não usado em nenhuma rota |
| `UserService` (`services/user.ts`) | `services/` | 🔴 — código morto/duplicado, aparentemente substituído pelo `AuthService` sem ser removido |

### Cobertura geral (fato consolidado)
- **Nenhum módulo usa `HttpClient`** — não há `environment.ts`, `proxy.conf.json` nem qualquer estrutura preparada para backend real.
- Persistência hoje se resume a `localStorage` (sessão e configurações) e estado em memória (perdido ao recarregar) nas demais telas.
- A maioria das telas do Diretor e do Aluno é **100% somente leitura com dados fixos**; a maioria das telas do Professor tem alguma interação em memória, mas nenhuma persiste.
- Não há guards de rota — controle de acesso por perfil é apenas visual (menu correto aparece, mas qualquer URL é acessível digitando direto).

---

## Requisitos Obrigatórios

### Funcionalidades críticas para o sistema funcionar
1. **Autenticação real** — validar credenciais contra uma base de usuários (hoje qualquer usuário/senha não vazios loga).
2. **Persistência real de dados de negócio** (cadastros, notas, frequência, avaliações, mensagens, observações) — hoje quase tudo é perdido ao recarregar a página.
3. **Guards de rota por autenticação e por perfil** — hoje qualquer pessoa acessa qualquer tela digitando a URL, mesmo sem estar logada ou tendo o perfil errado.
4. **Correção do logout incompleto na tela de Configurações** — hoje não limpa a sessão.
5. **Tela dedicada de troca de senha** — hoje "Alterar senha" apenas navega para `/perfil`, sem formulário.
6. **Persistência do cadastro de aluno** — hoje `finalizarCadastro()` não salva em lugar nenhum.
7. **Segmentação da tela de Configurações por perfil** (Aluno/Professor/Diretor) — já especificado na Task 3, a implementar na Task 4.
8. **Correção do bug `onEstudosChange`** na tela de Configurações — já identificado na Task 3.

### Requisitos não-funcionais
- **Segurança**
  - Senhas não podem ser tratadas em texto puro nem validadas apenas por "não vazio".
  - Sessão precisa expirar (hoje `localStorage` sem expiração nem token).
  - Rotas sensíveis (Diretor: auditoria, relatórios, matrículas; Professor: diário de classe, avaliações) precisam de controle de acesso por perfil no lado do cliente e, futuramente, validado pelo backend (Task 2/5).
- **Performance**
  - Telas com listas maiores (matrículas, gestão de evasão, monitoramento docente) devem prever paginação/carregamento assíncrono quando os dados deixarem de ser mockados.
  - Gráficos hoje desenhados manualmente com `<div>`s (Auditoria, Relatórios, Monitoramento Docente, Notas e Engajamento, Meus Níveis e Notas) devem ser padronizados — já existe Chart.js/ng2-charts no projeto, usado hoje só no Dashboard do Diretor.
- **Confiabilidade**
  - Eliminar features decorativas que sugerem integração inexistente (ex.: "Google Workspace / Azure AD: Operacional" em Auditoria, "Sincronizado com API MEC" em Relatórios) — hoje induzem o usuário a um falso estado de integração.
- **Manutenibilidade**
  - Remover código morto identificado: `menu-perfil/` (stub não usado), `UserService` (`services/user.ts`, duplicado do `AuthService`), campo `eventos` não renderizado no Dashboard do Diretor.
  - Consolidar lógica de `load/save/merge` de `localStorage`, hoje repetida (relevante para a Task 4, já apontado na Task 3).

### Dependências entre funcionalidades
- **Autenticação real** (Task 2/5) é pré-requisito para **guards de rota por perfil** funcionarem de forma confiável — hoje o "perfil" é auto-declarado no login, então um guard client-side sozinho não é suficiente.
- **Persistência via backend** (Task 2/5) é pré-requisito para todas as funcionalidades hoje marcadas 🟡 (diário de classe, avaliações, notas e engajamento, comunicação, cadastro) deixarem de perder dados ao recarregar.
- A **segmentação de Configurações por perfil** (Task 3/4) depende da correção do bug `onEstudosChange` ser aplicada de forma consistente nos três novos componentes.
- A **sincronização de preferências entre dispositivos** (Task 2) depende da tela de Configurações já estar segmentada por perfil (Task 4), para não duplicar retrabalho.
- **Guards de rota** dependem de o `AuthService` expor o *role* de forma confiável (hoje já expõe via `signal`, mas o valor não é validado por nenhum backend).

---

## Mapeamento de Priorização

### Must Have
- Autenticação real (validação de credenciais).
- Guards de rota por autenticação e por perfil.
- Persistência real (backend) de: cadastro de aluno, frequência/diário de classe, avaliações e notas, mensagens/comunicação.
- Correção do logout quebrado na tela de Configurações.
- Correção do bug `onEstudosChange`.
- Segmentação da tela de Configurações por perfil (Aluno/Professor/Diretor).

### Should Have
- Tela dedicada de troca de senha.
- Padronização dos gráficos "feitos à mão" para Chart.js/ng2-charts (Auditoria, Relatórios, Monitoramento Docente, Notas e Engajamento, Meus Níveis e Notas).
- Implementação dos handlers de ação hoje ausentes (Exportar, Contatar, Ver Detalhes, Publicar Aviso, Responder, Nova Questão, editar/excluir).
- Remoção do código morto (`menu-perfil/`, `UserService`, campo `eventos` não usado).
- Remoção de painéis decorativos que sugerem integração inexistente (Auditoria, Relatórios).
- Sincronização de preferências de Configurações entre dispositivos via API.

### Nice to Have
- Paginação/carregamento assíncrono nas listagens maiores.
- Exportação real de relatórios (PDF/Excel) e do diário de classe.
- Expiração/refresh de sessão.
- Consolidação da lógica de `load/save/merge` de `localStorage` em utilitário compartilhado (parte já prevista para a Task 4).
