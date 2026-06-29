import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

// ── Interfaces ────────────────────────────────────────────────────────────────

export interface ChatMessage {
  remetente: 'aluno' | 'professor';
  texto: string;
  data: string;
}

export interface Mensagem {
  id: number;
  aluno: string;
  assunto: string;
  horario: string;
  prioridade: 'dificil' | 'medio' | 'facil';
  avatar: string;
  historico: ChatMessage[];
}

export interface NotaAluno {
  disciplina: string;
  valor: string;
  peso: number;
}

export interface HistoricoItem {
  atividade: string;
  data: string;
  nota: string;
}

export interface ObservacaoPedagogica {
  professor: string;
  data: string;
  texto: string;
}

export interface AlunoDetalhe {
  nome: string;
  matricula: string;
  turma: string;
  email: string;
  telefone: string;
  mediaGeral: string;
  frequencia: string;
  engajamento: string;
  avatar: string;
  notas: NotaAluno[];
  historico: HistoricoItem[];
  observacoes: ObservacaoPedagogica[];
}

export interface Aviso {
  titulo: string;
  conteudo: string;
  horario: string;
  turmas: string[];
  vistos: number;
  total: number;
}

export interface Duvida {
  aluno: string;
  tempo: string;
  status: 'Pendente' | 'Respondida';
  pergunta: string;
  materia: string;
  avatar: string;
}

export interface StatComunicacao {
  label: string;
  value: string;
  icon: string;
  color: string;
}

// ── Componente ────────────────────────────────────────────────────────────────

@Component({
  selector: 'app-comunicacao',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './comunicacao.html',
  styleUrl: './comunicacao.scss',
})
export class Comunicacao implements OnInit {

  abaAtiva        = 'mensagens';
  buscaMensagem   = '';
  buscaAluno      = '';
  turmaSelecionada = '2º Ano A';
  textoResposta   = '';
  novaObservacao  = '';

  stats: StatComunicacao[] = [
    { label: 'Mensagens Pendentes',     value: '12',  icon: 'bi-chat-left-dots',  color: 'orange' },
    { label: 'Dúvidas Não Respondidas', value: '8',   icon: 'bi-question-circle', color: 'red'    },
    { label: 'Avisos Publicados',       value: '15',  icon: 'bi-megaphone',       color: 'green'  },
    { label: 'Taxa de Resposta',        value: '92%', icon: 'bi-percent',         color: 'green'  },
  ];

  mensagens: Mensagem[] = [
    {
      id: 1,
      aluno: 'Ana Carolina Silva',
      assunto: 'Dúvida sobre Função Exponencial',
      horario: 'Hoje 14:30',
      prioridade: 'dificil',
      avatar: 'https://i.pravatar.cc/150?img=47',
      historico: [
        { remetente: 'aluno',     texto: 'Professora, não entendi a parte sobre crescimento exponencial...', data: 'Hoje 14:30' },
        { remetente: 'professor', texto: 'Olá Ana! Vou explicar novamente. O crescimento exponencial ocorre quando a taxa de variação é proporcional ao valor atual.', data: 'Hoje 14:45' },
      ],
    },
    {
      id: 2,
      aluno: 'Bruno Henrique Costa',
      assunto: 'Solicitação de Revisão de Nota',
      horario: 'Hoje 12:15',
      prioridade: 'medio',
      avatar: 'https://i.pravatar.cc/150?img=33',
      historico: [
        { remetente: 'aluno', texto: 'Gostaria de revisar a questão 3 da prova, acredito que houve um erro na correção.', data: 'Hoje 12:15' },
      ],
    },
    {
      id: 3,
      aluno: 'Camila Rodrigues',
      assunto: 'Material de Apoio',
      horario: 'Ontem 16:45',
      prioridade: 'facil',
      avatar: 'https://i.pravatar.cc/150?img=49',
      historico: [
        { remetente: 'aluno', texto: 'Obrigada pelo material extra! Ajudou muito na preparação para a prova.', data: 'Ontem 16:45' },
      ],
    },
  ];

  mensagemSelecionada: Mensagem | null = null;

  avisos: Aviso[] = [
    {
      titulo:   'Prova Bimestral — Próxima Semana',
      conteudo: 'A prova bimestral de Matemática será aplicada na próxima segunda-feira, 10 de junho, às 14h. Estudem os capítulos 5 a 8.',
      horario:  'Hoje 09:00',
      turmas:   ['2º Ano A', '2º Ano B'],
      vistos:   28,
      total:    32,
    },
    {
      titulo:   'Aula de Reforço — Trigonometria',
      conteudo: 'Aula extra de trigonometria na quinta-feira, 13/06, às 16h na sala 204. Participação opcional.',
      horario:  'Ontem 15:30',
      turmas:   ['2º Ano C'],
      vistos:   22,
      total:    35,
    },
  ];

