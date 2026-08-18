import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AlunoCriado,
  AlunoResumoDTO,
  CadastroAlunoRequest,
  NotaDTO,
  ObservacaoDTO,
} from '../core/api.models';

/**
 * O que sobrou aqui é o que um sistema de aprendizado precisa do aluno. Endereço,
 * prontuário, histórico escolar em PDF e consulta de CEP saíram: eram a ficha
 * pessoal dele, e ela pertence ao sistema de aula da escola.
 */
@Injectable({ providedIn: 'root' })
export class AlunosService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/alunos`;

  /**
   * Os alunos que o operador alcança. O recorte é do servidor: o professor recebe
   * os das turmas que leciona, diretor e administrador recebem todos.
   */
  listar(): Observable<AlunoResumoDTO[]> {
    return this.http.get<AlunoResumoDTO[]>(this.api);
  }

  /** Cadastro de aluno — e-mail institucional e senha provisória são gerados no backend. */
  cadastrar(dados: CadastroAlunoRequest): Observable<AlunoCriado> {
    return this.http.post<AlunoCriado>(this.api, dados);
  }

  editarNotas(
    alunoId: number,
    notas: { disciplina?: string; periodo?: string; p1?: number; p2?: number; t1?: number; participacao?: number },
  ): Observable<NotaDTO> {
    return this.http.patch<NotaDTO>(`${this.api}/${alunoId}/notas`, notas);
  }

  listarObservacoes(alunoId: number): Observable<ObservacaoDTO[]> {
    return this.http.get<ObservacaoDTO[]>(`${this.api}/${alunoId}/observacoes`);
  }

  criarObservacao(alunoId: number, texto: string): Observable<ObservacaoDTO> {
    return this.http.post<ObservacaoDTO>(`${this.api}/${alunoId}/observacoes`, { texto });
  }
}
