import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AlunoDashboardService } from '../api/aluno-dashboard.service';
import { MateriaProgressoDTO } from '../core/api.models';
import { Subject, SUBJECTS } from './materias.data';

/** Filtros que a tela oferece — por progresso, que é o que o aluno usa para decidir. */
type FiltroProgresso = 'todas' | 'em-andamento' | 'nao-iniciadas' | 'concluidas';

@Component({
  selector: 'app-materias',
  imports: [CommonModule, FormsModule, RouterLink],
  standalone: true,
  templateUrl: './materias.html',
  styleUrls: ['./materias.scss'],
})
export class Materias {
  private readonly api = inject(AlunoDashboardService);

  readonly buscaDeTermos = signal('');
  readonly filtro = signal<FiltroProgresso>('todas');

  readonly carregando = signal(true);
  readonly erro = signal(false);

  /**
   * As matérias que o aluno cursa vêm do servidor (GET /api/aluno/materias) já
   * recortadas pela etapa dele — o do médio não recebe Ciências, o do fundamental
   * não recebe Física — e com o progresso real: conteúdos que ele concluiu ÷
   * total da matéria. Antes o percentual era número fixo no client, igual para
   * todo aluno e imune ao que ele fizesse.
   *
   * <p>Ícone, cor e tópicos continuam vindo de materias.data.ts: são apresentação,
   * não dado acadêmico. Matéria que o servidor manda e a tela não conhece aparece
   * com a aparência padrão em vez de sumir.
   */
  readonly materias = signal<Subject[]>([]);

  readonly opcoesFiltro: ReadonlyArray<{ valor: FiltroProgresso; rotulo: string }> = [
    { valor: 'todas', rotulo: 'Todas' },
    { valor: 'em-andamento', rotulo: 'Em andamento' },
    { valor: 'nao-iniciadas', rotulo: 'Não iniciadas' },
    { valor: 'concluidas', rotulo: 'Concluídas' },
  ];

  readonly filtradas = computed(() => {
    const termo = this.buscaDeTermos().trim().toLowerCase();
    const filtro = this.filtro();
    return this.materias().filter((m) => {
      const buscaOk = !termo || m.title.toLowerCase().includes(termo);
      const progressoOk =
        filtro === 'todas' ||
        (filtro === 'em-andamento' && m.progress > 0 && m.progress < 100) ||
        (filtro === 'nao-iniciadas' && m.progress === 0) ||
        (filtro === 'concluidas' && m.progress === 100);
      return buscaOk && progressoOk;
    });
  });

  /** Quantidade por filtro, exibida na própria aba — evita clicar para achar vazio. */
  readonly contagem = computed(() => {
    const lista = this.materias();
    return {
      todas: lista.length,
      'em-andamento': lista.filter((m) => m.progress > 0 && m.progress < 100).length,
      'nao-iniciadas': lista.filter((m) => m.progress === 0).length,
      concluidas: lista.filter((m) => m.progress === 100).length,
    } as Record<FiltroProgresso, number>;
  });

  readonly stats = computed(() => {
    const lista = this.materias();
    const total = lista.length;
    const concluidas = lista.reduce((soma, s) => soma + s.completedLessons, 0);
    const progressoMedio = total
      ? Math.round(lista.reduce((soma, s) => soma + s.progress, 0) / total)
      : 0;
    return [
      { label: 'Matérias Ativas', value: `${total}` },
      { label: 'Aulas Concluídas', value: `${concluidas}` },
      { label: 'Progresso Médio', value: `${progressoMedio}%` },
      { label: 'Tempo Total', value: `${concluidas * 45} min` },
    ];
  });

  constructor() {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.api.materias().subscribe({
      next: (doServidor) => {
        this.materias.set(doServidor.map((m) => this.comApresentacao(m)));
        this.carregando.set(false);
      },
      error: () => {
        this.erro.set(true);
        this.carregando.set(false);
      },
    });
  }

  /**
   * Junta o dado do servidor (nome, progresso) com a apresentação local (ícone,
   * cor, tópicos). Sem par local, usa a aparência padrão — some da tela seria
   * pior do que aparecer sem ícone bonito.
   */
  private comApresentacao(m: MateriaProgressoDTO): Subject {
    const local = SUBJECTS.find((s) => s.title.toLowerCase() === m.nome.toLowerCase());
    return {
      id: String(m.id), // rota /disciplina/:id passa a usar o id real da matéria
      title: m.nome,
      level: '',
      progress: m.percentual,
      completedLessons: m.conteudosConcluidos,
      totalLessons: m.totalConteudos,
      iconClass: local?.iconClass ?? 'bi-journal-text',
      iconText: local?.iconText,
      colorClass: local?.colorClass ?? 'subj-blue',
      topics: local?.topics ?? [],
    };
  }

  selecionarFiltro(valor: FiltroProgresso): void {
    this.filtro.set(valor);
  }

  limparBusca(): void {
    this.buscaDeTermos.set('');
  }
}
