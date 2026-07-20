# Nexo - Gestão Escolar — Design das Telas de Configuração

> Documento gerado na **Task 3** (Design das Telas de Configuração), definindo o escopo visual e funcional de cada perfil (Aluno, Professor, Diretor) antes da implementação (Task 4). Extraído do `tasks.md` para arquivo próprio, seguindo o mesmo padrão dos demais entregáveis do projeto.

---

## Diagnóstico do código atual
Hoje existe **um único componente** (`Configuracoes`) compartilhado pelos 3 perfis, com 6 blocos: `Notificações`, `Gamificação`, `Estudos`, `Aparência`, `Privacidade`, `Conta`. O conteúdo é 100% voltado ao perfil **Aluno** — Professor e Diretor veem opções que não fazem sentido pra eles (XP, ranking, modo foco) e não têm nada que seja da própria função.

### Bug real encontrado
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

---

## Esboços / Mockups de Baixa Fidelidade

Wireframes textuais das três telas, refletindo os blocos definidos na especificação acima. Todas as telas compartilham a mesma estrutura geral (cabeçalho + lista vertical de cards de seção, cada um com toggles/campos), mudando apenas os blocos.

### Configurações - Perfil Aluno

```
┌──────────────────────────────────────────────────────────┐
│  ⚙ Configurações                                          │
│  Gerencie suas preferências de conta e experiência        │
├──────────────────────────────────────────────────────────┤
│ ┌─ 🔔 Notificações ────────────────────────────────────┐  │
│ │ Aviso de tarefas novas             [====O]  (on)     │  │
│ │ Lembretes de prazos                [====O]  (on)     │  │
│ │ Mensagens de professores           [O====]  (off)    │  │
│ │ E-mail                             [====O]  (on)     │  │
│ └────────────────────────────────────────────────────┘  │
│ ┌─ 🎮 Gamificação ─────────────────────────────────────┐  │
│ │ Exibir XP em tempo real            [====O]  (on)     │  │
│ │ Animações de conquista             [====O]  (on)     │  │
│ │ Ranking da turma                   [====O]  (on)     │  │
│ └────────────────────────────────────────────────────┘  │
│ ┌─ 📚 Estudos ──────────────────────────────────────────┐  │
│ │ Modo foco                          [O====]  (off)    │  │
│ │ Auto avançar tarefas               [====O]  (on)     │  │
│ │ Lembrete de estudo diário          [====O]  (on)     │  │
│ └────────────────────────────────────────────────────┘  │
│ ┌─ 🎨 Aparência ────────────────────────────────────────┐  │
│ │ Modo escuro                        [O====]  (off)    │  │
│ │ Animações da interface             [====O]  (on)     │  │
│ └────────────────────────────────────────────────────┘  │
│ ┌─ ♿ Acessibilidade  (novo) ───────────────────────────┐  │
│ │ Fonte ampliada                     [O====]  (off)    │  │
│ │ Alto contraste                     [O====]  (off)    │  │
│ │ Leitura em voz alta                [O====]  (off)    │  │
│ └────────────────────────────────────────────────────┘  │
│ ┌─ 🔒 Privacidade ──────────────────────────────────────┐  │
│ │ Perfil público                     [====O]  (on)     │  │
│ │ Exibir no ranking                  [====O]  (on)     │  │
│ │ Visível para responsáveis  (novo)  [====O]  (on)     │  │
│ └────────────────────────────────────────────────────┘  │
│ ┌─ 👤 Conta ────────────────────────────────────────────┐  │
│ │ [ Editar perfil ]  [ Alterar senha ]  [ Encerrar ]    │  │
│ └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### Configurações - Perfil Professor

```
┌──────────────────────────────────────────────────────────┐
│  ⚙ Configurações                                          │
│  Gerencie suas preferências de conta e experiência        │
├──────────────────────────────────────────────────────────┤
│ ┌─ 🔔 Notificações  (adaptado) ────────────────────────┐  │
│ │ Novas entregas de alunos           [====O]  (on)     │  │
│ │ Mensagens de alunos/responsáveis   [====O]  (on)     │  │
│ │ Lembrete de correção pendente      [====O]  (on)     │  │
│ │ E-mail                             [====O]  (on)     │  │
│ └────────────────────────────────────────────────────┘  │
│ ┌─ 📝 Avaliação  (novo) ───────────────────────────────┐  │
│ │ Sugestão de correção automática    [O====]  (off)    │  │
│ │ Exibir notas imediatamente         [====O]  (on)     │  │
│ │ Permitir reenvio de atividade      [O====]  (off)    │  │
│ └────────────────────────────────────────────────────┘  │
│ ┌─ 🕐 Disponibilidade  (novo) ─────────────────────────┐  │
│ │ Aceitar contato fora do horário    [O====]  (off)    │  │
│ │ Horário de início    [ 08:00 ▾ ]                     │  │
│ │ Horário de fim        [ 18:00 ▾ ]                     │  │
│ │   (validação: início < fim)                           │  │
│ └────────────────────────────────────────────────────┘  │
│ ┌─ 🎨 Aparência ────────────────────────────────────────┐  │
│ │ Modo escuro                        [O====]  (off)    │  │
│ │ Animações da interface             [====O]  (on)     │  │
│ └────────────────────────────────────────────────────┘  │
│ ┌─ 🔒 Privacidade  (adaptado) ─────────────────────────┐  │
│ │ Perfil visível para alunos         [====O]  (on)     │  │
│ │ Exibir contato para responsáveis   [O====]  (off)    │  │
│ └────────────────────────────────────────────────────┘  │
│ ┌─ 👤 Conta ────────────────────────────────────────────┐  │
│ │ [ Editar perfil ]  [ Alterar senha ]  [ Encerrar ]    │  │
│ └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### Configurações - Perfil Diretor

