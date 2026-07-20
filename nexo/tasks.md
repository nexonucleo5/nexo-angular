# Nexo - Gestão Escolar - System
## Arquitetura de Desenvolvimento

---

# Task 0 - Descoberta de Requisitos
**Status:** ✅ Concluída

**Objetivo:** Estabelecer a base do projeto com levantamento completo de funcionalidades

## Entregáveis
- [x] Arquivo: `requisitos_funcionais.md`

## Conteúdo esperado
### Requisitos Já Implementados
- Lista de funcionalidades existentes no Angular atual
- Status e cobertura de cada funcionalidade

### Requisitos Obrigatórios
- Funcionalidades críticas para o sistema funcionar
- Requisitos não-funcionais (performance, segurança, etc)
- Dependências entre funcionalidades

### Mapeamento de Priorização
- Must Have
- Should Have
- Nice to Have

---

# Task 1 - Auditoria de Código
**Status:** ✅ Concluída

**Objetivo:** Identificar problemas técnicos e oportunidades de melhoria no código atual

## Entregáveis
- [x] Arquivo: `possiveis_alteracoes.md`

## Conteúdo esperado
### Análise de Erros Identificados
- Bugs presentes ou em potencial
- Anti-patterns encontrados
- Código duplicado ou desnecessário

### Problemas de Arquitetura
- Componentes mal estruturados
- Fluxos de dados confusos
- Gestão de estado inadequada

### Recomendações Técnicas
- Refatorações necessárias
- Melhorias de performance
- Padrões a serem implementados

> **Nota:** um bug real já foi encontrado durante a Task 3 (ver seção "Achados" abaixo), no componente de configurações. Quando esta task for executada para valer, ele deve entrar no `possiveis_alteracoes.md` junto com os demais problemas do restante do sistema.

---

# Task 2 - Planejamento de Arquitetura
**Status:** ✅ Concluída

**Objetivo:** Estruturar a comunicação Angular ↔ Java/Spring e separação de responsabilidades

## Entregáveis
- [x] Arquivo: `arquitetura_java.md`

## Conteúdo esperado
### Diagrama de Responsabilidades
- O que fica no Angular (Frontend)
- O que vai para Java/Spring (Backend)
- O que é compartilhado/sincronizado

### APIs e Contratos
- Endpoints RESTful esperados
- Estrutura de requests/responses
- Autenticação e autorização

### Funcionalidades a Migrar para Backend
- Lógica de negócio que sai do TypeScript
- Validações que passam para servidor
- Processamento de dados
- Acesso ao banco de dados

### Melhorias no Angular
- Componentes que podem ser simplificados
- Remoção de lógica pesada do frontend
- Otimização de state management

> **Nota:** a Task 3 já identificou que a persistência das configurações hoje é 100% `localStorage` (client-only). Quando esta task for feita, decidir se essas preferências passam a ser sincronizadas via API.

---

# Task 3 - Design das Telas de Configuração
**Status:** ✅ Concluída

**Objetivo:** Definir o escopo visual e funcional de cada perfil antes da implementação

## Entregáveis
- [x] Arquivo: `configuracao.md`
- [x] Esboços/Mockups das três telas (baixa/média fidelidade) — wireframes ASCII incluídos em `configuracao.md`

## Diagnóstico do código atual
Hoje existe **um único componente** (`Configuracoes`) compartilhado pelos 3 perfis, com 6 blocos: `Notificações`, `Gamificação`, `Estudos`, `Aparência`, `Privacidade`, `Conta`. O conteúdo é 100% voltado ao perfil **Aluno** — Professor e Diretor veem opções que não fazem sentido pra eles (XP, ranking, modo foco) e não têm nada que seja da própria função.

###  Bug real encontrado
No bloco **Estudos** do `configuracoes.html`, dois toggles (`autoAvancarTarefas` e `lembreteEstudoDiario`) passam o boolean já extraído para `onEstudosChange`, mas o método espera um `Event` completo (só `modoFoco` está correto). Isso quebra em tempo de execução. **Correção:** padronizar todos os handlers da tela para a assinatura `(key, checked: boolean)`, igual já é feito em Notificações, Gamificação e Privacidade.

