import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

// ── Interfaces ────────────────────────────────────────────────────────────────

export interface KpiCard {
  label: string;
  valor: string;
  tendencia: string;
  tendenciaClasse: string;
  icone: string;
  iconeBgClasse: string;
  iconeCorClasse: string;
}

export interface BarraDistribuicao {
  faixa: string;
  altura: number;   // percentual 0-100
  corClasse: string;
}

export interface PontoEvolucao {
  rotulo: string;
  alturaMedia: number;     // bottom% da linha verde
  alturaAprovacao: number; // bottom% da linha azul
}

export interface NivelEngajamento {
  label: string;
  qtd: number;
  corClasse: string;
  textClasse: string;
}

export interface CriterioEngajamento {
  titulo: string;
  descricao: string;
  nivel: string;
  nivelTextClasse: string;
  nivelBgClasse: string;
}

export interface AlunoNota {
  matricula: string;
  nome: string;
  foto: string;
  p1: string;
  p2: string;
  t1: string;
  part: string;
  media: number;
  mediaTextClasse: string;
  frequencia: number;
  engajamento: number;
  status: string;
  statusTextClasse: string;
  statusBgClasse: string;
}

// ── Componente ────────────────────────────────────────────────────────────────

@Component({
  selector: 'app-notas-engajamento',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './notas-engajamento.html',
  styleUrl: './notas-engajamento.scss',
})
export class NotasEngajamento {

  // ── Filtros ────────────────────────────────────────────────────────────────
  turmaSelecionada    = '2º Ano A';
  disciplinaSelecionada = 'Matemática';
  periodoSelecionado  = 'Este Bimestre';
  statusSelecionado   = 'Todos';

  turmas      = ['2º Ano A', '2º Ano B', '3º Ano A'];
  disciplinas = ['Matemática', 'Português', 'História'];
  periodos    = ['Este Bimestre', 'Último Bimestre', 'Ano Letivo'];
  statusOpcoes = ['Todos', 'Aprovados', 'Em Risco'];

  mensagemSucesso = '';

  // ── KPI Cards ──────────────────────────────────────────────────────────────
  kpis: KpiCard[] = [
    {
      label: 'Média Geral da Turma',
      valor: '7.8',
      tendencia: '+0.4',
      tendenciaClasse: 'text-green bg-green-dim',
      icone: 'bi-geo',
      iconeBgClasse: 'bg-green-dim',
      iconeCorClasse: 'text-green',
    },
    {
      label: 'Taxa de Aprovação',
      valor: '85%',
      tendencia: '+3%',
      tendenciaClasse: 'text-green bg-green-dim',
      icone: 'bi-check-lg',
      iconeBgClasse: 'bg-green',
      iconeCorClasse: 'text-white',
    },
    {
      label: 'Engajamento Médio',
      valor: '82%',
      tendencia: '+5%',
      tendenciaClasse: 'text-green bg-green-dim',
      icone: 'bi-activity',
      iconeBgClasse: 'bg-blue',
      iconeCorClasse: 'text-white',
    },
    {
      label: 'Alunos em Risco',
      valor: '4',
      tendencia: '-2',
      tendenciaClasse: 'text-blue bg-blue-dim',
      icone: 'bi-exclamation-triangle',
      iconeBgClasse: 'bg-amber',
      iconeCorClasse: 'text-white',
    },
  ];

  // ── Gráfico de barras (distribuição de notas) ──────────────────────────────
  distribuicaoNotas: BarraDistribuicao[] = [
    { faixa: '0-4',  altura: 12.5,  corClasse: 'bg-danger' },
    { faixa: '4-6',  altura: 31.25, corClasse: 'bg-amber'  },
    { faixa: '6-8',  altura: 75,    corClasse: 'bg-blue'   },
    { faixa: '8-10', altura: 81.25, corClasse: 'bg-green'  },
  ];

  // ── Gráfico de evolução ────────────────────────────────────────────────────
  pontosEvolucao: PontoEvolucao[] = [
    { rotulo: 'P1', alturaMedia: 30, alturaAprovacao: 8  },
    { rotulo: 'T1', alturaMedia: 30, alturaAprovacao: 8  },
    { rotulo: 'P2', alturaMedia: 30, alturaAprovacao: 8  },
    { rotulo: 'T2', alturaMedia: 30, alturaAprovacao: 8  },
  ];

