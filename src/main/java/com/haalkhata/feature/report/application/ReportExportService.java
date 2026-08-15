package com.haalkhata.feature.report.application;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JRDesignField;
import net.sf.jasperreports.engine.design.JRDesignLine;
import net.sf.jasperreports.engine.design.JRDesignRectangle;
import net.sf.jasperreports.engine.design.JRDesignSection;
import net.sf.jasperreports.engine.design.JRDesignStaticText;
import net.sf.jasperreports.engine.design.JRDesignStyle;
import net.sf.jasperreports.engine.design.JRDesignTextField;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.type.FillEnum;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.type.LineDirectionEnum;
import net.sf.jasperreports.engine.type.ModeEnum;
import net.sf.jasperreports.engine.type.OrientationEnum;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import org.springframework.stereotype.Service;

@Service
public class ReportExportService {

    private static final int PAGE_WIDTH = 842;
    private static final int PAGE_HEIGHT = 595;
    private static final int MARGIN = 30;
    private static final int COLUMN_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private static final Color HEADER_BG = new Color(0xEEEEEE);
    private static final Color GRID_LINE = new Color(0xCCCCCC);

    public byte[] exportPdf(ReportTable table) {
        return export(table, "pdf");
    }

    public byte[] exportXlsx(ReportTable table) {
        return export(table, "xlsx");
    }

    private byte[] export(ReportTable table, String format) {
        try {
            JasperDesign design = buildDesign(table);
            JasperReport report = JasperCompileManager.compileReport(design);            JasperPrint print = JasperFillManager.fillReport(
                    report, new HashMap<>(), new JRMapCollectionDataSource(new ArrayList<>(table.getRows())));
            if ("xlsx".equals(format)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                JRXlsxExporter exporter = new JRXlsxExporter();
                exporter.setExporterInput(new SimpleExporterInput(print));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
                exporter.exportReport();
                return out.toByteArray();
            }
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            throw new IllegalStateException("Failed to export report: " + table.getTitle(), e);
        }
    }

    private JasperDesign buildDesign(ReportTable table) throws JRException {
        JasperDesign design = new JasperDesign();
        design.setName("report");
        design.setLanguage("java");
        design.setProperty("net.sf.jasperreports.awt.ignore.missing.font", "true");
        design.setPageWidth(PAGE_WIDTH);
        design.setPageHeight(PAGE_HEIGHT);
        design.setOrientation(OrientationEnum.LANDSCAPE);
        design.setColumnWidth(COLUMN_WIDTH);
        design.setLeftMargin(MARGIN);
        design.setRightMargin(MARGIN);
        design.setTopMargin(40);
        design.setBottomMargin(30);

        JRDesignStyle base = new JRDesignStyle();
        base.setName("Base");
        base.setDefault(true);
        base.setFontName("Arial");
        base.setFontSize(9f);
        design.addStyle(base);

        JRDesignStyle title = new JRDesignStyle();
        title.setName("Title");
        title.setBold(true);
        title.setFontSize(16f);
        design.addStyle(title);

        JRDesignStyle header = new JRDesignStyle();
        header.setName("Header");
        header.setBold(true);
        header.setFontSize(9f);
        design.addStyle(header);

        for (ReportColumn c : table.getColumns()) {
            JRDesignField field = new JRDesignField();
            field.setName(c.getKey());
            field.setValueClass(Object.class);
            design.addField(field);
        }

        design.setTitle(buildTitleBand(table, title));
        design.setPageHeader(buildHeaderBand(table, header));
        ((JRDesignSection) design.getDetailSection()).addBand(buildDetailBand(table));
        design.setPageFooter(buildFooterBand());
        return design;
    }

    private JRDesignBand buildTitleBand(ReportTable table, JRDesignStyle title) {
        JRDesignBand band = new JRDesignBand();
        band.setHeight(26);
        JRDesignTextField tf = new JRDesignTextField();
        tf.setKey("title_tf");
        tf.setX(0);
        tf.setY(0);
        tf.setWidth(COLUMN_WIDTH);
        tf.setHeight(26);
        tf.setStyle(title);
        tf.setFontSize(16f);
        tf.setExpression(new JRDesignExpression("\"" + escape(table.getTitle()) + "\""));
        band.addElement(tf);
        return band;
    }

