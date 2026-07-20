/**
 * Exportação client-side para CSV — usada pelos botões "Exportar" que não têm
 * endpoint dedicado no backend (diário, matrículas, evasão). Gera o arquivo no
 * navegador a partir dos dados já carregados na tela.
 */
export function exportarCsv(nomeArquivo: string, colunas: string[], linhas: (string | number)[][]): void {
  const escapar = (v: string | number) => {
    const s = String(v ?? '');
    return /[";\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const conteudo = [colunas, ...linhas].map((linha) => linha.map(escapar).join(';')).join('\n');
  // BOM para o Excel reconhecer UTF-8 (acentos)
  const blob = new Blob(['﻿' + conteudo], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = nomeArquivo.endsWith('.csv') ? nomeArquivo : `${nomeArquivo}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}
