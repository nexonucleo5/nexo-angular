import { environment } from '../../environments/environment';

/**
 * Avatar exibido quando o usuário ainda não enviou uma foto. É um contorno
 * genérico sobre fundo neutro — nenhuma pessoa real aparece.
 */
export const AVATAR_PADRAO = 'assets/avatar-padrao.svg';

/**
 * Converte o valor guardado em `foto` na URL que a tag <img> deve usar.
 *
 * As fotos enviadas ficam no banco e são servidas em `/api/fotos/{id}`, um caminho
 * relativo à API. Em produção a API e o frontend estão no mesmo domínio e o caminho
 * já funciona; em desenvolvimento o Angular roda em :4200 e a API em :8080, então é
 * preciso apontar para a base correta. Sem foto (ou valor vazio), cai no avatar padrão.
 */
export function resolverFoto(foto: string | null | undefined): string {
  if (!foto || !foto.trim()) return AVATAR_PADRAO;

  const caminho = foto.trim();
  if (caminho.startsWith('/api/')) {
    // environment.apiUrl já termina em '/api' (absoluto em dev, relativo em produção)
    return environment.apiUrl + caminho.substring('/api'.length);
  }
  return caminho;
}