### Outros pontos de atenção
- Persistência 100% `localStorage` — sem sincronia entre dispositivos (relevante para a Task 2/5).
- `alterarSenha()` e `editarPerfil()` navegam para a mesma rota (`/perfil`) — falta tela dedicada de troca de senha.
- Lógica de `load/save/merge` do storage é repetida por perfil — vira candidata a utilitário compartilhado na Task 4.

## Especificação por perfil

### Configurações - Perfil Aluno
| Bloco | Campo | Origem |
|---|---|---|
| Notificações | Aviso de tarefas novas / Lembretes de prazos / Mensagens de professores / E-mail | mantém |
| Gamificação | Exibir XP em tempo real / Animações de conquista / Ranking da turma | mantém |
| Estudos | Modo foco / Auto avançar tarefas / Lembrete de estudo diário | mantém (corrigir bug) |
| Aparência | Modo escuro / Animações da interface | mantém |
| **Acessibilidade** *(novo)* | Fonte ampliada / Alto contraste / Leitura em voz alta | novo |
| Privacidade | Perfil público / Exibir no ranking / **Visível para responsáveis** *(novo)* | mantém + 1 novo |
| Conta | Editar perfil / Alterar senha / Encerrar sessão | mantém |

### Configurações - Perfil Professor
| Bloco | Campo | Origem |
|---|---|---|
| Notificações | Novas entregas de alunos / Mensagens de alunos e responsáveis / Lembrete de correção pendente / E-mail | adaptado |
| **Avaliação** *(novo)* | Sugestão de correção automática / Exibir notas imediatamente / Permitir reenvio de atividade | novo |
| **Disponibilidade** *(novo)* | Aceitar contato fora do horário / Horário de início e fim de atendimento | novo |
| Aparência | Modo escuro / Animações da interface | mantém |
| Privacidade | Perfil visível para alunos / Exibir contato para responsáveis | adaptado |
| Conta | Editar perfil / Alterar senha / Encerrar sessão | mantém |

*Removido do Professor:* Gamificação e Estudos (são conceitos de consumo do aluno, não fazem sentido como preferência do professor).

### Configurações - Perfil Diretor
| Bloco | Campo | Origem |
|---|---|---|
| Notificações | Novos cadastros pendentes / Relatórios pendentes / Alertas do sistema / E-mail | novo |
| **Institucional** *(novo)* | Nome da instituição / Ano letivo ativo | novo |
| **Gestão** *(novo)* | Aprovar professores automaticamente / Exigir aprovação manual de cadastro de aluno | novo |
| **Integrações** *(novo)* | Exportar relatórios automaticamente / Sincronizar calendário institucional | novo |
| Aparência | Modo escuro / Animações da interface | mantém |
| Privacidade | Exibir dados da instituição publicamente | adaptado |
| Conta | Editar perfil / Alterar senha / Encerrar sessão | mantém |

*Removido do Diretor:* Gamificação e Estudos, pelo mesmo motivo do Professor.

## Resumo — o que sai, o que fica, o que entra
| Bloco original | Aluno | Professor | Diretor |
|---|---|---|---|
| Notificações | ✅ mantém | 🔄 adaptado | 🔄 adaptado |
| Gamificação | ✅ mantém | ❌ remove | ❌ remove |
| Estudos | ✅ mantém (bug a corrigir) | ❌ remove | ❌ remove |
| Aparência | ✅ mantém | ✅ mantém | ✅ mantém |
| Privacidade | ✅ mantém + 1 campo | 🔄 adaptado | 🔄 adaptado |
| Conta | ✅ mantém | ✅ mantém | ✅ mantém |
| Acessibilidade | ➕ novo | — | — |
| Avaliação | — | ➕ novo | — |
| Disponibilidade | — | ➕ novo | — |
| Institucional | — | — | ➕ novo |
| Gestão | — | — | ➕ novo |
| Integrações | — | — | ➕ novo |

