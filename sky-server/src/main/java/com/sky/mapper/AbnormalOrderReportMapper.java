package com.sky.mapper;

import com.sky.entity.AbnormalOrderReport;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AbnormalOrderReportMapper {

    @Insert("INSERT INTO abnormal_order_report (report_date, total_cancelled, total_rejected, total_timeout, avg_cancel_time, top_cancel_reason, detail_json) " +
            "VALUES (#{reportDate}, #{totalCancelled}, #{totalRejected}, #{totalTimeout}, #{avgCancelTime}, #{topCancelReason}, #{detailJson})")
    void insert(AbnormalOrderReport report);

    @Select("SELECT * FROM abnormal_order_report WHERE report_date = #{date}")
    AbnormalOrderReport getByDate(LocalDate date);

    @Select("SELECT * FROM abnormal_order_report WHERE report_date BETWEEN #{start} AND #{end} ORDER BY report_date DESC")
    List<AbnormalOrderReport> getByDateRange(@Param("start") LocalDate start, @Param("end") LocalDate end);
}