  // ── Engajamento (pizza) ────────────────────────────────────────────────────
  niveisEngajamento: NivelEngajamento[] = [
    { label: 'Alto',  qtd: 18, corClasse: 'bg-green', textClasse: 'text-green' },
    { label: 'Médio', qtd: 10, corClasse: 'bg-blue',  textClasse: 'text-blue'  },
    { label: 'Baixo', qtd: 4,  corClasse: 'bg-amber', textClasse: 'text-amber' },
  ];

  criterios: CriterioEngajamento[] = [
    { titulo: 'Tempo de Acesso Semanal',    descricao: '12h 30min média',    nivel: 'Alto',  nivelTextClasse: 'text-green', nivelBgClasse: 'bg-green-dim' },
    { titulo: 'Participação em Atividades', descricao: '82% de conclusão',   nivel: 'Alto',  nivelTextClasse: 'text-green', nivelBgClasse: 'bg-green-dim' },
    { titulo: 'Visualização de Materiais',  descricao: '95% dos materiais',  nivel: 'Alto',  nivelTextClasse: 'text-green', nivelBgClasse: 'bg-green-dim' },
    { titulo: 'Interação em Fóruns',        descricao: '68% participam',     nivel: 'Médio', nivelTextClasse: 'text-amber', nivelBgClasse: 'bg-amber-dim' },
  ];

  // ── Tabela de alunos ───────────────────────────────────────────────────────
  alunos: AlunoNota[] = [
    {
      matricula: '2024001',
      nome: 'Ana Carolina Silva',
      foto: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150',
      p1: '8,5', p2: '9', t1: '8', part: '9,5',
      media: 8.75, mediaTextClasse: 'text-green',
      frequencia: 95, engajamento: 92,
      status: 'Excelente', statusTextClasse: 'text-green', statusBgClasse: 'bg-green-dim',
    },
    {
      matricula: '2024002',
      nome: 'Bruno Henrique Costa',
      foto: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150',
      p1: '7', p2: '7,5', t1: '8,5', part: '7',
      media: 7.50, mediaTextClasse: 'text-blue',
      frequencia: 88, engajamento: 75,
      status: 'Bom', statusTextClasse: 'text-blue', statusBgClasse: 'bg-blue-dim',
    },
    {
      matricula: '2024003',
      nome: 'Camila Rodrigues',
      foto: 'https://images.unsplash.com/photo-1438761681033-6461ffad8d80?w=150',
      p1: '5,5', p2: '6', t1: '6,5', part: '5',
      media: 5.75, mediaTextClasse: 'text-danger',
      frequencia: 72, engajamento: 58,
      status: 'Atenção', statusTextClasse: 'text-amber', statusBgClasse: 'bg-amber-dim',
    },
    {
      matricula: '2024004',
      nome: 'Daniel Santos',
      foto: 'https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=150',
      p1: '9', p2: '8,5', t1: '9,5', part: '10',
      media: 9.25, mediaTextClasse: 'text-green',
      frequencia: 98, engajamento: 95,
      status: 'Excelente', statusTextClasse: 'text-green', statusBgClasse: 'bg-green-dim',
    },
  ];

  // ── Getters ────────────────────────────────────────────────────────────────
  get alunosFiltrados(): AlunoNota[] {
    if (this.statusSelecionado === 'Todos') return this.alunos;
    if (this.statusSelecionado === 'Aprovados') return this.alunos.filter(a => a.media >= 6);
    if (this.statusSelecionado === 'Em Risco')  return this.alunos.filter(a => a.media < 6);
    return this.alunos;
  }

  // ── Métodos ────────────────────────────────────────────────────────────────
  salvarAlteracoes(): void {
    // TODO: integrar com API — enviar this.alunos ao backend
    console.log('Notas para salvar:', this.alunos);
    this.mensagemSucesso = '✅ Alterações salvas com sucesso!';
    setTimeout(() => (this.mensagemSucesso = ''), 3000);
  }
}