## Documentação Técnica
- Separar em 3 pastas: `configuracao-aluno/`, `configuracao-professor/`, `configuracao-diretor/`, cada uma com seu próprio `model.ts`, `service.ts`, `component.ts/.html/.scss` — sem herdar entre si, pois as regras de cada perfil são diferentes o suficiente.
- Estados a gerenciar: um `signal<Settings>` por perfil, com `computed` por seção (igual ao padrão já usado hoje).
- Validações específicas: horário de início/fim (Professor) precisa checar `inicio < fim`.
- Fluxo de salvamento: automático via `effect()` a cada mudança (padrão já usado), candidato a virar chamada de API na Task 5.

## Pendências desta task
- [x] Gerar os esboços/mockups de baixa fidelidade das 3 telas — feito em `configuracao.md`

---

# Task 4 - Implementação das Telas de Configuração
**Status:** ✅ Concluída

**Objetivo:** Desenvolver as três telas com base no `configuracao.md` (Task 3)

## Entregáveis
- [x] Componentes: `configuracao-aluno.ts/html/scss` (+ `model.ts` e `service.ts`)
- [x] Componentes: `configuracao-professor.ts/html/scss` (+ `model.ts` e `service.ts`)
- [x] Componentes: `configuracao-diretor.ts/html/scss` (+ `model.ts` e `service.ts`)
- [x] Serviços de suporte (um por perfil) + utilitário de storage compartilhado (`configuracoes/settings-store.ts`)
- [x] Atualização: `configuracao.md` com ajustes realizados

## Como foi implementado
- A rota `/configuracoes` virou um **wrapper por role** (mesmo padrão do `matriculas-wrapper`), renderizando `app-configuracao-aluno|professor|diretor` conforme o usuário logado.
- `SettingsStore<T>` (utilitário único) concentra load/save/merge do `localStorage` **e** a sincronização com a API (`GET /api/configuracoes` servidor-vence + `PATCH /api/configuracoes/{secao}` com debounce de 600 ms) — os três services de perfil só declaram defaults e efeitos próprios.
- Bug do `onEstudosChange` corrigido: todos os handlers padronizados na assinatura `(key, checked: boolean)`.
- Validação `início < fim` da Disponibilidade (Professor) com feedback imediato no client; o backend reforça e responde 400.
- Estilos base (`.settings-card`, `.custom-switch`, `.btn-gradient`, inputs de texto/horário) extraídos para `configuracoes/settings-base.scss`, compartilhado pelas três telas.
- Bônus: o tema salvo agora é aplicado no boot (o `app.ts` forçava `light`), e o toggle de tema do header persiste na seção Aparência do perfil logado (localStorage + API). Defaults do client idênticos aos do backend (`ConfiguracaoService.defaultsPara`).

## Fluxo de Trabalho
### Desenvolvimento
- Seguir exatamente a especificação da Task 3
- Corrigir o bug do `onEstudosChange` já identificado
- Extrair lógica de load/save/merge do `localStorage` para um utilitário único
- Extrair estilos base (`.settings-card`, `.custom-switch`, `.btn-gradient`) para um SCSS compartilhado

### Refinamento
- Testar responsividade
- Validar fluxos de dados
- Ajustar conforme feedback

### Documentação
- Manter `configuracao.md` atualizado com mudanças
- Registrar decisões técnicas
- Documentar componentes criados

---

# Task 5 - Integração com Backend
**Status:** ✅ Concluída

**Objetivo:** Conectar o frontend às APIs do Java/Spring

## Entregáveis
- [x] Serviços HTTP configurados (`src/app/api/` + dashboards)
- [x] Interceptadores (autenticação, tratamento de erro)
- [x] Modelos (interfaces/types) sincronizados com backend (`core/api.models.ts`)
- [x] Arquivo: `integracao_backend.md`
- [x] **Todas as telas migradas** de arrays hardcoded para os endpoints reais

## Resumo da execução
Todas as telas do sistema passaram a consumir o backend Spring (ver tabela em `integracao_backend.md`):
- **Diretor:** Gestão de Evasão, Monitoramento Docente, Relatórios (com export PDF/XLSX real), Matrículas, Dashboard.
- **Professor:** Dashboard, Diário de Classe (frequência/conteúdo persistidos), Avaliações, Notas-Engajamento, Comunicação.
- **Aluno:** Dashboard (gamificação), Matérias/Disciplinas.
- **Todos:** Configurações por perfil (Task 4).

