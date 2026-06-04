package com.industrialhub.backend.common.application.service;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.IEventHandler;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import java.io.InputStream;
import java.util.List;

/**
 * Classe base para renderers PDF do Industrial Hub — MSB.
 * <p>
 * Centraliza constantes de cor MSB, cabeçalho, título de seção, tabela estilizada e rodapé
 * com número de página. Subclasses devem implementar apenas a lógica de negócio específica
 * de cada relatório.
 * <p>
 * Não é um Spring bean — apenas herança de implementação.
 */
public abstract class AbstractMsbPdfRenderer {

    // -------------------------------------------------------------------------
    // Constantes de cor MSB (Manual de Marca)
    // -------------------------------------------------------------------------

    /** azulFiltrado — cor principal da marca */
    protected static final DeviceRgb MSB_BLUE     = new DeviceRgb(0x56, 0xA4, 0xBB);

    /** azulProfundo — texto e cabeçalhos escuros */
    protected static final DeviceRgb MSB_DARK     = new DeviceRgb(0x1F, 0x3A, 0x4A);

    /** branco — texto sobre fundo escuro */
    protected static final DeviceRgb MSB_WHITE    = new DeviceRgb(255, 255, 255);

    /** cinzaSeguro — linhas alternadas de tabela */
    protected static final DeviceRgb MSB_ROW_ALT  = new DeviceRgb(0xD9, 0xE4, 0xE8);

    // -------------------------------------------------------------------------
    // Logo
    // -------------------------------------------------------------------------

    /**
     * Tenta carregar o logo MSB do classpath ({@code classpath:images/msb-logo.png}).
     * Retorna {@code null} se o recurso não for encontrado, permitindo fallback por texto.
     */
    protected byte[] loadLogoBytes() {
        try (InputStream is = getClass().getResourceAsStream("/images/msb-logo.png")) {
            if (is == null) {
                return null;
            }
            return is.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Cabeçalho
    // -------------------------------------------------------------------------

    /**
     * Adiciona o cabeçalho padrão MSB ao documento:
     * <ul>
     *   <li>Logo PNG (se disponível no classpath) ou texto "MSB" em bold como substituto</li>
     *   <li>Título centralizado em {@code MSB_DARK}, 18pt bold</li>
     *   <li>Subtítulo centralizado em {@code MSB_BLUE}, 11pt</li>
     * </ul>
     */
    protected void addMsbHeader(Document doc, String title, String subtitle) {
        byte[] logoBytes = loadLogoBytes();
        if (logoBytes != null) {
            try {
                Image logo = new Image(ImageDataFactory.create(logoBytes));
                logo.setWidth(100);
                logo.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
                doc.add(logo);
            } catch (Exception e) {
                addLogoFallbackText(doc);
            }
        } else {
            addLogoFallbackText(doc);
        }

        doc.add(new Paragraph(title)
                .setFontColor(MSB_DARK)
                .setBold()
                .setFontSize(18)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(4));

        doc.add(new Paragraph(subtitle)
                .setFontColor(MSB_BLUE)
                .setFontSize(11)
                .setTextAlignment(TextAlignment.CENTER));

        doc.add(new Paragraph(" "));
    }

    private void addLogoFallbackText(Document doc) {
        doc.add(new Paragraph("MSB")
                .setBold()
                .setFontSize(22)
                .setFontColor(MSB_DARK)
                .setTextAlignment(TextAlignment.CENTER));
    }

    // -------------------------------------------------------------------------
    // Título de seção
    // -------------------------------------------------------------------------

    /**
     * Adiciona um título de seção: 14pt bold, cor {@code MSB_BLUE}, margem superior 12pt.
     */
    protected void addSectionTitle(Document doc, String title) {
        doc.add(new Paragraph(title)
                .setBold()
                .setFontSize(14)
                .setFontColor(MSB_BLUE)
                .setMarginTop(12));
    }

    // -------------------------------------------------------------------------
    // Tabela estilizada
    // -------------------------------------------------------------------------

    /**
     * Cria e adiciona ao documento uma tabela com colunas de largura igual.
     * <ul>
     *   <li>Cabeçalho: fundo {@code MSB_DARK}, texto branco bold</li>
     *   <li>Linhas pares: fundo {@code MSB_ROW_ALT}; linhas ímpares: branco</li>
     * </ul>
     *
     * @param doc     documento de destino
     * @param headers rótulos das colunas
     * @param rows    linhas de dados (cada linha deve ter o mesmo número de colunas que {@code headers})
     * @return a {@link Table} adicionada ao documento
     */
    protected Table addStyledTable(Document doc, String[] headers, List<String[]> rows) {
        float[] colWidths = new float[headers.length];
        for (int i = 0; i < colWidths.length; i++) {
            colWidths[i] = 1f;
        }
        Table table = new Table(UnitValue.createPercentArray(colWidths)).useAllAvailableWidth();

        // Cabeçalho
        for (String h : headers) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(h)
                            .setBold()
                            .setFontColor(MSB_WHITE)
                            .setFontSize(10))
                    .setBackgroundColor(MSB_DARK));
        }

