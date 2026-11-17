package com.farmlive.service;

import com.farmlive.common.PageResult;
import com.farmlive.dto.Dtos;
import com.farmlive.repo.StatsRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 统计口径：四项指标、完成场次明细、趋势和导出。
 */
@Service
public class StatsService {
    private final StatsRepository repository;

    public StatsService(StatsRepository repository) {
        this.repository = repository;
    }

    public Dtos.StatsOverviewView overview(String from, String to, String countyCode,
                                           Long venueId, String productName, String productType) {
        StatsRepository.Filter filter = filter(from, to, countyCode, venueId, productName, productType);
        long total = repository.countAll(filter);
        long approved = repository.count(filter, "'approved','completed'");
        long completed = repository.count(filter, "'completed'");
        double sold = repository.soldQuantity(filter);
        long days = ChronoUnit.DAYS.between(filter.from().toLocalDate(), filter.to().toLocalDate());
        double capacity = repository.venueCapacityHours(filter, Math.max(days, 1));
        double booked = repository.bookedHours(filter);
        double occupancy = capacity <= 0 ? 0 : Math.min(100, booked / capacity * 100);
        double success = total == 0 ? 0 : approved * 100.0 / total;
        return new Dtos.StatsOverviewView(round(occupancy), completed, round(sold),
                round(success), total, approved, completed);
    }

    public PageResult<Dtos.StatsDetailView> details(String from, String to, String countyCode,
                                                    Long venueId, String productName, String productType,
                                                    int page, int size) {
        return repository.details(filter(from, to, countyCode, venueId, productName, productType), page, size);
    }

    public List<Dtos.StatsTrendView> trend(String from, String to, String countyCode,
                                           Long venueId, String productName, String productType,
                                           String granularity) {
        String value = switch (granularity == null ? "day" : granularity) {
            case "week", "month" -> granularity;
            default -> "day";
        };
        return repository.trend(filter(from, to, countyCode, venueId, productName, productType), value);
    }

    public List<Dtos.StatsDetailView> export(String from, String to, String countyCode,
                                             Long venueId, String productName, String productType) {
        return repository.exportDetails(filter(from, to, countyCode, venueId, productName, productType));
    }

    public List<String> productNames() {
        return repository.productNames();
    }

    /**
     * 农产品类型字典（按名称关键字自动归类）。
     */
    public List<String> productTypes() {
        List<String> types = new java.util.ArrayList<>(
                com.farmlive.repo.StatsRepository.typeNames());
        types.add("其他");
        return types;
    }

    /**
     * 聚合口径 CSV：四项指标 + 趋势，无农户明细，全部角色可导出。
     */
    public byte[] exportAggregateCsv(String from, String to, String countyCode,
                                     Long venueId, String productName, String productType) {
        Dtos.StatsOverviewView overview = overview(from, to, countyCode, venueId, productName, productType);
        List<Dtos.StatsTrendView> trend = trend(from, to, countyCode, venueId, productName, productType, "day");
        StringBuilder csv = new StringBuilder("\uFEFF指标,数值\n");
        csv.append("场地占用率(%),").append(overview.occupancyRate()).append("\n");
        csv.append("完成场次,").append(overview.completedCount()).append("\n");
        csv.append("卖出数量,").append(overview.soldQuantity()).append("\n");
        csv.append("预约成功率(%),").append(overview.successRate()).append("\n");
        csv.append("\n周期,完成场次,卖出数量\n");
        for (Dtos.StatsTrendView row : trend) {
            csv.append(row.period()).append(",")
                    .append(row.liveCount()).append(",")
                    .append(row.soldQuantity()).append("\n");
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public Dtos.MyStatsView myStats(Long farmerId) {
        return repository.myStats(farmerId);
    }

    public List<Dtos.StatsDetailView> myDetails(Long farmerId) {
        return repository.myDetails(farmerId);
    }

    private StatsRepository.Filter filter(String from, String to, String countyCode,
                                          Long venueId, String productName, String productType) {
        LocalDate endDate = parseDate(to, LocalDate.now());
        LocalDate startDate = parseDate(from, endDate.minusDays(29));
        if (startDate.isAfter(endDate)) {
            LocalDate tmp = startDate;
            startDate = endDate;
            endDate = tmp;
        }
        LocalDateTime fromTime = startDate.atStartOfDay();
        LocalDateTime toTime = endDate.plusDays(1).atStartOfDay();
        return new StatsRepository.Filter(fromTime, toTime, countyCode, venueId,
                productName == null || productName.isBlank() ? null : productName.trim(),
                productType == null || productType.isBlank() ? null : productType.trim());
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ex) {
            throw com.farmlive.common.BusinessException.badRequest("日期格式必须是 yyyy-MM-dd");
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
