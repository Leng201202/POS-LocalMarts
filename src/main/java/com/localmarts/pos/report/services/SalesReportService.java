package com.localmarts.pos.report.services;


import com.localmarts.pos.report.ReportRepository;
import com.localmarts.pos.report.dtos.SalesReportDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SalesReportService {
    private final ReportRepository repository;

    public SalesReportDTO getReport(
            LocalDate start,
            LocalDate end
    ){
        if(start==null && end==null){
            result =repository.findAllSalesSummary();
        }else{
            LocalDateTime startDate=start.atStartOfDay();
            LocalDateTime endDate=end.plusDays(1).atStartOfDay();

            result=repository.findSalesBetween(startDate,endDate);
        }
        Long totalSale=result[0] !=null
                ? (Long) result[0]
                : 0L;
        BigDecimal totalRev=result[1] !=null
                ? (BigDecimal) result[1]
                : BigDecimal.ZERO;
        return SalesReportDTO.builder()
                .startDate(start)
                .endDate(end)
                .totalSaleCount(totalSale)
                .totalRevenue(totalRev)
                .build();
    }
}
