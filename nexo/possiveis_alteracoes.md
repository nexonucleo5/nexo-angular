# Nexo - Gestão Escolar — Auditoria de Código

> Documento gerado na **Task 1** (Auditoria de Código), a partir de análise estática do código Angular atual (`src/app`) e do levantamento factual já produzido na Task 0 (`requisitos_funcionais.md`).

---

## Análise de Erros Identificados

### Bugs presentes
| # | Bug | Local | Descrição |
|---|---|---|---|
| 1 | `onEstudosChange` recebe tipo errado | `configuracoes/configuracoes.html` (bloco Estudos) | Os toggles `autoAvancarTarefas` e `lembreteEstudoDiario` passam o `boolean` já extraído para `onEstudosChange`, mas o método espera um `Event` completo — só `modoFoco` está correto. Quebra em tempo de execução. Já identificado na Task 3; correção: padronizar todos os handlers para a assinatura `(key, checked: boolean)`, igual já é feito em Notificações, Gamificação e Privacidade. |
| 2 | Logout incompleto na tela de Configurações | `configuracoes/configuracoes.ts` | Botão "Encerrar sessão" navega para `/login` mas **não chama** `AuthService.logout()` — a sessão permanece salva em `localStorage`. Há `// TODO: chamar AuthService.logout() e redirecionar` no código. |
| 3 | Filtro de "eventos" nunca renderizado | `diretor-dashboard/diretor-dashboard.ts` | O array `eventos` é definido no `.ts` mas não existe bloco correspondente no template — código morto que nunca chega a aparecer na tela. |
| 4 | `atualizarDados()` não busca dados novos | `diario-classe-professor/diario-classe-professor.ts:~110` | Trocar o filtro de turma/disciplina apenas loga no console (`// TODO: buscar alunos da turma selecionada via API`) — a tabela de frequência não muda ao trocar o filtro, dando a falsa impressão de que o filtro funciona. |
| 5 | "Exportar Diário" não exporta nada | `diario-classe-professor/diario-classe-professor.ts` | O botão do cabeçalho na prática chama `salvarFrequencia()` — mesmo comportamento de salvar, não gera exportação nenhuma. |
| 6 | Filtros de período/visão sem efeito | `relatorios-diretor/relatorios-diretor.ts` | Os `ngModel` de período (Bimestre/Semestre/Geral) e visão (Geral/Pedagógico/Financeiro) não alteram nenhum dado exibido — nenhum getter os utiliza. |
| 7 | Textos que sugerem integração inexistente | `auditoria/auditoria.html`, `relatorios-diretor/relatorios-diretor.html` | Painel "Google Workspace / Azure AD: Operacional" e rótulo "Sincronizado com API MEC" são estáticos/decorativos — não refletem nenhuma integração real, e induzem o usuário a um falso estado do sistema. |
| 8 | Perfil de Professor sem tratamento em `perfil/` | `perfil/perfil.html` | Só existem blocos condicionais para Aluno e Diretor; um professor logado vê apenas o card de cabeçalho, sem conteúdo. |
| 9 | Professor cai no dashboard de Aluno | `dashboards/dashboards.ts` (ou `.html`) | A tela de `/dashboards` só decide entre visão de Aluno e `<app-dashboard-diretor>`; um professor logado nessa rota vê a visão de Aluno, não tem dashboard próprio ali (o dashboard correto do professor só existe em `/professor-dashboard`). |
| 10 | Saudação com nome fixo | `dashboards/dashboards.ts`/`.html` | Card de boas-vindas exibe "Gabriel" fixo, não usa o nome do usuário logado via `AuthService`. |

