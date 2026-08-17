import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService, RoleCliente } from '../services/auth.service';

interface Duvida {
  pergunta: string;
  resposta: string;
  /** Perfis para os quais a dúvida faz sentido; vazio = todos. */
  perfis?: RoleCliente[];
}

@Component({
  selector: 'app-suporte',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './suporte.html',
  styleUrl: './suporte.scss',
})
export class Suporte {
  private readonly auth = inject(AuthService);

  readonly usuario = computed(() => this.auth.usuarioLogado());

  /** Aberta por vez: FAQ com tudo expandido vira parede de texto. */
  readonly abertaIndice = signal<number | null>(null);

  alternar(indice: number): void {
    this.abertaIndice.update((atual) => (atual === indice ? null : indice));
  }

  private readonly todas: ReadonlyArray<Duvida> = [
    {
      pergunta: 'Esqueci minha senha. Como recupero?',
      resposta:
        'A senha é redefinida pela secretaria ou pela direção, que geram uma senha ' +
        'provisória nova. Ao entrar com ela, troque em Configurações → Conta → Alterar senha.',
    },
    {
      pergunta: 'Por que não vejo algumas matérias?',
      resposta:
        'A lista mostra apenas as matérias da sua etapa de ensino. Quem está no ensino ' +
        'médio não cursa Ciências, e quem está no fundamental não cursa Física, Química ' +
        'ou Biologia — por isso elas não aparecem.',
      perfis: ['aluno'],
    },
    {
      pergunta: 'Liguei o modo foco e não acho o menu.',
      resposta:
        'O modo foco esconde a barra lateral de propósito, para tirar distrações. Para sair, ' +
        'use o botão "Sair do modo foco" no topo da tela, ou desligue em Configurações → Estudos.',
      perfis: ['aluno'],
    },
    {
      pergunta: 'Não consigo lançar nota de uma matéria.',
      resposta:
        'Cada professor lança notas e avaliações apenas das matérias que leciona, e somente ' +
        'nas turmas que são dele. Se a atribuição estiver errada, a direção ajusta no cadastro.',
      perfis: ['professor'],
    },
    {
      pergunta: 'O CEP não preencheu o endereço. E agora?',
      resposta:
        'A busca depende de um serviço externo. Se ele estiver fora do ar, os campos continuam ' +
        'editáveis: preencha o endereço à mão que o cadastro segue normalmente.',
      perfis: ['secretaria', 'diretor'],
    },
    {
      pergunta: 'A declaração de matrícula não é emitida.',
      resposta:
        'A declaração atesta vínculo vigente, então só sai para matrícula ativa. Se a matrícula ' +
        'estiver pendente, efetive-a antes; se estiver trancada, reative.',
      perfis: ['secretaria', 'diretor'],
    },
    {
      pergunta: 'Cadastrei uma data de nascimento e o sistema recusou.',
      resposta:
        'O cadastro recusa data futura e idade fora do plausível — aluno abaixo de 4 anos e ' +
        'professor abaixo de 18. Quase sempre é o ano digitado com um dígito trocado.',
      perfis: ['secretaria', 'diretor'],
    },
    {
      pergunta: 'Onde vejo o que já foi alterado no sistema?',
      resposta:
        'Em Auditoria. Login, alteração de notas, mudança de matrícula e emissão de documento ' +
        'ficam registrados com autor, data e detalhe.',
      perfis: ['diretor'],
    },
  ];

  /** Só o que interessa a quem está lendo — FAQ genérica é ruído. */
  readonly duvidas = computed(() => {
    const role = this.usuario()?.role;
    return this.todas.filter((d) => !d.perfis || (role && d.perfis.includes(role)));
  });

  readonly canais = [
    {
      icone: 'bi-envelope',
      titulo: 'Secretaria',
      detalhe: 'secretaria@nexo.escola.com',
      texto: 'Matrícula, documentos e dados cadastrais',
    },
    {
      icone: 'bi-headset',
      titulo: 'Suporte técnico',
      detalhe: 'suporte@nexo.escola.com',
      texto: 'Acesso, senha e erros do sistema',
    },
    {
      icone: 'bi-telephone',
      titulo: 'Telefone da escola',
      detalhe: '(11) 4002-8922',
      texto: 'Seg. a sex., das 8h às 17h',
    },
  ];

  /**
   * Dados que o suporte sempre pede na primeira resposta. Reunidos aqui para
   * quem abre o chamado já mandar tudo — e não descobrir depois que faltou.
   */
  readonly infoTecnica = computed(() => {
    const u = this.usuario();
    return [
      { rotulo: 'Perfil', valor: u ? u.role : '—' },
      { rotulo: 'Usuário', valor: u?.nome ?? '—' },
      { rotulo: 'Navegador', valor: navigator.userAgent.split(') ')[0] + ')' },
      { rotulo: 'Tela', valor: `${window.innerWidth}×${window.innerHeight}` },
    ];
  });

  readonly copiado = signal(false);

  copiarInfo(): void {
    const texto = this.infoTecnica().map((i) => `${i.rotulo}: ${i.valor}`).join('\n');
    navigator.clipboard.writeText(texto).then(() => {
      this.copiado.set(true);
      setTimeout(() => this.copiado.set(false), 2500);
    });
  }
}
