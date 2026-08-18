import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AlunoCriado,
  CadastroAlunoRequest,
  EnderecoCepDTO,
  EnderecoDTO,
  EnderecoRequest,
  NotaDTO,
  ObservacaoDTO,
  ProntuarioDTO,
} from '../core/api.models';

@Injectable({ providedIn: 'root' })
export class AlunosService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/alunos`;

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

  /** PUT e não PATCH: o corpo é o endereço inteiro, campo ausente apaga o valor. */
  salvarEndereco(alunoId: number, endereco: EnderecoRequest): Observable<EnderecoDTO> {
    return this.http.put<EnderecoDTO>(`${this.api}/${alunoId}/endereco`, endereco);
  }

  /** Ficha completa do aluno — o que a secretaria abre quando o responsável liga. */
  prontuario(alunoId: number): Observable<ProntuarioDTO> {
    return this.http.get<ProntuarioDTO>(`${this.api}/${alunoId}/prontuario`);
  }

  historicoEscolar(alunoId: number): Observable<Blob> {
    return this.http.get(`${this.api}/${alunoId}/historico`, { responseType: 'blob' });
  }

  /**
   * Busca o endereço de um CEP. Quem fala com o provedor externo é o backend —
   * ver EnderecoCepDTO. `cep` pode vir formatado; o servidor normaliza.
   */
  buscarCep(cep: string): Observable<EnderecoCepDTO> {
    return this.http.get<EnderecoCepDTO>(
      `${environment.apiUrl}/cep/${cep.replace(/\D/g, '')}`,
    );
  }
}