### Bugs em potencial (não travam a aplicação, mas geram comportamento incorreto silencioso)
- **Ausência de guards de rota**: qualquer usuário — mesmo não autenticado ou com role incompatível — acessa qualquer tela digitando a URL diretamente (`app.routes.ts` não tem nenhum `canActivate`). Ex.: um aluno pode acessar `/auditoria` ou `/relatorios` manualmente.
- **`updateSection(..., { [key]: value } as any)`** em `configuracoes/configuracoes.ts:33,44,61` — o cast para `any` mascara uma incompatibilidade real entre o tipo genérico esperado por `updateSection` e o payload passado; qualquer chave inválida passaria despercebida pelo compilador.
- **Botões sem `(click)` implementado**: "Ver todos" (diretor-dashboard), "Contatar"/"Ver Detalhes"/"Exportar Relatório" (gestão de evasão), "Ver Detalhes"/"Exportar" (matrículas), "Corrigir" (fila de correção), "Nova Questão"/editar/deletar (banco de questões), "Publicar Novo Aviso"/"Responder" (comunicação) — funcionam visualmente mas não disparam nenhuma ação, criando a expectativa de uma funcionalidade que não existe.

### Anti-patterns encontrados
- **Dois serviços de sessão/usuário coexistindo**: `services/auth.service.ts` (moderno, `signal<Usuario|null>`) e `services/user.ts` (antigo, `BehaviorSubject<string>` sem tipagem de role). `matriculas-wrapper/matriculas-wrapper.ts` importa `UserService`, `Subscription`, `OnInit`, `OnDestroy` sem nunca usá-los — resíduo de refatoração incompleta.
- **Strings mágicas de role** (`'aluno' | 'diretor' | 'professor'`) redeclaradas em pelo menos 3 lugares (`auth.service.ts:7`, `auth.service.ts:68`, `comunicacao.ts:8`) em vez de um único `type Role` exportado. O mapeamento de login→role em `auth.service.ts:36` (`if (loginLimpo === 'diretor' || loginLimpo === 'admin')`) é regra de negócio sensível embutida em comparação de string solta.
- **Manipulação direta do DOM disputada por dois lugares**: `app.ts:57` e `configuracoes/configuracoes.service.ts:18,25,44` chamam `document.documentElement.setAttribute('data-theme', ...)`/`classList.toggle(...)` de forma independente — duas fontes de verdade competindo pelo mesmo estado global do DOM.
- **`console.log`/`console.error` como comportamento final** (não só debug esquecido) em 8 pontos que representam ações que deveriam persistir: `avaliacoes.ts:142,165`, `cadastro.ts:97`, `configuracoes.service.ts:102`, `diario-classe-professor.ts:110,142`, `notas-engajamento.ts:206`, `auth.service.ts:23`.
- **Zero uso da sintaxe `@for`/`track`**: apesar do projeto estar em Angular 21, todos os 16 templates com listas usam `*ngFor` legado, nenhum com `trackBy` — no padrão atual do framework isso seria `@for (item of lista; track item.id)`.
- **4 padrões reativos diferentes coexistindo sem critério**: signals (`AuthService`, `ConfiguracoesService`, `menu-usuario`), `BehaviorSubject` (`UserService`, morto), `ngModel` solto em 9 templates, `ReactiveFormsModule` só em `cadastro`.
- **Dado de apresentação embutido no modelo/mock**: vários objetos mockados já trazem a classe CSS calculada manualmente junto do dado (ex. `notas-engajamento.ts:52-58,162-191` — `mediaTextClasse: 'text-green'` escrito à mão por registro, em vez de derivado de `media` via pipe/função), o mesmo padrão em `matriculas-diretor.ts` (`statusBgClasse`, `statusTextClasse`) e inline no template de `dashboard-professor.html:83-84`.

