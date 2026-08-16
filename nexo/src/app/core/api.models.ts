/**
 * Modelos compartilhados dos contratos da API (arquitetura_java.md).
 * Datas chegam em ISO-8601 UTC; formatação fica nos pipes do Angular.
 */

/** Envelope padrão de listagem paginada. */
export interface PageEnvelope<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Envelope de erro padrão devolvido pelo backend. */
export interface ApiErro {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  fields?: Record<string, string>;
}

/**
 * KPI genérico — substitui as 8 interfaces Stat* duplicadas apontadas na Task 1.
 * Populado pela resposta agregada da API, nunca calculado no client.
 */
export interface KpiStat {
  label: string;
  valor: string | number;
  icone?: string;
  variacao?: string;
  tom?: 'positivo' | 'negativo' | 'neutro';
}

// ── Turmas / Alunos / Matrículas ─────────────────────────────────────────────

export interface TurmaDTO {
  id: number;
  nome: string;
  anoLetivo: number;
  turno: string;
}

export interface AlunoCriado {
  id: number;
  nome: string;
  emailInstitucional: string;
  senhaProvisoria: string;
  matriculaId: number;
}

export interface CadastroAlunoRequest {
  nome: string;
  dataNascimento: string;
  sexo: string;
  /** Ano do ensino básico em que o aluno entra (turma existente). */
  turmaId: number | null;
}

// ── Matérias / Professores ───────────────────────────────────────────────────

export interface MateriaDTO {
  id: number;
  nome: string;
}

/** Conteúdo/documento de uma matéria — só Matemática tem exemplos por enquanto. */
export interface ConteudoMateriaDTO {
  id: number;
  titulo: string;
  /** Uma frase sobre o tópico — aparece no card, antes de abrir. */
  resumo: string;
  /** Corpo do texto. Linha em branco separa parágrafos. */
  texto: string;
  /** Exemplo concreto ou macete; pode não existir. */
  exemplo: string | null;
  /** Minutos estimados de leitura. */
  minutos: number;
  ordem: number;
}

export interface CadastroProfessorRequest {
  nome: string;
  dataNascimento: string;
  sexo: string;
  materiaIds: number[];
}

export interface ProfessorCriado {
  id: number;
  nome: string;
  disciplinas: string;
  emailInstitucional: string;
  senhaProvisoria: string;
}

export type StatusMatricula = 'ATIVA' | 'PENDENTE' | 'TRANCADA' | 'CANCELADA';
export type StatusDocumentacao = 'COMPLETA' | 'PENDENTE' | 'INCOMPLETA';

export interface MatriculaDTO {
  id: number;
  alunoId: number;
  aluno: string;
  turma: string | null;
  status: StatusMatricula;
  documentacao: StatusDocumentacao;
  dataMatricula: string;
}

/** GET /api/secretaria/dashboard */
export interface DashboardSecretariaDTO {
  totalAlunos: number;
  totalTurmas: number;
  matriculasAtivas: number;
  matriculasPendentes: number;
  matriculasTrancadas: number;
  documentacaoPendente: number;
}

/** GET /api/secretaria/pendencias — fila de trabalho, mais antiga primeiro */
export interface PendenciaDTO {
  matriculaId: number;
  alunoId: number;
  aluno: string;
  turma: string | null;
  status: StatusMatricula;
  documentacao: StatusDocumentacao;
  dataMatricula: string;
  aguardaEfetivacao: boolean;
  aguardaDocumentacao: boolean;
}

/** GET /api/secretaria/turmas/ocupacao */
export interface OcupacaoTurmaDTO {
  turmaId: number;
  turma: string;
  turno: string | null;
  alunos: number;
  capacidade: number;
  percentual: number;
}

// ── Diário de classe ─────────────────────────────────────────────────────────

export interface PresencaAluno {
  alunoId: number;
  nome: string;
  presente: boolean | null;
}

export interface ResumoFrequencia {
  total: number;
  presentes: number;
  ausentes: number;
  percentualPresenca: number;
}

export interface ConteudoDTO {
  id: number;
  data: string;
  titulo: string;
  descricao: string;
  observacoes: string;
  professor: string | null;
}

// ── Avaliações / Notas ───────────────────────────────────────────────────────

export type StatusAvaliacao = 'RASCUNHO' | 'PUBLICADA' | 'EM_CORRECAO' | 'CORRIGIDA';

export interface AvaliacaoDTO {
  id: number;
  titulo: string;
  disciplina: string;
  turma: string | null;
  tipo: string;
  status: StatusAvaliacao;
  data: string;
  entregas: number;
  pendentesCorrecao: number;
}

export interface QuestaoDTO {
  id: number;
  enunciado: string;
  disciplina: string;
  tipo: 'OBJETIVA' | 'DISSERTATIVA';
  dificuldade: 'FACIL' | 'MEDIA' | 'DIFICIL';
  criadaEm: string;
}

