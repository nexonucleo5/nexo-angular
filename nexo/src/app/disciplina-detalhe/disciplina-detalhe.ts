import { Component, computed, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { SUBJECTS } from '../materias/materias.data';
import { MateriasService } from '../api/materias.service';
import { ConteudoMateriaDTO } from '../core/api.models';

/**
 * Detalhe de uma disciplina. O cabeçalho (progresso, ícone, nível) continua
 * vindo do mock em materias.data.ts — só os "Conteúdos da disciplina" passam a
 * vir do backend quando a matéria correspondente já tem documentos cadastrados
 * (piloto: Matemática); sem conteúdo real, cai de volta nos tópicos estáticos.
 */
@Component({
  selector: 'app-disciplina-detalhe',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './disciplina-detalhe.html',
  styleUrl: './disciplina-detalhe.scss',
})
export class DisciplinaDetalhe {
  private readonly materiasService = inject(MateriasService);

  /** Vem do parâmetro de rota /disciplina/:id (withComponentInputBinding). */
  readonly id = input<string>('');

  readonly disciplina = computed(() => SUBJECTS.find((s) => s.id === this.id()) ?? null);

  readonly carregandoConteudo = signal(true);
  readonly conteudos = signal<ConteudoMateriaDTO[]>([]);
  readonly topicoSelecionado = signal<ConteudoMateriaDTO | null>(null);

  constructor() {
    const d = this.disciplina();
    if (!d) {
      this.carregandoConteudo.set(false);
      return;
    }
    // Resolve o id numérico da Materia pelo título (os dois catálogos usam os mesmos nomes).
    this.materiasService.listar().subscribe({
      next: (materias) => {
        const materia = materias.find((m) => m.nome.toLowerCase() === d.title.toLowerCase());
        if (!materia) {
          this.carregandoConteudo.set(false);
          return;
        }
        this.materiasService.listarConteudos(materia.id).subscribe({
          next: (conteudos) => {
            this.conteudos.set(conteudos);
            this.carregandoConteudo.set(false);
          },
          error: () => this.carregandoConteudo.set(false),
        });
      },
      error: () => this.carregandoConteudo.set(false),
    });
  }

  selecionarTopico(c: ConteudoMateriaDTO): void {
    this.topicoSelecionado.set(c);
  }

  voltarTopicos(): void {
    this.topicoSelecionado.set(null);
  }
}