### Código duplicado ou desnecessário
- **8 interfaces `Stat*` idênticas** (mesmo shape `{label, value, icon, color}`) reimplementadas com nomes diferentes por tela: `StatAvaliacao`, `StatDesafio`, `StatDiario`, `StatRelatorio`, `StatAuditoria`, `StatProfessor`, `StatNivel`, `StatComunicacao`.
- **Interface `AtividadeRecente` com o mesmo nome mas campos diferentes** em `dashboard-professor.ts:40-47` e `meus-niveis-notas.ts:24-30` — risco de colisão caso migrem para um barrel/index compartilhado.
- **45+ interfaces locais** sem pasta `models/`/`interfaces/` compartilhada, muitas descrevendo o mesmo conceito de domínio (ex. "Aluno" reaparece como `AlunoRisco`, `AlunoAtencao`, `AlunoNota`, `AlunoFrequencia`, `AlunoDetalhe`, `NotaAluno` — 6 formas diferentes).
- **Lógica de busca/filtro client-side copy-pasted** (`x.campo.toLowerCase().includes(termo.toLowerCase())`) em `avaliacoes.ts`, `comunicacao.ts` (2x), `desafios.ts`, `materias.ts`, `matriculas-diretor.ts`, sem util/pipe compartilhado.
- **Lógica de load/save/merge de `localStorage`** repetida por perfil (apontado já na Task 3, confirmado agora como padrão geral do projeto).
- **319 cores hexadecimais hardcoded** em 23 dos 26 arquivos SCSS de componente — mesmo bloco de "cor por categoria" (blue/green/orange/purple, mesmos valores) repetido em `relatorios-diretor.scss`, `avaliacoes.scss`, `dashboard-professor.scss` e outros, quando poderia ser mixin/classe única em `styles.scss`.
- **`UserService` (`services/user.ts`)** — código morto, substituído pelo `AuthService` sem remoção.
- **`menu-perfil/`** — componente stub gerado pelo Angular CLI (`<p>menu-perfil works!</p>`), não registrado em nenhuma rota, não importado em lugar nenhum.

---

## Problemas de Arquitetura

### Componentes mal estruturados
- **`comunicacao.ts`** (290 linhas) concentra 9 interfaces + estado de chat + estado de avisos + estado de dúvidas + estado de detalhe de aluno num único componente — candidato natural a 4 subcomponentes (chat, avisos, dúvidas, ficha do aluno).
- **`notas-engajamento.ts`** (209 linhas) mistura KPIs, dois gráficos manuais, painel de engajamento e tabela editável de notas na mesma classe.
- **`avaliacoes.ts`** (168 linhas) tem 5 interfaces e 4 abas (ativas/correção/banco de questões/nova avaliação) sem nenhuma separação por aba.
- **Único módulo com separação model/service própria é `configuracoes/`** (`configuracoes.model.ts` + `configuracoes.service.ts`); os demais módulos, incluindo os igualmente complexos `comunicacao` e `avaliacoes`, misturam interface + estado + lógica dentro do próprio componente — inconsistência de padrão entre módulos, não apenas ausência de padrão.

### Fluxos de dados confusos
- **`MatriculasWrapper`** decide entre `<app-materias>` e `<app-matriculas-diretor>` com base em `AuthService.usuarioLogado().role`, mas ainda carrega um `UserService` não utilizado — trilha morta que confunde quem for dar manutenção no fluxo de decisão de role.
- **Duplicidade de fonte de verdade para o tema** (dark/light): tanto `app.ts` quanto `ConfiguracoesService` escrevem diretamente no `document.documentElement`, sem um único ponto de controle.
- **Professor sem fluxo de dashboard próprio na rota compartilhada `/dashboards`** — a lógica de decisão de qual dashboard mostrar cobre só 2 dos 3 perfis, criando um fluxo incompleto.
- **Filtros de UI que não filtram** (`diario-classe-professor`, `relatorios-diretor`) — o fluxo visual sugere que os dados mudam conforme o filtro, mas o dado exibido é sempre o mesmo, o que confunde tanto o usuário quanto quem for integrar esses filtros a uma API futura.

### Gestão de estado inadequada
- **4 padrões de estado coexistindo** (signals, `BehaviorSubject`, `ngModel` solto, Reactive Forms) sem um padrão consolidado — dificulta previsibilidade e testes.
- **Estado de negócio vivendo só em memória** (arrays mutados diretamente nos componentes) na maioria dos módulos de Professor e no `cadastro` — qualquer refresh de página perde o estado, e não há camada de service/estado intermediária que poderia, no futuro, ser trocada por chamadas HTTP sem reescrever os componentes.
- **Nenhum tratamento de erro nem estado de loading** em lugar nenhum do projeto — natural hoje, já que não há chamadas assíncronas reais, mas é uma lacuna estrutural relevante para a Task 5 (Integração com Backend).

---

## Recomendações Técnicas

