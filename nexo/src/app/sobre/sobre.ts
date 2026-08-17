import { Component, computed, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../services/auth.service';

/** O que cada perfil pode fazer — a mesma matriz que o backend aplica por rota. */
interface Permissao {
  perfil: string;
  icone: string;
  cor: string;
  faz: string[];
}

@Component({
  selector: 'app-sobre',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './sobre.html',
  styleUrl: './sobre.scss',
})
export class Sobre {
  private readonly auth = inject(AuthService);

  readonly versao = '1.0';
  readonly anoLetivo = 2026;

  readonly perfilAtual = computed(() => this.auth.usuarioLogado()?.role ?? null);

  /**
   * Descrição honesta do que cada papel alcança. Serve de referência para quem
   * usa ("por que não vejo isso?") e evita a explicação boca a boca de sempre.
   */
  readonly permissoes: ReadonlyArray<Permissao> = [
    {
      perfil: 'Aluno',
      icone: 'bi-mortarboard',
      cor: 'blue-gradient',
      faz: [
        'Vê apenas as matérias da própria etapa de ensino',
        'Acompanha notas, frequência e progresso pessoal',
        'Participa de desafios e do ranking da turma',
        'Fala com professores pelas mensagens',
      ],
    },
    {
      perfil: 'Professor',
      icone: 'bi-easel',
      cor: 'green',
      faz: [
        'Atua somente nas turmas que leciona',
        'Lança notas e avaliações apenas das próprias matérias',
        'Registra frequência e conteúdo no diário de classe',
        'Responde dúvidas e envia avisos',
      ],
    },
    {
      perfil: 'Secretaria',
      icone: 'bi-folder2-open',
      cor: 'orange',
      faz: [
        'Cadastra alunos e mantém endereço e documentação',
        'Efetiva, tranca, cancela e transfere matrículas',
        'Emite declaração de matrícula em PDF',
        'Acompanha a fila de pendências e as vagas por turma',
      ],
    },
    {
      perfil: 'Diretor',
      icone: 'bi-buildings',
      cor: 'purple-gradient',
      faz: [
        'Enxerga a escola inteira, sem restrição de turma ou matéria',
        'Acompanha evasão, desempenho e monitoramento docente',
        'Cadastra professores e supervisiona a secretaria',
        'Consulta a auditoria de tudo que foi alterado',
      ],
    },
  ];

  readonly recursos: ReadonlyArray<{ icone: string; titulo: string; texto: string }> = [
    {
      icone: 'bi-shield-lock',
      titulo: 'Acesso por perfil',
      texto:
        'Cada papel enxerga só o que lhe cabe, e a regra é aplicada no servidor — ' +
        'não apenas escondendo botões na tela.',
    },
    {
      icone: 'bi-clock-history',
      titulo: 'Auditoria',
      texto:
        'Login, alteração de nota, mudança de matrícula e emissão de documento ' +
        'ficam registrados com autor, data e detalhe.',
    },
    {
      icone: 'bi-geo-alt',
      titulo: 'Endereço por CEP',
      texto:
        'O cadastro preenche o endereço a partir do CEP, consultando BrasilAPI ' +
        'com ViaCEP de reserva.',
    },
    {
      icone: 'bi-file-earmark-pdf',
      titulo: 'Documentos oficiais',
      texto:
        'Declaração de matrícula e relatórios de desempenho saem em PDF e ' +
        'planilha, prontos para entrega.',
    },
  ];
}