  duvidas: Duvida[] = [
    {
      aluno:    'Daniel Santos',
      tempo:    'Há 2 horas',   // ✅ Corrigido: era 'Há 4 hours' (inglês)
      status:   'Pendente',
      pergunta: 'Como resolver sistemas lineares com 3 variáveis?',
      materia:  'Matemática',
      avatar:   'https://i.pravatar.cc/150?img=12',
    },
    {
      aluno:    'Eduarda Ferreira',
      tempo:    'Há 4 horas',
      status:   'Respondida',
      pergunta: 'Qual a diferença entre velocidade média e instantânea?',
      materia:  'Física',
      avatar:   'https://i.pravatar.cc/150?img=26',
    },
  ];

  alunos: AlunoDetalhe[] = [
    {
      nome:        'Ana Carolina Silva',
      matricula:   '2024001',
      turma:       '2º Ano A',
      email:       'ana.silva@nexo.escola.com',
      telefone:    '(11) 98765-4321',
      mediaGeral:  '8.75',
      frequencia:  '95%',
      engajamento: '92%',
      avatar:      'https://i.pravatar.cc/150?img=47',
      notas: [
        { disciplina: 'Matemática', valor: '8.5', peso: 3 },
        { disciplina: 'Física',     valor: '9.0', peso: 3 },
        { disciplina: 'Química',    valor: '8.0', peso: 2 },
      ],
      historico: [
        { atividade: 'Entregou Trabalho de Funções', data: '30 Mai', nota: '9.0' },
        { atividade: 'Prova de Trigonometria',       data: '27 Mai', nota: '8.5' }, // ✅ Corrigido: era 'activity'
        { atividade: 'Quiz de Geometria',            data: '23 Mai', nota: '7.5' },
        { atividade: 'Trabalho em Grupo',            data: '20 Mai', nota: '9.5' },
      ],
      observacoes: [
        { professor: 'Prof. Ana',    data: '01 Jun', texto: 'Excelente desempenho nas últimas avaliações. Continue assim!' },
        { professor: 'Prof. Carlos', data: '15 Mai', texto: 'Participação ativa nas aulas práticas de laboratório.' },
      ],
    },
    {
      nome:        'Bruno Henrique Costa',
      matricula:   '2024002',
      turma:       '2º Ano A',
      email:       'bruno.costa@nexo.escola.com',
      telefone:    '(11) 91234-5678',
      mediaGeral:  '7.20',
      frequencia:  '88%',
      engajamento: '79%',
      avatar:      'https://i.pravatar.cc/150?img=33',
      notas: [
        { disciplina: 'Matemática', valor: '6.5', peso: 3 },
        { disciplina: 'Física',     valor: '7.5', peso: 3 },
        { disciplina: 'Química',    valor: '7.8', peso: 2 },
      ],
      historico: [
        { atividade: 'Prova de Trigonometria', data: '27 Mai', nota: '6.5' },
        { atividade: 'Quiz de Geometria',      data: '23 Mai', nota: '8.0' },
      ],
      observacoes: [],
    },
  ];

  alunoSelecionado: AlunoDetalhe | null = null;

  ngOnInit(): void {
    if (this.alunos.length > 0) {
      this.alunoSelecionado = this.alunos[0];
    }
  }

  mudarAba(aba: string): void {
    this.abaAtiva = aba;
  }

  filtrarMensagens(): Mensagem[] {
    return this.mensagens.filter(m =>
      m.aluno.toLowerCase().includes(this.buscaMensagem.toLowerCase()) ||
      m.assunto.toLowerCase().includes(this.buscaMensagem.toLowerCase())
    );
  }

  selecionarMensagem(msg: Mensagem): void {
    this.mensagemSelecionada = msg;
  }

  enviarResposta(): void {
    if (!this.textoResposta.trim() || !this.mensagemSelecionada) return;

    this.mensagemSelecionada.historico.push({
      remetente: 'professor',
      texto:     this.textoResposta,
      data:      'Hoje ' + new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }),
    });

    this.textoResposta = '';
  }

  getPorcentagem(vistos: number, total: number): number {
    return Math.round((vistos / total) * 100);
  }

  alunosFiltrados(): AlunoDetalhe[] {
    return this.alunos.filter(a =>
      a.turma === this.turmaSelecionada &&
      (a.nome.toLowerCase().includes(this.buscaAluno.toLowerCase()) ||
       a.matricula.includes(this.buscaAluno))
    );
  }

  salvarObservacao(): void {
    if (!this.novaObservacao.trim() || !this.alunoSelecionado) return;

    this.alunoSelecionado.observacoes.unshift({
      professor: 'Prof. Responsável',
      data:      'Hoje',
      texto:     this.novaObservacao,
    });

    this.novaObservacao = '';
  }
}