### Refatorações necessárias
1. Unificar `AuthService` e `UserService`, removendo `services/user.ts` e o import morto em `matriculas-wrapper.ts`.
2. Extrair um único `type Role = 'aluno' | 'diretor' | 'professor'` compartilhado e substituir as redeclarações locais.
3. Criar pasta `src/app/models/` (ou `shared/models/`) com interfaces de domínio comuns (`KpiStat`, `Aluno` base, `AtividadeRecente` únicos) para substituir as 45+ interfaces locais duplicadas.
4. Corrigir o bug `onEstudosChange` (já detalhado na Task 3), padronizando a assinatura `(key, checked: boolean)` em todos os handlers de toggle do projeto, não só em Configurações.
5. Corrigir o logout incompleto em `configuracoes.ts`, chamando `AuthService.logout()` antes do redirect.
6. Consolidar o controle do atributo `data-theme` num único serviço (`ConfiguracoesService` ou um `ThemeService` dedicado), removendo a duplicidade com `app.ts`.
7. Remover `menu-perfil/` (stub não usado) ou implementá-lo, se houver uso planejado.
8. Extrair a lógica de load/save/merge de `localStorage` para um utilitário único (já previsto na Task 4).
9. Extrair um pipe/função de mapeamento `statusClasse(status)`/`notaClasse(media)` para substituir os campos de classe CSS calculados manualmente nos mocks.

### Melhorias de performance
1. Converter `app.routes.ts` para lazy loading (`loadComponent: () => import(...)`) — hoje ~2140 linhas de TypeScript de 20 componentes são carregadas eager no bundle inicial, incluindo telas de Diretor e Professor que um Aluno nunca acessa. Os blocos já estão comentados por perfil no próprio arquivo, o que torna a conversão praticamente mecânica.
2. Adicionar `track` (ou `trackBy` se mantida a sintaxe `*ngFor`) em todos os loops de lista — nenhum dos 16 templates com `*ngFor` tem isso hoje.
3. Migrar gráficos desenhados manualmente com `<div>`/SVG fixo (`meus-niveis-notas`, `auditoria`, `relatorios-diretor`, `monitoramento-docente`, `notas-engajamento`) para Chart.js/ng2-charts, já usado com sucesso em `diretor-dashboard` — além de corrigir a representação visual, evita reflow manual de barras com porcentagens fixas.
4. Adotar `@for`/`@if` (novo control flow do Angular) no lugar de `*ngFor`/`*ngIf`, alinhando o projeto com o padrão da própria versão do framework em uso (Angular 21).

### Padrões a serem implementados
1. **Guards de rota** por autenticação e por perfil (`CanActivateFn`), aproveitando os blocos já organizados por perfil em `app.routes.ts`.
2. **Padronizar em signals** como modelo de estado (já é o padrão mais moderno e mais usado no projeto — `AuthService`, `ConfiguracoesService`), migrando `ngModel` solto e o `BehaviorSubject` remanescente para esse mesmo modelo, à medida que os módulos forem sendo tocados (Task 4/5).
3. **Padronizar a estrutura de pasta por módulo** (`.ts` / `.html` / `.scss` / `.model.ts` / `.service.ts` quando o módulo tiver estado não-trivial), seguindo o exemplo já existente em `configuracoes/` — em vez de ser exceção, deveria ser a convenção para módulos como `comunicacao` e `avaliacoes`.
4. **Promover a variáveis CSS compartilhadas** as cores de destaque por categoria (purple/green/blue/orange/red) hoje hardcoded 319 vezes em 23 arquivos SCSS, complementando as variáveis de tema (`--bg-*`, `--text-*`, `--border-color`) já bem definidas em `styles.scss`.
5. **Adicionar testes** — hoje não existe nenhum arquivo `.spec.ts` no projeto; ao menos os fluxos com lógica real (máscaras de `cadastro`, cálculo de métricas de `diario-classe-professor`, `ConfiguracoesService`) deveriam ganhar cobertura antes da Task 5 (Integração com Backend), para não migrar comportamento sem rede de segurança.
6. **Remover ou implementar de fato** os botões sem `(click)` e os painéis decorativos que sugerem integração inexistente (Google Workspace/Azure AD, "Sincronizado com API MEC”) — decisão a ser tomada por tela na Task 4/5, mas o estado atual (metade funcional, metade decorativo) é o pior cenário para manutenção.
