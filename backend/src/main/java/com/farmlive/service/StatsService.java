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
                                           Long venueId, String productName) {
        StatsRepository.Filter filter = filter(from, to, countyCode, venueId, productName);
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
                                                    Long venueId, String productName,
                                                    int page, int size) {
        return repository.details(filter(from, to, countyCode, venueId, productName), page, size);
    }

    public List<Dtos.StatsTrendView> trend(String from, String to, String countyCode,
                                           Long venueId, String productName, String granularity) {
        String value = switch (granularity == null ? "day" : granularity) {
            case "week", "month" -> granularity;
            default -> "day";
        };
        return repository.trend(filter(from, to, countyCode, venueId, productName), value);
    }

    public List<Dtos.StatsDetailView> export(String from, String to, String countyCode,
                                             Long venueId, String productName) {
        return repository.exportDetails(filter(from, to, countyCode, venueId, productName));
    }

    public List<String> productNames() {
        return repository.productNames();
    }

    public Dtos.MyStatsView myStats(Long farmerId) {
        return repository.myStats(farmerId);
    }

    public List<Dtos.StatsDetailView> myDetails(Long farmerId) {
        return repository.myDetails(farmerId);
    }

    private StatsRepository.Filter filter(String from, String to, String countyCode,
                                          Long venueId, String productName) {
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
                productName == null || productName.isBlank() ? null : productName.trim());
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
