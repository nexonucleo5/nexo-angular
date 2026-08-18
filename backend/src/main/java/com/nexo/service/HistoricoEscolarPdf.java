package com.nexo.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Histórico escolar em PDF — o segundo documento oficial que a secretaria emite,
 * ao lado da declaração de matrícula. Sai do prontuário, então mostra exatamente
 * o que está lançado no sistema, sem redigitação.
 */
@Service
public class HistoricoEscolarPdf {

    private static final DateTimeFormatter DATA_EXTENSO =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("pt", "BR"));
    private static final DateTimeFormatter DATA_CURTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] gerar(ProntuarioService.ProntuarioDTO prontuario) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph titulo = new Paragraph("HISTÓRICO ESCOLAR",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16));
            titulo.setAlignment(Element.ALIGN_CENTER);
            doc.add(titulo);

            Paragraph escola = new Paragraph("Nexo — Gestão Escolar",
                    FontFactory.getFont(FontFactory.HELVETICA, 11));
            escola.setAlignment(Element.ALIGN_CENTER);
            doc.add(escola);
            doc.add(new Paragraph(" "));

            var id = prontuario.identificacao();
            Font rotulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
            Font valor = FontFactory.getFont(FontFactory.HELVETICA, 11);

            PdfPTable dados = new PdfPTable(2);
            dados.setWidthPercentage(100);
            dados.setWidths(new float[]{1f, 2.4f});
            linha(dados, "Aluno", id.nome(), rotulo, valor);
            linha(dados, "Matrícula", String.valueOf(prontuario.matricula() != null
                    ? prontuario.matricula().id() : "—"), rotulo, valor);
            linha(dados, "Turma", id.turma() != null ? id.turma() : "—", rotulo, valor);
            linha(dados, "Etapa", etapaPorExtenso(id.etapa()), rotulo, valor);
            linha(dados, "Nascimento", id.dataNascimento() != null
                    ? id.dataNascimento().format(DATA_CURTA) : "—", rotulo, valor);
            doc.add(dados);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Desempenho por disciplina",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
            doc.add(new Paragraph(" "));

            List<ProntuarioService.DesempenhoDTO> desempenho = prontuario.desempenho();
            if (desempenho.isEmpty()) {
                doc.add(new Paragraph("Nenhuma nota lançada até a presente data.", valor));
            } else {
                PdfPTable tabela = new PdfPTable(3);
                tabela.setWidthPercentage(100);
                tabela.setWidths(new float[]{2.2f, 1f, 1f});
                for (String cabecalho : List.of("Disciplina", "Período", "Média")) {
                    tabela.addCell(new Paragraph(cabecalho, rotulo));
                }
                for (var d : desempenho) {
                    tabela.addCell(new Paragraph(d.disciplina() == null ? "—" : d.disciplina(), valor));
                    tabela.addCell(new Paragraph(d.periodo() == null ? "—" : d.periodo(), valor));
                    tabela.addCell(new Paragraph(d.media() == null ? "—"
                            : String.format(Locale.of("pt", "BR"), "%.1f", d.media()), valor));
                }
                doc.add(tabela);

                if (prontuario.mediaGeral() != null) {
                    Paragraph media = new Paragraph(String.format(Locale.of("pt", "BR"),
                            "Média geral: %.1f", prontuario.mediaGeral()), rotulo);
                    media.setAlignment(Element.ALIGN_RIGHT);
                    media.setSpacingBefore(10f);
                    doc.add(media);
                }
            }

            doc.add(new Paragraph(" "));
            Paragraph emissao = new Paragraph(
                    "Documento emitido em " + LocalDate.now().format(DATA_EXTENSO) + ".", valor);
            emissao.setAlignment(Element.ALIGN_RIGHT);
            doc.add(emissao);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(" "));
            Paragraph assinatura = new Paragraph("_________________________________\nSecretaria Escolar",
                    FontFactory.getFont(FontFactory.HELVETICA, 11));
            assinatura.setAlignment(Element.ALIGN_CENTER);
            doc.add(assinatura);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar o histórico escolar", e);
        }
    }

    private static void linha(PdfPTable tabela, String chave, String texto, Font rotulo, Font valor) {
        tabela.addCell(new Paragraph(chave, rotulo));
        tabela.addCell(new Paragraph(texto == null ? "—" : texto, valor));
    }

    private static String etapaPorExtenso(String segmento) {
        if (segmento == null) return "—";
        return switch (segmento) {
            case "MEDIO" -> "Ensino Médio";
            case "FUNDAMENTAL" -> "Ensino Fundamental";
            default -> segmento;
        };
    }
}
