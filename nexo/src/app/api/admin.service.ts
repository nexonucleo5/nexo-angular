import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, of } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  ContaDTO,
  ConteudoAdminDTO,
  DashboardAdminDTO,
  DesafioAdminDTO,
  MateriaCatalogoDTO,
  PageEnvelope,
  PapelConta,
  SenhaRedefinidaDTO,
} from '../core/api.models';

/** GET /api/admin/contas/resumo (usuario-api). */
type ResumoContas = Pick<DashboardAdminDTO,
  'contas' | 'contasInativas' | 'alunos' | 'professores' | 'diretores' | 'admins'>;

/** GET /api/admin/dashboard (pedagogico-api). */
type ResumoCatalogo = Pick<DashboardAdminDTO,
  'turmas' | 'materias' | 'conteudos' | 'conteudosDespublicados'>;

/**
 * Painel do administrador: contas e catálogo de conteúdo. Substituiu o
 * SecretariaService, que servia fila de matrícula, documentação e ocupação de
 * turma — assuntos do sistema de aula da escola, não deste.
 */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private http = inject(HttpClient);
  private api = `${environment.apiUrl}/admin`;

  /**
   * O painel junta três fontes, uma por backend: contas (usuario-api), catálogo
   * (pedagogico-api) e desafios (ainda no monólito). Os desafios são o único pedaço
   * dispensável: se o monólito estiver fora do ar, o painel abre com 0 em vez de falhar.
   */
  dashboard(): Observable<DashboardAdminDTO> {
    return forkJoin({
      contas: this.http.get<ResumoContas>(`${this.api}/contas/resumo`),
      catalogo: this.http.get<ResumoCatalogo>(`${this.api}/dashboard`),
      desafios: this.desafios().pipe(catchError(() => of([] as DesafioAdminDTO[]))),
    }).pipe(
      map(({ contas, catalogo, desafios }) => ({
        ...contas,
        ...catalogo,
        desafios: desafios.length,
        desafiosDespublicados: desafios.filter((d) => !d.publicado).length,
      })),
    );
  }

  // ── Contas ─────────────────────────────────────────────────────────────────

  contas(filtro: { papel?: PapelConta; busca?: string; page?: number; size?: number } = {}):
    Observable<PageEnvelope<ContaDTO>> {
    let params = new HttpParams();
    if (filtro.papel) params = params.set('papel', filtro.papel);
    if (filtro.busca) params = params.set('busca', filtro.busca);
    if (filtro.page != null) params = params.set('page', filtro.page);
    if (filtro.size != null) params = params.set('size', filtro.size);
    return this.http.get<PageEnvelope<ContaDTO>>(`${this.api}/contas`, { params });
  }

  /** Desativar derruba as sessões abertas da conta no servidor. */
  atualizarAtivo(id: number, ativo: boolean): Observable<ContaDTO> {
    return this.http.patch<ContaDTO>(`${this.api}/contas/${id}/ativo`, { ativo });
  }

  /** A senha em claro só existe nesta resposta — depois dela, só o hash. */
  redefinirSenha(id: number): Observable<SenhaRedefinidaDTO> {
    return this.http.post<SenhaRedefinidaDTO>(`${this.api}/contas/${id}/senha-provisoria`, {});
  }

  // ── Catálogo ───────────────────────────────────────────────────────────────

  catalogo(): Observable<MateriaCatalogoDTO[]> {
    return this.http.get<MateriaCatalogoDTO[]>(`${this.api}/catalogo`);
  }

  conteudosDaMateria(materiaId: number): Observable<ConteudoAdminDTO[]> {
    return this.http.get<ConteudoAdminDTO[]>(`${this.api}/catalogo/materias/${materiaId}/conteudos`);
  }

  publicarConteudo(id: number, publicado: boolean): Observable<ConteudoAdminDTO> {
    return this.http.patch<ConteudoAdminDTO>(`${this.api}/catalogo/conteudos/${id}/publicado`, { publicado });
  }

  /** A ordem é do conjunto: vai a lista inteira da matéria, na sequência desejada. */
  reordenar(materiaId: number, conteudoIds: number[]): Observable<ConteudoAdminDTO[]> {
    return this.http.patch<ConteudoAdminDTO[]>(
      `${this.api}/catalogo/materias/${materiaId}/ordem`, { conteudoIds });
  }

  desafios(): Observable<DesafioAdminDTO[]> {
    return this.http.get<DesafioAdminDTO[]>(`${this.api}/catalogo/desafios`);
  }

  publicarDesafio(id: number, publicado: boolean): Observable<DesafioAdminDTO> {
    return this.http.patch<DesafioAdminDTO>(`${this.api}/catalogo/desafios/${id}/publicado`, { publicado });
  }
}
