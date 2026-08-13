package com.nexo.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.nexo.api.ApiException;
import com.nexo.domain.Aluno;
import com.nexo.domain.Turma;
import com.nexo.repository.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Agregações institucionais (KPIs, séries por turma) e exportação real PDF/Excel —
 * substitui os números fixos de relatorios-diretor.ts e os botões sem handler.
 */
@Service
public class RelatorioService {

    public record SerieTurma(String turma, double media, double frequencia, int alunos) {}

    public record Desempenho(double taxaAprovacao, double mediaGeral, double frequenciaMedia,
                             double engajamentoMedio, long totalAlunos, List<SerieTurma> turmas) {}

    /** Exportações que podem estar sendo montadas ao mesmo tempo — ver {@link #exportar}. */
    private static final int EXPORTACOES_SIMULTANEAS = 2;

    /** Quanto uma requisição espera pela vez antes de levar 429. */
    private static final Duration ESPERA_NA_FILA = Duration.ofSeconds(5);

    private final Semaphore exportacoes = new Semaphore(EXPORTACOES_SIMULTANEAS);

    private final AlunoRepository alunos;
    private final TurmaRepository turmas;
    private final AgregadosAcademicos agregados;

    public RelatorioService(AlunoRepository alunos, TurmaRepository turmas, AgregadosAcademicos agregados) {
        this.alunos = alunos;
        this.turmas = turmas;
        this.agregados = agregados;
    }

    /**
     * KPIs institucionais e série por turma em 4 queries fixas (antes: {@code 4N + T + 4},
     * com N = alunos — o que fazia esta tela levar segundos em produção).
     */
    @Transactional(readOnly = true)
    public Desempenho desempenho(String periodo, String visao) {
        var indices = agregados.carregar(periodo);
        var todosAlunos = alunos.findAllComTurma();
        long total = todosAlunos.size();

        // Um único passo pelos alunos alimenta os quatro KPIs. A versão anterior
        // materializava um List<Double> com uma média boxed por aluno e depois o
        // percorria três vezes (média, aprovados, engajamento).
        double somaMedias = 0;
        int comNota = 0;
        int aprovados = 0;
        long somaEngajamento = 0;
        for (Aluno a : todosAlunos) {
            Double m = indices.media(a.getId());
            if (m != null) {
                somaMedias += m;
                comNota++;
                if (m >= 6.0) aprovados++;
            }
            somaEngajamento += a.getEngajamento();
        }

        double mediaGeral = arred(comNota == 0 ? 0 : somaMedias / comNota);
        double taxaAprovacao = comNota == 0 ? 0 : arred(aprovados * 100.0 / comNota);
        double engajamentoMedio = arred(total == 0 ? 0 : (double) somaEngajamento / total);

        var geral = indices.totalGeral();
        double frequenciaMedia = geral.total() == 0 ? 0
                : arred((geral.total() - geral.faltas()) * 100.0 / geral.total());

        Map<Long, List<Aluno>> porTurma = todosAlunos.stream()
                .filter(a -> a.getTurma() != null)
                .collect(Collectors.groupingBy(a -> a.getTurma().getId()));

        List<SerieTurma> series = new ArrayList<>(porTurma.size());
        for (Turma turma : turmas.findAll()) {
            var alunosTurma = porTurma.getOrDefault(turma.getId(), List.of());
            if (alunosTurma.isEmpty()) continue;
            double somaTurma = 0;
            int comNotaTurma = 0;
            double somaPresenca = 0;
            for (Aluno a : alunosTurma) {
                Double m = indices.media(a.getId());
                if (m != null) {
                    somaTurma += m;
                    comNotaTurma++;
                }
                somaPresenca += indices.percentualPresenca(a.getId());
            }
            double media = arred(comNotaTurma == 0 ? 0 : somaTurma / comNotaTurma);
            double freq = arred(somaPresenca / alunosTurma.size());
            series.add(new SerieTurma(turma.getNome(), media, freq, alunosTurma.size()));
        }

        return new Desempenho(taxaAprovacao, mediaGeral, frequenciaMedia, engajamentoMedio, total, series);
    }

