package com.farmlive.web;

import com.farmlive.auth.AuthInterceptor;
import com.farmlive.auth.CurrentUser;
import com.farmlive.common.ApiResponse;
import com.farmlive.common.BusinessException;
import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import com.farmlive.service.AuditService;
import com.farmlive.service.ReportService;
import com.farmlive.service.StatsService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 数据统计：四项指标、完成场次明细、趋势、CSV / Excel / PDF 导出。
 * 数据统计员只能看聚合口径：可导出含指标与趋势的聚合报表，不能导出农户明细。
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {
    private final StatsService service;
    private final ReportService reportService;
    private final AuditService auditService;

    public StatsController(StatsService service, ReportService reportService, AuditService auditService) {
        this.service = service;
        this.reportService = reportService;
        this.auditService = auditService;
    }

    @GetMapping("/products")
    public ApiResponse<List<String>> products() {
        return ApiResponse.ok(service.productNames());
    }

    @GetMapping("/product-types")
    public ApiResponse<List<String>> productTypes() {
        return ApiResponse.ok(service.productTypes());
    }

    @GetMapping("/overview")
    public ApiResponse<Dtos.StatsOverviewView> overview(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String countyCode,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String productType) {
        return ApiResponse.ok(service.overview(from, to, countyCode, venueId, productName, productType));
    }

    @GetMapping("/details")
    public ApiResponse<PageResult<Dtos.StatsDetailView>> details(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String countyCode,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String productType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireDetail(user);
        page = Math.max(1, page);
        size = Math.min(100, Math.max(1, size));
        return ApiResponse.ok(service.details(from, to, countyCode, venueId, productName, productType, page, size));
    }

    @GetMapping("/trend")
    public ApiResponse<List<Dtos.StatsTrendView>> trend(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String countyCode,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String productType,
            @RequestParam(defaultValue = "day") String granularity) {
        return ApiResponse.ok(service.trend(from, to, countyCode, venueId, productName, productType, granularity));
    }

    /**
     * 聚合口径 CSV 导出：四项指标 + 趋势，全部角色可用（数据统计员导出的唯一明细级内容）。
     */
    @GetMapping("/export/aggregate-csv")
    public ResponseEntity<byte[]> exportAggregateCsv(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String countyCode,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String productType) {
        byte[] csv = service.exportAggregateCsv(from, to, countyCode, venueId, productName, productType);
        auditService.record(user, "STATS_EXPORT", "导出聚合统计 CSV（指标+趋势）");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("stats-aggregate.csv", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String countyCode,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String productType) {
        requireDetail(user);
        List<Dtos.StatsDetailView> rows = service.export(from, to, countyCode, venueId, productName, productType);
        StringBuilder csv = new StringBuilder("\uFEFF场次开始,预约号,农户,场地,区域编码,主题,销售数量,销售条数\n");
        for (Dtos.StatsDetailView row : rows) {
            csv.append(csv(row.startTime())).append(',')
                    .append(csv(row.bookingNo())).append(',')
                    .append(csv(row.farmerName())).append(',')
                    .append(csv(row.venueName())).append(',')
                    .append(csv(row.countyCode())).append(',')
                    .append(csv(row.topic())).append(',')
                    .append(row.soldQuantity()).append(',')
                    .append(row.salesCount()).append('\n');
        }
        auditService.recordCurrent("STATS_EXPORT", "导出场次明细 CSV（" + rows.size() + " 行）");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("stats-details.csv", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Excel 报表：含统计概览、场次明细（统计员无此页）与趋势折线图。
     */
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String countyCode,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String productType,
            @RequestParam(defaultValue = "day") String granularity) {
        boolean includeDetails = isDetailViewer(user);
        Dtos.StatsOverviewView overview = service.overview(from, to, countyCode, venueId, productName, productType);
        List<Dtos.StatsTrendView> trend = service.trend(from, to, countyCode, venueId, productName, productType, granularity);
        List<Dtos.StatsDetailView> details = includeDetails
                ? service.export(from, to, countyCode, venueId, productName, productType)
                : List.of();
        String title = reportTitle(from, to, includeDetails);
        byte[] bytes = reportService.excel(title, overview, details, trend, includeDetails);
        auditService.recordCurrent("STATS_EXPORT", "导出统计报表 Excel（" + (includeDetails ? "完整" : "聚合") + "口径）");
        return attachment(bytes, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "stats-report.xlsx");
    }

    /**
     * PDF 报表：含统计概览、场次明细（统计员无此页）与趋势折线。
     */
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestAttribute(AuthInterceptor.CURRENT_USER) CurrentUser user,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String countyCode,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String productType,
            @RequestParam(defaultValue = "day") String granularity) {
        boolean includeDetails = isDetailViewer(user);
        Dtos.StatsOverviewView overview = service.overview(from, to, countyCode, venueId, productName, productType);
        List<Dtos.StatsTrendView> trend = service.trend(from, to, countyCode, venueId, productName, productType, granularity);
        List<Dtos.StatsDetailView> details = includeDetails
                ? service.export(from, to, countyCode, venueId, productName, productType)
                : List.of();
        String title = reportTitle(from, to, includeDetails);
        byte[] bytes = reportService.pdf(title, overview, details, trend, includeDetails);
        auditService.recordCurrent("STATS_EXPORT", "导出统计报表 PDF（" + (includeDetails ? "完整" : "聚合") + "口径）");
        return attachment(bytes, "application/pdf", "stats-report.pdf");
    }

    private String reportTitle(String from, String to, boolean includeDetails) {
        String scope = includeDetails ? "完整报表" : "聚合报表";
        String range = (from == null || from.isBlank() ? "近 30 天" : from) + " 至 "
                + (to == null || to.isBlank() ? "今天" : to);
        return "助农直播统计" + scope + "（" + range + "）";
    }

    private boolean isDetailViewer(CurrentUser user) {
        return "live_admin".equals(user.roleCode()) || "system_admin".equals(user.roleCode());
    }

    private ResponseEntity<byte[]> attachment(byte[] bytes, String contentType, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private void requireDetail(CurrentUser user) {
        if (!isDetailViewer(user)) {
            throw BusinessException.forbidden("数据统计员只能查看聚合统计，不能查看单个农户明细");
        }
    }

    private String csv(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