        // Linhas de dados com zebra-striping
        for (int r = 0; r < rows.size(); r++) {
            DeviceRgb bg = (r % 2 == 1) ? MSB_ROW_ALT : MSB_WHITE;
            for (String value : rows.get(r)) {
                table.addCell(new Cell()
                        .add(new Paragraph(value == null ? "" : value).setFontSize(9))
                        .setBackgroundColor(bg));
            }
        }

        doc.add(table);
        return table;
    }

    // -------------------------------------------------------------------------
    // Rodapé com número de página
    // -------------------------------------------------------------------------

    /**
     * Registra um handler de rodapé em todas as páginas do PDF.
     * <p>
     * O rodapé exibe:
     * <ul>
     *   <li>Esquerda: {@code "{leftText} | Industrial Hub — MSB — Confidencial"}</li>
     *   <li>Direita: número da página atual</li>
     *   <li>Separador horizontal na cor {@code MSB_BLUE} acima do texto</li>
     * </ul>
     * Deve ser chamado <em>antes</em> de adicionar qualquer conteúdo ao documento.
     *
     * @param pdf      o {@link PdfDocument} alvo
     * @param leftText texto livre exibido à esquerda (ex.: nome do relatório)
     */
    protected void addFooterWithPageNumber(PdfDocument pdf, String leftText) {
        String footerLeft = leftText + " | Industrial Hub — MSB — Confidencial";
        pdf.addEventHandler(PdfDocumentEvent.END_PAGE, new FooterHandler(footerLeft));
    }

    // -------------------------------------------------------------------------
    // Handler de rodapé (classe privada estática)
    // -------------------------------------------------------------------------

    private static final class FooterHandler implements IEventHandler {

        private final String leftText;

        FooterHandler(String leftText) {
            this.leftText = leftText;
        }

        @Override
        public void handleEvent(Event event) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdfDoc = docEvent.getDocument();
            PdfPage page = docEvent.getPage();

            float pageWidth  = page.getPageSize().getWidth();
            float pageHeight = page.getPageSize().getHeight();
            float margin     = 36f;
            float footerY    = margin - 14f;
            float lineY      = margin - 4f;

            PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdfDoc);

            // Separador horizontal MSB_BLUE
            pdfCanvas.saveState()
                    .setStrokeColor(MSB_BLUE)
                    .setLineWidth(0.5f)
                    .moveTo(margin, lineY)
                    .lineTo(pageWidth - margin, lineY)
                    .stroke()
                    .restoreState();

            Rectangle footerRect = new Rectangle(margin, footerY, pageWidth - 2 * margin, 12f);

            try (Canvas canvas = new Canvas(pdfCanvas, footerRect)) {
                // Texto à esquerda
                canvas.add(new Paragraph(leftText)
                        .setFontSize(7)
                        .setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.LEFT)
                        .setMargin(0));

                // Número de página à direita
                int pageNumber = pdfDoc.getPageNumber(page);
                canvas.add(new Paragraph(String.valueOf(pageNumber))
                        .setFontSize(7)
                        .setFontColor(ColorConstants.GRAY)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setMargin(0)
                        .setFixedPosition(margin, footerY, pageWidth - 2 * margin));
            }

            pdfCanvas.release();
        }
    }
}
