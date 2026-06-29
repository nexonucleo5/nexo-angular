import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface AlunoFrequencia {
  nome: string;
  matricula: string;
  presente: boolean | null;
  freqGeral: number;
  foto: string;
}

export interface HistoricoAula {
  titulo: string;
  data: string;
  presentes: number;
  ausentes: number;
}

export interface StatDiario {
  label: string;
  value: string;
}

@Component({
  selector: 'app-diario-classe-professor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './diario-classe-professor.html',
  styleUrl: './diario-classe-professor.scss',
})
export class DiarioClasseProfessor implements OnInit {

  // ── Filtros ────────────────────────────────────────────────────────
  turmaSelecionada     = '2º Ano A';
  disciplinaSelecionada = 'Matemática';
  dataAula             = new Date().toISOString().substring(0, 10);
  horarioAula          = '14:00';

  turmas      = ['1º Ano A', '1º Ano B', '2º Ano A', '2º Ano B', '3º Ano A'];
  disciplinas = ['Matemática', 'Física', 'Geometria'];

  // ── Formulário de conteúdo da aula ────────────────────────────────
  tituloAula       = '';
  descricaoAula    = '';
  observacoesAula  = '';

  // ── Feedback de UI ────────────────────────────────────────────────
  mensagemSucesso = '';
  mensagemErro    = '';

  // ── Stats dinâmicas ───────────────────────────────────────────────
  stats: StatDiario[] = [
    { label: 'Total de Alunos',   value: '0'  },
    { label: 'Presentes Hoje',    value: '0'  },
    { label: 'Ausentes Hoje',     value: '0'  },
    { label: 'Taxa de Frequência', value: '0%' },
  ];

  // ── Dados dos alunos ──────────────────────────────────────────────
  alunos: AlunoFrequencia[] = [
    { nome: 'Ana Carolina Silva',   matricula: '2024001', presente: true,  freqGeral: 95, foto: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100' },
    { nome: 'Bruno Henrique Costa', matricula: '2024002', presente: true,  freqGeral: 92, foto: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=100' },
    { nome: 'Camila Rodrigues',     matricula: '2024003', presente: false, freqGeral: 88, foto: 'https://images.unsplash.com/photo-1517841905240-472988babdf9?w=100' },
    { nome: 'Daniel Santos',        matricula: '2024004', presente: true,  freqGeral: 90, foto: 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=100' },
    { nome: 'Eduarda Ferreira',     matricula: '2024005', presente: true,  freqGeral: 97, foto: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=100' },
  ];

  // ── Histórico de aulas ────────────────────────────────────────────
  historico: HistoricoAula[] = [
    { titulo: 'Funções Quadráticas — Gráficos e Propriedades',      data: '30 Mai 2026', presentes: 28, ausentes: 4 },
    { titulo: 'Equações do Segundo Grau — Fórmula de Bhaskara',     data: '27 Mai 2026', presentes: 30, ausentes: 2 },
    { titulo: 'Sistemas Lineares — Método de Substituição',          data: '23 Mai 2026', presentes: 29, ausentes: 3 },
  ];

  // ── Lifecycle ─────────────────────────────────────────────────────
  ngOnInit(): void {
    this.calcularMetricas();
  }

  // ── Métodos ───────────────────────────────────────────────────────

  marcarTodosPresentes(): void {
    this.alunos.forEach(a => (a.presente = true));
    this.calcularMetricas();
  }

  calcularMetricas(): void {
    const total    = this.alunos.length;
    const presentes = this.alunos.filter(a => a.presente === true).length;
    const ausentes  = total - presentes;
    const taxa      = total > 0 ? Math.round((presentes / total) * 100) : 0;

    this.stats[0].value = total.toString();
    this.stats[1].value = presentes.toString();
    this.stats[2].value = ausentes.toString();
    this.stats[3].value = `${taxa}%`;
  }

  salvarFrequencia(): void {
    const payload = {
      turma:       this.turmaSelecionada,
      disciplina:  this.disciplinaSelecionada,
      data:        this.dataAula,
      horario:     this.horarioAula,
      frequencia:  this.alunos.map(a => ({ matricula: a.matricula, presente: a.presente })),
    };

    // TODO: substituir por chamada HTTP ao backend
    console.log('Payload de frequência para envio:', payload);
    this._exibirSucesso('✅ Frequência salva com sucesso!');
  }

  registrarConteudo(): void {
    if (!this.tituloAula.trim()) {
      this.mensagemErro = 'O título da aula é obrigatório.';
      return;
    }
    if (!this.descricaoAula.trim()) {
      this.mensagemErro = 'Descreva o conteúdo ministrado.';
      return;
    }

    const presentes = this.alunos.filter(a => a.presente === true).length;
    const ausentes  = this.alunos.filter(a => a.presente !== true).length;

    this.historico.unshift({
      titulo:    this.tituloAula,
      data:      new Date().toLocaleDateString('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' }),
      presentes,
      ausentes,
    });

    this.tituloAula      = '';
    this.descricaoAula   = '';
    this.observacoesAula = '';
    this._exibirSucesso('✅ Conteúdo registrado e adicionado ao histórico!');
  }

  atualizarDados(): void {
    // TODO: buscar alunos da turma selecionada via API
    console.log(`Carregando diário da turma: ${this.turmaSelecionada}`);
  }

  getClasseFreq(freq: number): string {
    if (freq >= 90) return 'text-success';
    if (freq >= 75) return 'text-warning';
    return 'text-danger';
  }

  // ── Helpers privados ─────────────────────────────────────────────
  private _exibirSucesso(msg: string): void {
    this.mensagemErro   = '';
    this.mensagemSucesso = msg;
    setTimeout(() => (this.mensagemSucesso = ''), 3500);
  }
}