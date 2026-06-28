import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

export interface TagDocumento {
  nome: string;
  status: 'ok' | 'pendente';
}

export interface Matricula {
  nome: string;
  iniciais: string;
  foto?: string;
  turma: string;
  nivelTurma: 'facil' | 'medio' | 'dificil';
  matricula: string;
  dataMatricula: string;
  responsavel: string;
  contrato: 'ok' | 'pendente' | 'atrasado';
  mec: 'ok' | 'atrasado';
  docCompletos: number;
  docTotal: number;
  docTags: TagDocumento[];
  temAlerta: boolean;
}

@Component({
  selector: 'app-matriculas-diretor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './matriculas-diretor.html',
  styleUrl: './matriculas-diretor.scss',
})
export class MatriculasDiretor {

  buscaTermo       = '';
  statusSelecionado = 'Todos os Status';

  statusOpcoes = ['Todos os Status', 'Em dia', 'Pendente', 'Atrasado'];

  matriculas: Matricula[] = [
    {
      nome: 'Gabriel Henrique Costa',
      iniciais: 'GH',
      foto: '/imagensProjeto/gabrielZapelini.jpeg',
      turma: '2º Ano A - Ensino Médio',
      nivelTurma: 'medio',
      matricula: '2024156',
      dataMatricula: '15/01/2024',
      responsavel: 'Maria Costa',
      contrato: 'ok',
      mec: 'ok',
      docCompletos: 5,
      docTotal: 6,
      docTags: [
        { nome: 'RG',      status: 'ok'      },
        { nome: 'CPF',     status: 'ok'      },
        { nome: 'Foto 3x4', status: 'pendente' },
      ],
      temAlerta: false,
    },
    {
      nome: 'Rafael Oliveira Lima',
      iniciais: 'RO',
      foto: undefined,
      turma: '3º Ano C - Ensino Médio',
      nivelTurma: 'dificil',
      matricula: '2024158',
      dataMatricula: '20/01/2024',
      responsavel: 'Patricia Lima',
      contrato: 'pendente',
      mec: 'atrasado',
      docCompletos: 4,
      docTotal: 6,
      docTags: [
        { nome: 'RG',               status: 'ok'      },
        { nome: 'Histórico Escolar', status: 'pendente' },
        { nome: 'Comprovante',       status: 'pendente' },
      ],
      temAlerta: true,
    },
    {
      nome: 'Ana Beatriz Ferreira',
      iniciais: 'AB',
      foto: 'https://i.pravatar.cc/150?u=ana',
      turma: '1º Ano B - Ensino Médio',
      nivelTurma: 'facil',
      matricula: '2024162',
      dataMatricula: '22/01/2024',
      responsavel: 'Carla Ferreira',
      contrato: 'ok',
      mec: 'ok',
      docCompletos: 6,
      docTotal: 6,
      docTags: [
        { nome: 'RG',      status: 'ok' },
        { nome: 'CPF',     status: 'ok' },
        { nome: 'Foto 3x4', status: 'ok' },
      ],
      temAlerta: false,
    },
    {
      nome: 'Carlos Eduardo Moreira',
      iniciais: 'CE',
      foto: 'https://i.pravatar.cc/150?u=carlos',
      turma: '2º Ano C - Ensino Médio',
      nivelTurma: 'medio',
      matricula: '2024175',
      dataMatricula: '25/01/2024',
      responsavel: 'José Moreira',
      contrato: 'ok',
      mec: 'atrasado',
      docCompletos: 3,
      docTotal: 6,
      docTags: [
        { nome: 'RG',               status: 'ok'      },
        { nome: 'CPF',              status: 'pendente' },
        { nome: 'Histórico Escolar', status: 'pendente' },
      ],
      temAlerta: true,
    },
  ];

  get matriculasFiltradas(): Matricula[] {
    return this.matriculas.filter(m => {
      const buscaOk =
        !this.buscaTermo ||
        m.nome.toLowerCase().includes(this.buscaTermo.toLowerCase()) ||
        m.matricula.includes(this.buscaTermo) ||
        m.responsavel.toLowerCase().includes(this.buscaTermo.toLowerCase());

      const statusOk =
        this.statusSelecionado === 'Todos os Status' ||
        (this.statusSelecionado === 'Em dia'   && !m.temAlerta) ||
        (this.statusSelecionado === 'Pendente' && m.contrato === 'pendente') ||
        (this.statusSelecionado === 'Atrasado' && m.mec === 'atrasado');

      return buscaOk && statusOk;
    });
  }

  getProgressoWidth(m: Matricula): string {
    return `${Math.round((m.docCompletos / m.docTotal) * 100)}%`;
  }
}