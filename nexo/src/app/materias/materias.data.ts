export interface Subject {
  /** Identificador usado na rota /disciplina/:id */
  id: string;
  title: string;
  level: string;
  progress: number;
  completedLessons: number;
  totalLessons: number;
  iconClass: string;
  iconText?: string;
  /** Classe de cor (gradiente) aplicada ao ícone e à barra de progresso. */
  colorClass: string;
  /** Conteúdos que o aluno aprende nesta disciplina. */
  topics: string[];
}

/**
 * Catálogo de disciplinas do aluno (compartilhado entre a lista `Materias`
 * e a tela de detalhe `DisciplinaDetalhe`). Enquanto não há endpoint próprio,
 * é a fonte única desses dados no client.
 */
export const SUBJECTS: Subject[] = [
  {
    id: 'biologia',
    title: 'Biologia',
    level: 'Fundamental',
    progress: 45,
    completedLessons: 11,
    totalLessons: 24,
    iconClass: 'bi-heart-pulse',
    colorClass: 'subj-green',
    topics: ['Células e tecidos', 'Corpo humano', 'Ecossistemas', 'Genética básica', 'Reinos dos seres vivos'],
  },
  {
    id: 'matematica',
    title: 'Matemática',
    level: 'Médio',
    progress: 60,
    completedLessons: 22,
    totalLessons: 36,
    iconClass: 'bi-calculator',
    colorClass: 'subj-blue',
    topics: ['Funções', 'Geometria plana e espacial', 'Trigonometria', 'Estatística', 'Probabilidade'],
  },
  {
    id: 'ingles',
    title: 'Inglês',
    level: 'Médio',
    progress: 70,
    completedLessons: 21,
    totalLessons: 30,
    iconClass: '',
    iconText: 'US',
    colorClass: 'subj-amber',
    topics: ['Verb tenses', 'Vocabulary building', 'Reading comprehension', 'Conversation', 'Writing essays'],
  },
  {
    id: 'portugues',
    title: 'Português',
    level: 'Fundamental',
    progress: 52,
    completedLessons: 18,
    totalLessons: 34,
    iconClass: 'bi-book',
    colorClass: 'subj-pink',
    topics: ['Interpretação de texto', 'Gramática', 'Ortografia', 'Produção de redação', 'Literatura'],
  },
  {
    id: 'historia',
    title: 'História',
    level: 'Médio',
    progress: 40,
    completedLessons: 12,
    totalLessons: 30,
    iconClass: 'bi-hourglass-split',
    colorClass: 'subj-orange',
    topics: ['História Antiga', 'Idade Média', 'Brasil Colônia', 'Revolução Industrial', 'Guerras Mundiais'],
  },
  {
    id: 'geografia',
    title: 'Geografia',
    level: 'Fundamental',
    progress: 55,
    completedLessons: 16,
    totalLessons: 29,
    iconClass: 'bi-globe-americas',
    colorClass: 'subj-teal',
    topics: ['Cartografia', 'Relevo e clima', 'Geopolítica', 'Urbanização', 'Meio ambiente'],
  },
  {
    id: 'fisica',
    title: 'Física',
    level: 'Médio',
    progress: 33,
    completedLessons: 10,
    totalLessons: 32,
    iconClass: 'bi-lightning-charge',
    colorClass: 'subj-indigo',
    topics: ['Cinemática', 'Leis de Newton', 'Energia e trabalho', 'Eletricidade', 'Óptica'],
  },
  {
    id: 'quimica',
    title: 'Química',
    level: 'Médio',
    progress: 28,
    completedLessons: 8,
    totalLessons: 31,
    iconClass: 'bi-droplet-half',
    colorClass: 'subj-cyan',
    topics: ['Tabela periódica', 'Ligações químicas', 'Reações químicas', 'Estequiometria', 'Química orgânica'],
  },
  {
    id: 'artes',
    title: 'Artes',
    level: 'Fundamental',
    progress: 80,
    completedLessons: 20,
    totalLessons: 25,
    iconClass: 'bi-palette',
    colorClass: 'subj-purple',
    topics: ['História da arte', 'Desenho e pintura', 'Música', 'Teatro', 'Arte digital'],
  },
  {
    id: 'educacao-fisica',
    title: 'Educação Física',
    level: 'Fundamental',
    progress: 68,
    completedLessons: 17,
    totalLessons: 25,
    iconClass: 'bi-dribbble',
    colorClass: 'subj-red',
    topics: ['Esportes coletivos', 'Ginástica', 'Saúde e nutrição', 'Atletismo', 'Jogos cooperativos'],
  },
];
