package com.farmlive.web;

import com.farmlive.common.ApiResponse;
import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import com.farmlive.service.StatsService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 数据统计：四项指标、完成场次明细、趋势和 CSV 导出。
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {
    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<Dtos.StatsOverviewView> overview(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String countyCode,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String productName) {
        return ApiResponse.ok(service.overview(from, to, countyCode, venueId, productName));
    }

    @GetMapping("/details")
    public ApiResponse<PageResult<Dtos.StatsDetailView>> details(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String countyCode,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String productName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        page = Math.max(1, page);
        size = Math.min(100, Math.max(1, size));
        return ApiResponse.ok(service.details(from, to, countyCode, venueId, productName, page, size));
    }

    @GetMapping("/trend")
    public ApiResponse<List<Dtos.StatsTrendView>> trend(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String countyCode,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String productName,
            @RequestParam(defaultValue = "day") String granularity) {
        return ApiResponse.ok(service.trend(from, to, countyCode, venueId, productName, granularity));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String countyCode,
            @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String productName) {
        List<Dtos.StatsDetailView> rows = service.export(from, to, countyCode, venueId, productName);
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
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("stats-details.csv", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String csv(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