    /**
     * O arquivo é montado inteiro em memória ({@link ByteArrayOutputStream}) antes de virar
     * resposta — é o jeito mais simples, e o certo enquanto o relatório couber com folga na
     * heap. O que não dá é deixar quantas montagens simultâneas o cliente quiser: no
     * contêiner de 0,5 vCPU do plano free, um punhado de exportações ao mesmo tempo faz o
     * {@code -XX:+ExitOnOutOfMemoryError} do Dockerfile cumprir o que promete e derrubar o
     * processo — levando junto todo mundo que estava usando o sistema.
     *
     * <p>O semáforo põe teto nisso. A espera curta é de propósito: são poucos diretores, e
     * dois clicarem no mesmo instante é uso legítimo, não abuso — quem chega junto espera a
     * vez em vez de tomar erro na cara. Passou disso, 429 com {@code Retry-After}, que o
     * cliente já sabe tratar.
     */
    @Transactional(readOnly = true)
    public byte[] exportar(String formato, String periodo, String visao) {
        // Formato validado antes de ocupar uma permissão: pedido malfeito não tem por que
        // entrar na fila nem fazer os outros esperarem.
        boolean pdf = "pdf".equalsIgnoreCase(formato);
        if (!pdf && !"xlsx".equalsIgnoreCase(formato)) {
            throw ApiException.badRequest("Formato de exportação inválido. Use pdf ou xlsx.");
        }

        boolean adquiriu;
        try {
            adquiriu = exportacoes.tryAcquire(ESPERA_NA_FILA.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // Desligamento gracioso em curso: devolve a marca de interrupção e desiste.
            Thread.currentThread().interrupt();
            throw ApiException.tooManyRequests("EXPORTACAO_OCUPADA",
                    "A geração do relatório foi interrompida. Tente novamente.", 5);
        }
        if (!adquiriu) {
            throw ApiException.tooManyRequests("EXPORTACAO_OCUPADA",
                    "Há relatórios sendo gerados no momento. Tente novamente em instantes.", 15);
        }

        try {
            Desempenho dados = desempenho(periodo, visao);
            return pdf ? exportarPdf(dados) : exportarXlsx(dados);
        } finally {
            exportacoes.release();
        }
    }

    private byte[] exportarPdf(Desempenho dados) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Paragraph cabecalho = new Paragraph("Nexo — Relatório de Desempenho Institucional", titulo);
            cabecalho.setAlignment(Element.ALIGN_CENTER);
            doc.add(cabecalho);
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(String.format(
                    "Taxa de aprovação: %.1f%%   |   Média geral: %.1f   |   Frequência média: %.1f%%   |   Engajamento médio: %.1f",
                    dados.taxaAprovacao(), dados.mediaGeral(), dados.frequenciaMedia(), dados.engajamentoMedio())));
            doc.add(new Paragraph(" "));

            PdfPTable tabela = new PdfPTable(4);
            tabela.setWidthPercentage(100);
            for (String h : List.of("Turma", "Alunos", "Média", "Frequência (%)")) {
                tabela.addCell(new Paragraph(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
            }
            for (SerieTurma s : dados.turmas()) {
                tabela.addCell(s.turma());
                tabela.addCell(String.valueOf(s.alunos()));
                tabela.addCell(String.format("%.1f", s.media()));
                tabela.addCell(String.format("%.1f", s.frequencia()));
            }
            doc.add(tabela);
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar PDF", e);
        }
    }

    private byte[] exportarXlsx(Desempenho dados) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet kpis = wb.createSheet("KPIs");
            String[][] linhasKpi = {
                    {"Taxa de aprovação (%)", String.valueOf(dados.taxaAprovacao())},
                    {"Média geral", String.valueOf(dados.mediaGeral())},
                    {"Frequência média (%)", String.valueOf(dados.frequenciaMedia())},
                    {"Engajamento médio", String.valueOf(dados.engajamentoMedio())},
                    {"Total de alunos", String.valueOf(dados.totalAlunos())},
            };
            for (int i = 0; i < linhasKpi.length; i++) {
                Row row = kpis.createRow(i);
                row.createCell(0).setCellValue(linhasKpi[i][0]);
                row.createCell(1).setCellValue(Double.parseDouble(linhasKpi[i][1]));
            }

            Sheet porTurma = wb.createSheet("Por turma");
            Row header = porTurma.createRow(0);
            String[] colunas = {"Turma", "Alunos", "Média", "Frequência (%)"};
            for (int i = 0; i < colunas.length; i++) header.createCell(i).setCellValue(colunas[i]);
            int linha = 1;
            for (SerieTurma s : dados.turmas()) {
                Row row = porTurma.createRow(linha++);
                row.createCell(0).setCellValue(s.turma());
                row.createCell(1).setCellValue(s.alunos());
                row.createCell(2).setCellValue(s.media());
                row.createCell(3).setCellValue(s.frequencia());
            }

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar Excel", e);
        }
    }

    private static double arred(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