```
┌──────────────────────────────────────────────────────────┐
│  ⚙ Configurações                                          │
│  Gerencie suas preferências de conta e experiência        │
├──────────────────────────────────────────────────────────┤
│ ┌─ 🔔 Notificações  (novo) ────────────────────────────┐  │
│ │ Novos cadastros pendentes          [====O]  (on)     │  │
│ │ Relatórios pendentes               [====O]  (on)     │  │
│ │ Alertas do sistema                 [====O]  (on)     │  │
│ │ E-mail                             [====O]  (on)     │  │
│ └────────────────────────────────────────────────────┘  │
│ ┌─ 🏫 Institucional  (novo) ───────────────────────────┐  │
│ │ Nome da instituição   [ Colégio Nexo            ]    │  │
│ │ Ano letivo ativo      [ 2026 ▾ ]                     │  │
│ └────────────────────────────────────────────────────┘  │
│ ┌─ 🗂 Gestão  (novo) ──────────────────────────────────┐  │
│ │ Aprovar professores automaticamente [O====] (off)    │  │
│ │ Exigir aprovação manual de aluno    [====O] (on)     │  │
│ └────────────────────────────────────────────────────┘  │
│ ┌─ 🔗 Integrações  (novo) ─────────────────────────────┐  │
│ │ Exportar relatórios automaticamente [O====] (off)    │  │
│ │ Sincronizar calendário institucional[====O] (on)     │  │
│ └────────────────────────────────────────────────────┘  │
│ ┌─ 🎨 Aparência ────────────────────────────────────────┐  │
│ │ Modo escuro                        [O====]  (off)    │  │
│ │ Animações da interface             [====O]  (on)     │  │
│ └────────────────────────────────────────────────────┘  │
│ ┌─ 🔒 Privacidade  (adaptado) ─────────────────────────┐  │
│ │ Exibir dados da instituição publicamente [O====](off)│  │
│ └────────────────────────────────────────────────────┘  │
│ ┌─ 👤 Conta ────────────────────────────────────────────┐  │
│ │ [ Editar perfil ]  [ Alterar senha ]  [ Encerrar ]    │  │
│ └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### Notas sobre os wireframes
- Estrutura de card por seção segue o padrão visual já existente (`.settings-card`), reaproveitado nos três perfis — só o conjunto de campos muda.
- `[====O]`/`[O====]` representam o estado do switch (ligado/desligado) apenas para ilustrar o layout; valores reais vêm do `signal<Settings>` de cada perfil.
- O bloco **Disponibilidade** (Professor) é o único com campos de horário em vez de apenas toggles — reflete a validação `início < fim` já prevista na especificação técnica.
- O bloco **Conta** é idêntico nos três perfis (Editar perfil / Alterar senha / Encerrar sessão).

---

## Ajustes realizados (Task 4 — implementação)

As três telas foram implementadas conforme a especificação acima, com os seguintes ajustes/decisões registrados:

### Estrutura criada
```
src/app/
├── configuracoes/                  # wrapper da rota /configuracoes + código compartilhado
│   ├── configuracoes.ts/html       # renderiza a tela do perfil logado (padrão matriculas-wrapper)
│   ├── settings-store.ts           # utilitário único: localStorage + sync com a API + tema
│   └── settings-base.scss          # .settings-card, .custom-switch, .btn-gradient, inputs
├── configuracao-aluno/             # model.ts, service.ts, ts/html/scss
├── configuracao-professor/         # model.ts, service.ts, ts/html/scss
└── configuracao-diretor/           # model.ts, service.ts, ts/html/scss
```

### Decisões técnicas
- **Sem herança entre os perfis** (como especificado): cada service declara apenas seus defaults e efeitos próprios e delega persistência/sincronização ao `SettingsStore<T>` — que unifica a lógica de load/save/merge antes repetida e a sincronização da Task 2 (GET com servidor-vence, PATCH por seção com debounce de 600 ms, cache local se a API cair).
- **Defaults do client = defaults do backend** (`ConfiguracaoService.defaultsPara`), evitando "flash" de valores divergentes antes do primeiro GET.
- **Bug do `onEstudosChange` corrigido**: todos os handlers das três telas usam a assinatura `(key, checked: boolean)`.
- **Validação `início < fim`** (Disponibilidade do Professor): checada no client com mensagem de erro na própria seção (valor inválido não é persistido); o backend reforça e responde `400 VALIDATION_ERROR`.
- **Aparência centralizada no store**: tema e animações são efeitos do `SettingsStore`, comuns aos três perfis; o toggle de tema do header (`app.ts`) passou a persistir na seção Aparência do perfil logado, e o tema salvo é aplicado no boot (antes o `app.ts` forçava `light`).
- **Acessibilidade (Aluno)**: `fonteAmpliada` e `altoContraste` aplicam classes globais (`html.fonte-ampliada`, `html.alto-contraste` em `styles.scss`); `leituraVozAlta` fica persistida como preferência para consumo futuro das telas de atividade.
- **Institucional (Diretor)**: `nomeInstituicao` é input de texto (persistido no blur, ignorando vazio) e `anoLetivoAtivo` é um select (2024–2027).
- O componente/serviço/modelo antigos (`configuracoes.service.ts`, `configuracoes.model.ts`, `configuracoes.scss`) foram removidos — o `configuracoes.ts` restante é só o wrapper.

### Pendência que permanece fora do escopo
- Tela dedicada de troca de senha (`alterarSenha()` ainda navega para `/perfil`); o endpoint `POST /api/usuarios/me/senha` já existe no backend.