Subsistemas criados no backend para preservar a UI: gamificação do aluno (XP/ofensiva/ranking/atividades), grade de horários + feed do professor, métricas de evasão e monitoramento docente.

Bug crítico corrigido: double-encoding de UTF-8 na serialização JSON (afetava todos os acentos) → `spring.jackson.generator.escape-non-ascii`. Tela de Auditoria removida a pedido do time.

## Conteúdo esperado
### Serviços HTTP
- Chamadas às APIs de configuração
- Tratamento de erros
- Retry logic

### Autenticação
- Token JWT/Bearer
- Refresh token
- Logout seguro

### Sincronia de Dados
- Validação de tipos
- Transformação de dados
- Caching quando apropriado

---

# Task 6 - Deploy em Hospedagem
**Status:** ✅ Concluída

**Objetivo:** Publicar o projeto em nexo-gestao-escolar.com.br

## Entregáveis
- [x] Build otimizado gerado (`ng build --configuration production` → `dist/nexo/`)
- [x] Arquivo: `deploy.md` com instruções (guia completo)

## O que foi feito
- **Corrigido o erro do `index.html`**: o builder `@angular/build:application` gerava a saída em `dist/nexo/browser/`, mas o deploy procurava em `dist/nexo/`. Ajustado o `angular.json` com `outputPath: { base: "dist/nexo", browser: "" }` — agora o `index.html` fica na raiz de `dist/nexo/`.
- **GitHub Actions corrigido** (`.github/workflows/static.yml`): permissões do Pages (`pages: write`, `id-token: write`) e `environment: github-pages` que faltavam (causa comum de falha), caminho de upload → `nexo/dist/nexo`, `npm ci`, fallback de SPA (`404.html` = `index.html`), e um job que empacota o jar do backend.
- **Rewrite rules de SPA** documentadas (nginx `try_files`, `404.html` no Pages).
- **Perfil de produção do backend** (`application-prod.yml`): PostgreSQL via env vars, `NEXO_JWT_SECRET` obrigatório (sem default), CORS por env, seed desligado. Driver Postgres adicionado ao `pom.xml`.
- **`deploy.md`** cobre: build, CI/CD, duas topologias (Nginx single-domain recomendada com proxy de `/api` e `/ws`; ou Pages + backend à parte), banco H2→Postgres, variáveis de ambiente e checklist pós-deploy.

## Nota sobre a comunicação em tempo real
Foi adicionado um **chat WebSocket** professor ↔ diretor (Spring `TextWebSocketHandler` em `/ws/chat`, autenticado por token na query; front com `WebSocket` nativo e reconexão). O `deploy.md` inclui a config de proxy do `/ws` no Nginx (headers de Upgrade + `wss` sob HTTPS).

## Problema Atual & Solução
### Erro Identificado
- GitHub Actions não encontra `index.html`
- Caminho de build não está correto

### Passos de Resolução
1. **Verificar arquivo angular.json**
   - Confirmar outputPath está correto (geralmente `dist/nexo-gestao-escolar`)
   - Validar configuração de build

2. **Configurar GitHub Actions**
   - Build deve gerar arquivos em pasta `dist/`
   - Deploy precisa copiar a pasta correta para hospedagem

3. **Preparar Hospedagem**
   - Configurar caminho raiz do projeto
   - Ajustar rewrite rules (se necessário)
   - Testar acesso a `index.html`

4. **Automação**
   - Configurar CI/CD para deploy automático
   - Teste de funcionamento pós-deploy

---

## 📋 Resumo da Sequência Recomendada

Task 0 (Requisitos)        ✅
Task 1 (Auditoria)         ✅
Task 2 (Arquitetura)       ✅
Task 3 (Design Config)     ✅ 
Task 4 (Implementação)     ✅ 
Task 5 (Integração)        ✅
Task 6 (Deploy)            ✅