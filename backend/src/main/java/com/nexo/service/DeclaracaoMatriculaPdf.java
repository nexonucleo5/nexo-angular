package com.nexo.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.nexo.domain.Matricula;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Declaração de matrícula em PDF — o documento que a secretaria emite quando o
 * responsável precisa comprovar o vínculo do aluno (transporte escolar, bolsa,
 * plano de saúde). Mesmo OpenPDF já usado no relatório de desempenho.
 */
@Service
public class DeclaracaoMatriculaPdf {

    private static final DateTimeFormatter DATA_EXTENSO =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("pt", "BR"));

    public byte[] gerar(Matricula matricula) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document();
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font fonteTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Paragraph titulo = new Paragraph("DECLARAÇÃO DE MATRÍCULA", fonteTitulo);
            titulo.setAlignment(Element.ALIGN_CENTER);
            doc.add(titulo);

            Paragraph escola = new Paragraph("Nexo — Gestão Escolar",
                    FontFactory.getFont(FontFactory.HELVETICA, 11));
            escola.setAlignment(Element.ALIGN_CENTER);
            doc.add(escola);
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(" "));

            String turma = matricula.getTurma() != null ? matricula.getTurma().getNome() : "turma a definir";
            String corpo = String.format(
                    "Declaramos, para os devidos fins, que %s encontra-se regularmente "
                            + "matriculado(a) nesta instituição de ensino, na turma %s, "
                            + "sob a matrícula nº %d, desde %s.",
                    matricula.getAluno().getNome(), turma, matricula.getId(),
                    matricula.getDataMatricula().format(DATA_EXTENSO));
            Paragraph texto = new Paragraph(corpo, FontFactory.getFont(FontFactory.HELVETICA, 12));
            texto.setAlignment(Element.ALIGN_JUSTIFIED);
            texto.setLeading(20f);
            doc.add(texto);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(" "));
            Paragraph data = new Paragraph(
                    "Emitida em " + LocalDate.now().format(DATA_EXTENSO) + ".",
                    FontFactory.getFont(FontFactory.HELVETICA, 12));
            data.setAlignment(Element.ALIGN_RIGHT);
            doc.add(data);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(" "));
            Paragraph assinatura = new Paragraph("_________________________________\nSecretaria Escolar",
                    FontFactory.getFont(FontFactory.HELVETICA, 11));
            assinatura.setAlignment(Element.ALIGN_CENTER);
            doc.add(assinatura);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar a declaração de matrícula", e);
        }
    }
}
