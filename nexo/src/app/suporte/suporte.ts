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
        'A senha é redefinida pela administração ou pela direção, em Contas → Nova senha, ' +
        'que gera uma senha provisória. Ao entrar com ela, troque em Configurações → Conta → ' +
        'Alterar senha.',
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
      pergunta: 'Onde cadastro endereço, documentos ou histórico do aluno?',
      resposta:
        'Aqui não. Este sistema cuida de aprendizado e retenção de conteúdo, e guarda do aluno ' +
        'apenas nome, turma e progresso. Ficha cadastral, documentos, matrícula e rematrícula ' +
        'ficam no sistema de aula da escola.',
      perfis: ['admin', 'diretor', 'professor'],
    },
    {
      pergunta: 'Despubliquei um conteúdo por engano. Perdi o progresso dos alunos?',
      resposta:
        'Não. Despublicar só tira o conteúdo da tela do aluno; quem já concluiu continua com o ' +
        'registro. Basta publicar de novo em Catálogo que tudo volta como estava.',
      perfis: ['admin', 'diretor'],
    },
    {
      pergunta: 'Desativei uma conta. A pessoa sai na hora?',
      resposta:
        'As sessões abertas são encerradas e o login para de funcionar. O acesso que já estava ' +
        'em uso pode sobreviver por até 15 minutos, que é a validade do token em curso.',
      perfis: ['admin', 'diretor'],
    },
    {
      pergunta: 'Cadastrei uma data de nascimento de professor e o sistema recusou.',
      resposta:
        'O cadastro de professor recusa data futura e idade abaixo de 18 anos. Quase sempre é o ' +
        'ano digitado com um dígito trocado.',
      perfis: ['admin', 'diretor'],
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
      titulo: 'Administração',
      detalhe: 'admin@nexo.escola.com',
      texto: 'Contas de acesso e catálogo de conteúdo',
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
