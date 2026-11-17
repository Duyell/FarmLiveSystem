package com.farmlive.service;

import com.farmlive.common.BusinessException;
import com.farmlive.dto.Dtos;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * 统计报表生成：Excel（POI，含趋势折线图）与 PDF（PDFBox，表格 + 折线）。
 * summary 口径不含农户明细，供数据统计员导出聚合报表。
 */
@Service
public class ReportService {
    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final String[] FONT_CANDIDATES = {
            "C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/simhei.ttf", "C:/Windows/Fonts/simsun.ttc"
    };

    public byte[] excel(String title, Dtos.StatsOverviewView overview,
                        List<Dtos.StatsDetailView> details, List<Dtos.StatsTrendView> trend,
                        boolean includeDetails) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            overviewSheet(workbook, title, overview);
            if (includeDetails) {
                detailSheet(workbook, details);
            }
            trendSheet(workbook, trend);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw BusinessException.badRequest("生成 Excel 失败：" + ex.getMessage());
        }
    }

    public byte[] pdf(String title, Dtos.StatsOverviewView overview,
                      List<Dtos.StatsDetailView> details, List<Dtos.StatsTrendView> trend,
                      boolean includeDetails) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDFont font = loadChineseFont(document);
            PdfWriter writer = new PdfWriter(document, font);

            writer.title(title);
            writer.metrics(overview);
            if (includeDetails && !details.isEmpty()) {
                writer.details(details);
            }
            writer.trendChart(trend);
            writer.closeStream();

            document.save(out);
            return out.toByteArray();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("生成 PDF 失败", ex);
            throw BusinessException.badRequest("生成 PDF 失败：" + ex.getMessage());
        }
    }

    // ---------------- Excel ----------------

    private void overviewSheet(XSSFWorkbook workbook, String title, Dtos.StatsOverviewView overview) {
        XSSFSheet sheet = workbook.createSheet("统计概览");
        row(sheet, 0, 0, title);
        row(sheet, 2, 0, "直播资源占用率(%)");
        row(sheet, 2, 2, String.valueOf(overview.occupancyRate()));
        row(sheet, 3, 0, "完成场次");
        row(sheet, 3, 2, String.valueOf(overview.completedCount()));
        row(sheet, 4, 0, "农产品销量");
        row(sheet, 4, 2, String.valueOf(overview.soldQuantity()));
        row(sheet, 5, 0, "预约成功率(%)");
        row(sheet, 5, 2, String.valueOf(overview.successRate()));
        row(sheet, 7, 0, "预约总数");
        row(sheet, 7, 2, String.valueOf(overview.bookingTotal()));
        row(sheet, 8, 0, "通过预约数");
        row(sheet, 8, 2, String.valueOf(overview.approvedCount()));
        sheet.setDefaultColumnWidth(18);
    }

    private void detailSheet(XSSFWorkbook workbook, List<Dtos.StatsDetailView> details) {
        XSSFSheet sheet = workbook.createSheet("场次明细");
        String[] headers = {"场次开始", "预约号", "农户", "场地", "区域编码", "主题", "销售数量", "销售条数"};
        for (int i = 0; i < headers.length; i++) {
            row(sheet, 0, i, headers[i]);
        }
        for (int r = 0; r < details.size(); r++) {
            Dtos.StatsDetailView d = details.get(r);
            row(sheet, r + 1, 0, d.startTime());
            row(sheet, r + 1, 1, d.bookingNo());
            row(sheet, r + 1, 2, d.farmerName());
            row(sheet, r + 1, 3, d.venueName());
            row(sheet, r + 1, 4, d.countyCode());
            row(sheet, r + 1, 5, d.topic());
            cell(sheet, r + 1, 6, d.soldQuantity());
            cell(sheet, r + 1, 7, d.salesCount());
        }
        sheet.setDefaultColumnWidth(16);
    }

    private void trendSheet(XSSFWorkbook workbook, List<Dtos.StatsTrendView> trend) {
        XSSFSheet sheet = workbook.createSheet("趋势");
        row(sheet, 0, 0, "时段");
        row(sheet, 0, 1, "完成场次");
        row(sheet, 0, 2, "销售数量");
        int lastRow = Math.max(trend.size(), 1);
        for (int r = 0; r < trend.size(); r++) {
            row(sheet, r + 1, 0, trend.get(r).period());
            cell(sheet, r + 1, 1, trend.get(r).liveCount());
            cell(sheet, r + 1, 2, trend.get(r).soldQuantity());
        }

        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 4, 1, 14, 22);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("直播场次与销量趋势");
        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);
        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
                sheet, new CellRangeAddress(1, lastRow, 0, 0));
        XDDFNumericalDataSource<Double> sold = XDDFDataSourcesFactory.fromNumericCellRange(
                sheet, new CellRangeAddress(1, lastRow, 2, 2));
        XDDFChartData.Series series = data.addSeries(categories, sold);
        series.setTitle("销售数量", null);
        chart.plot(data);
    }

    private void row(XSSFSheet sheet, int rowIndex, int colIndex, String value) {
        XSSFCell cell = sheet.getRow(rowIndex) == null
                ? sheet.createRow(rowIndex).createCell(colIndex)
                : sheet.getRow(rowIndex).getCell(colIndex) == null
                        ? sheet.getRow(rowIndex).createCell(colIndex)
                        : sheet.getRow(rowIndex).getCell(colIndex);
        cell.setCellValue(value == null ? "" : value);
    }

    private void cell(XSSFSheet sheet, int rowIndex, int colIndex, double value) {
        if (sheet.getRow(rowIndex) == null) {
            sheet.createRow(rowIndex);
        }
        XSSFCell cell = sheet.getRow(rowIndex).getCell(colIndex);
        if (cell == null) {
            cell = sheet.getRow(rowIndex).createCell(colIndex);
        }
        cell.setCellValue(value);
    }

    // ---------------- PDF ----------------

    private PDFont loadChineseFont(PDDocument document) throws IOException {
        for (String path : FONT_CANDIDATES) {
            File file = new File(path);
            if (!file.exists()) {
                continue;
            }
            if (path.endsWith(".ttc")) {
                org.apache.fontbox.ttf.TrueTypeCollection collection =
                        new org.apache.fontbox.ttf.TrueTypeCollection(file);
                String fontName = path.endsWith("msyh.ttc") ? "MicrosoftYaHei" : "SimSun";
                return PDType0Font.load(document, collection.getFontByName(fontName), true);
            }
            return PDType0Font.load(document, file);
        }
        throw BusinessException.badRequest("PDF_MISSING_FONT",
                "系统缺少中文字体（msyh.ttc / simhei.ttf / simsun.ttc），无法生成 PDF");
    }

    private class PdfWriter {
        private final PDDocument document;
        private final PDFont font;
        private PDPageContentStream stream;
        private PDPage page;
        private float y = PDRectangle.A4.getHeight() - 50;
        private static final float MARGIN = 50;
        private static final float PAGE_BOTTOM = 60;

        PdfWriter(PDDocument document, PDFont font) throws IOException {
            this.document = document;
            this.font = font;
            newPage();
        }

        void title(String title) throws IOException {
            text(title, 16, MARGIN, y);
            y -= 24;
        }

        void metrics(Dtos.StatsOverviewView overview) throws IOException {
            line("直播资源占用率：" + overview.occupancyRate() + "%");
            line("完成场次：" + overview.completedCount());
            line("农产品销量：" + overview.soldQuantity());
            line("预约成功率：" + overview.successRate() + "%");
            line("预约总数：" + overview.bookingTotal() + "，其中通过 " + overview.approvedCount());
            y -= 10;
        }

        void details(List<Dtos.StatsDetailView> details) throws IOException {
            ensureSpace(30);
            text("场次明细", 13, MARGIN, y);
            y -= 18;
            float[] widths = {90, 88, 55, 75, 105, 48, 38};
            String[] headers = {"场次开始", "预约号", "农户", "场地", "主题", "销量", "条数"};
            header(widths, headers);
            y -= 4;
            for (Dtos.StatsDetailView d : details) {
                ensureSpace(16);
                String[] cells = {
                        d.startTime(), d.bookingNo(), d.farmerName(),
                        d.venueName(), d.topic(),
                        String.valueOf(d.soldQuantity()), String.valueOf(d.salesCount())
                };
                tableRow(widths, cells);
            }
            y -= 10;
        }

        void trendChart(List<Dtos.StatsTrendView> trend) throws IOException {
            if (trend.isEmpty()) {
                return;
            }
            ensureSpace(200);
            text("直播场次与销量趋势", 13, MARGIN, y);
            y -= 14;
            float left = MARGIN + 30;
            float right = PDRectangle.A4.getWidth() - 40;
            float bottom = y - 110;
            float top = y - 10;
            // 坐标轴
            moveTo(left, top);
            lineTo(left, bottom);
            lineTo(right, bottom);
            stroke();
            double maxSold = trend.stream().mapToDouble(Dtos.StatsTrendView::soldQuantity)
                    .max().orElse(1);
            if (maxSold <= 0) maxSold = 1;
            // 折线
            float step = trend.size() == 1 ? 0 : (right - left) / (trend.size() - 1);
            moveTo(left, bottom);
            for (int i = 0; i < trend.size(); i++) {
                double value = trend.get(i).soldQuantity();
                float x = trend.size() == 1 ? left : left + step * i;
                float py = bottom + (float) (value / maxSold * (top - bottom - 10)) + 5;
                lineTo(x, py);
            }
            stroke();
            // 刻度标签（最多 6 个）
            int labelStep = Math.max(1, trend.size() / 6);
            for (int i = 0; i < trend.size(); i += labelStep) {
                float x = trend.size() == 1 ? left + 40 : left + step * i;
                text(trend.get(i).period(), 7, x, bottom - 10);
            }
            text("最大销量 " + String.format("%.0f", maxSold), 7, left + 4, top - 2);
            y = bottom - 24;
        }

        private void line(String content) throws IOException {
            ensureSpace(16);
            text(content, 10, MARGIN, y);
            y -= 16;
        }

        private void header(float[] widths, String[] headers) throws IOException {
            float x = MARGIN;
            for (int i = 0; i < headers.length; i++) {
                text(headers[i], 9, x, y);
                x += widths[i];
            }
        }

        private void tableRow(float[] widths, String[] cells) throws IOException {
            float x = MARGIN;
            for (int i = 0; i < cells.length; i++) {
                String value = cells[i] == null ? "" : cells[i];
                text(ellipsis(value, 14), 8, x, y);
                x += widths[i];
            }
            y -= 14;
        }

        private String ellipsis(String value, int limit) {
            if (value == null) return "";
            return value.length() <= limit ? value : value.substring(0, limit) + "…";
        }

        private void ensureSpace(float needed) throws IOException {
            if (y - needed < PAGE_BOTTOM) {
                newPage();
            }
        }

        private void newPage() throws IOException {
            closeStream();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.OVERWRITE, true, true);
            y = PDRectangle.A4.getHeight() - 50;
        }

        private void text(String content, float size, float x, float yAt) throws IOException {
            if (content == null || content.isEmpty()) return;
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, yAt);
            stream.showText(content);
            stream.endText();
        }

        private void moveTo(float x, float yAt) throws IOException {
            stream.moveTo(x, yAt);
        }

        private void lineTo(float x, float yAt) throws IOException {
            stream.lineTo(x, yAt);
        }

        private void stroke() throws IOException {
            stream.stroke();
        }

        private void closeStream() {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // 关闭失败不影响文档
                }
                stream = null;
            }
        }
    }
}