export interface NotaDTO {
  id: number;
  alunoId: number;
  aluno: string;
  disciplina: string;
  periodo: string;
  p1: number | null;
  p2: number | null;
  t1: number | null;
  participacao: number | null;
  media: number | null;
}

// ── Comunicação ──────────────────────────────────────────────────────────────

export interface MensagemDTO {
  id: number;
  autor: string;
  minha: boolean;
  texto: string;
  criadaEm: string;
}

export interface ConversaDTO {
  id: number;
  assunto: string;
  participante: string;
  papel: string;
  atualizadaEm: string;
  mensagens: MensagemDTO[];
}

export interface AvisoDTO {
  id: number;
  titulo: string;
  conteudo: string;
  autor: string;
  destino: string;
  criadoEm: string;
}

export interface DuvidaDTO {
  id: number;
  aluno: string;
  disciplina: string;
  pergunta: string;
  resposta: string | null;
  status: 'ABERTA' | 'RESPONDIDA';
  criadaEm: string;
  respondidaEm: string | null;
}

export interface ObservacaoDTO {
  id: number;
  alunoId: number;
  autor: string;
  texto: string;
  criadaEm: string;
}

// ── Gestão do Diretor ────────────────────────────────────────────────────────

export type RiscoEvasao = 'BAIXO' | 'MEDIO' | 'ALTO';

export interface AlunoRiscoDTO {
  alunoId: number;
  nome: string;
  turma: string | null;
  matricula: string;
  media: number;
  percentualFaltas: number;
  engajamento: number;
  risco: RiscoEvasao;
  foto: string | null;
  motivoPrincipal: string;
  ultimoAcessoEm: string | null;
  intervencoes: number;
  ultimaIntervencaoEm: string | null;
}

export interface SerieTurma {
  turma: string;
  media: number;
  frequencia: number;
  alunos: number;
}

export interface DesempenhoDTO {
  taxaAprovacao: number;
  mediaGeral: number;
  frequenciaMedia: number;
  engajamentoMedio: number;
  totalAlunos: number;
  turmas: SerieTurma[];
}

export interface ProfessorMonitorDTO {
  id: number;
  /** Id da conta de login — é por ele que o chat identifica o interlocutor.
   *  Numeração diferente do `id` acima. Nulo para docente ainda sem conta. */
  usuarioId: number | null;
  nome: string;
  disciplina: string;
  foto: string | null;
  turmas: string | null;
  correcoesPendentes: number;
  tempoRespostaDias: number;
  interacoesSemana: number;
  avaliacao: number;
  tarefasConcluidas: number;
  tarefasTotal: number;
  status: string;
}

// ── Dashboard do Aluno (gamificação) ─────────────────────────────────────────

export interface AtividadeAlunoDTO {
  titulo: string;
  materia: string;
  xp: number;
  progresso: number;
  icone: string;
  criadaEm: string;
}

export interface RankingItemDTO {
  posicao: number;
  nome: string;
  xp: number;
  foto: string | null;
  isMe: boolean;
}

export interface AlunoDashboardDTO {
  nome: string;
  xpSemana: number;
  metaSemanalXp: number;
  ofensivaDias: number;
  posicao: number;
  totalAlunos: number;
  turmaNome: string | null;
  tarefasFeitasHoje: number;
  tarefasHoje: number;
  xpTotal: number;
  nivel: number;
  atividades: AtividadeAlunoDTO[];
  ranking: RankingItemDTO[];
}

export interface AlunoNotaDTO {
  disciplina: string;
  media: number | null;
  p1: number | null;
  p2: number | null;
  t1: number | null;
  participacao: number | null;
}

// ── Dashboard do Professor ───────────────────────────────────────────────────

export interface TurmaResumoDTO {
  nome: string;
  disciplina: string;
  alunos: number;
  mediaGeral: number;
  progresso: number;
}

export interface ProximaAulaDTO {
  turma: string;
  disciplina: string;
  hora: string;
  data: string;
  sala: string;
  alunos: number;
}

export interface AlunoAtencaoDTO {
  nome: string;
  turma: string;
  mediaAtual: number;
  frequencia: number;
  status: 'critico' | 'atencao';
  foto: string | null;
  ultimaAtividade: string | null;
}

export interface AtividadeProfessorDTO {
  tipo: string;
  descricao: string;
  turma: string;
  icone: string;
  cor: string;
  criadaEm: string;
}

export interface ProfessorDashboardDTO {
  turmasAtivas: number;
  totalAlunos: number;
  correcoesPendentes: number;
  avaliacoesMes: number;
  turmas: TurmaResumoDTO[];
  proximasAulas: ProximaAulaDTO[];
  alunosAtencao: AlunoAtencaoDTO[];
  atividades: AtividadeProfessorDTO[];
}