    private JRDesignBand buildHeaderBand(ReportTable table, JRDesignStyle header) {
        JRDesignBand band = new JRDesignBand();
        band.setHeight(34);

        JRDesignStaticText subtitle = new JRDesignStaticText();
        subtitle.setKey("subtitle_tf");
        subtitle.setX(0);
        subtitle.setY(0);
        subtitle.setWidth(COLUMN_WIDTH);
        subtitle.setHeight(14);
        subtitle.setFontSize(9f);
        subtitle.setText(table.getSubtitle() == null ? "" : table.getSubtitle());
        band.addElement(subtitle);

        JRDesignRectangle rect = new JRDesignRectangle();
        rect.setKey("header_rect");
        rect.setX(0);
        rect.setY(15);
        rect.setWidth(COLUMN_WIDTH);
        rect.setHeight(16);
        rect.setMode(ModeEnum.OPAQUE);
        rect.setFill(FillEnum.SOLID);
        rect.setBackcolor(HEADER_BG);
        band.addElement(rect);

        int x = 0;
        int i = 0;
        for (ReportColumn c : table.getColumns()) {
            JRDesignStaticText st = new JRDesignStaticText();
            st.setKey("col" + i + "_hdr");
            st.setX(x);
            st.setY(15);
            st.setWidth(c.getWidth());
            st.setHeight(16);
            st.setStyle(header);
            st.setHorizontalTextAlign(
                    c.isRightAlign() ? HorizontalTextAlignEnum.RIGHT : HorizontalTextAlignEnum.LEFT);
            st.setText(c.getHeader());
            band.addElement(st);
            x += c.getWidth();
            i++;
        }

        JRDesignLine line = new JRDesignLine();
        line.setKey("header_line");
        line.setX(0);
        line.setY(31);
        line.setWidth(COLUMN_WIDTH);
        line.setHeight(1);
        line.setDirection(LineDirectionEnum.TOP_DOWN);
        band.addElement(line);
        return band;
    }

    private JRDesignBand buildDetailBand(ReportTable table) {
        JRDesignBand band = new JRDesignBand();
        band.setHeight(19);

        int x = 0;
        int i = 0;
        for (ReportColumn c : table.getColumns()) {
            JRDesignTextField tf = new JRDesignTextField();
            tf.setKey("col" + i + "_tf");
            tf.setX(x);
            tf.setY(0);
            tf.setWidth(c.getWidth());
            tf.setHeight(18);
            tf.setFontSize(9f);
            tf.getLineBox().setPadding(2);
            tf.setHorizontalTextAlign(
                    c.isRightAlign() ? HorizontalTextAlignEnum.RIGHT : HorizontalTextAlignEnum.LEFT);
            tf.setBlankWhenNull(true);
            tf.setExpression(new JRDesignExpression("$F{" + c.getKey() + "}"));
            band.addElement(tf);
            x += c.getWidth();
            i++;
        }

        JRDesignLine line = new JRDesignLine();
        line.setKey("detail_line");
        line.setX(0);
        line.setY(18);
        line.setWidth(COLUMN_WIDTH);
        line.setHeight(1);
        line.setDirection(LineDirectionEnum.TOP_DOWN);
        line.getLinePen().setLineWidth(0.5f);
        line.getLinePen().setLineColor(GRID_LINE);
        band.addElement(line);

        return band;
    }

    private JRDesignBand buildFooterBand() {
        JRDesignBand band = new JRDesignBand();
        band.setHeight(18);
        JRDesignTextField tf = new JRDesignTextField();
        tf.setKey("page_tf");
        tf.setX(0);
        tf.setY(0);
        tf.setWidth(COLUMN_WIDTH);
        tf.setHeight(18);
        tf.setFontSize(8f);
        tf.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
        tf.setExpression(new JRDesignExpression("\"Page \" + $V{PAGE_NUMBER}"));
        band.addElement(tf);
        return band;
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}