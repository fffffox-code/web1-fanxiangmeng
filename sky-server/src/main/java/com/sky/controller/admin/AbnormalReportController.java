package com.sky.controller.admin;

import com.sky.entity.AbnormalOrderReport;
import com.sky.mapper.AbnormalOrderReportMapper;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/admin/abnormal")   // 修改为 /admin/abnormal 以匹配前端
@Api(tags = "异常订单分析")
@Slf4j
public class AbnormalReportController {

    @Autowired
    private AbnormalOrderReportMapper reportMapper;

    @GetMapping("/recent")
    @ApiOperation("获取最近N天的异常报告")
    public Result<List<AbnormalOrderReport>> getRecentReports(@RequestParam(defaultValue = "7") int days) {
        LocalDate end = LocalDate.now().minusDays(1);
        LocalDate start = end.minusDays(days - 1);
        List<AbnormalOrderReport> list = reportMapper.getByDateRange(start, end);
        return Result.success(list);
    }

    // 添加获取详细订单列表的接口，供前端详情弹窗使用
    @GetMapping("/detail")
    @ApiOperation("获取指定报告日的异常订单明细")
    public Result<String> getDetail(@RequestParam String reportDate) {
        LocalDate date = LocalDate.parse(reportDate);
        AbnormalOrderReport report = reportMapper.getByDate(date);
        if (report != null && report.getDetailJson() != null) {
            return Result.success(report.getDetailJson());
        }
        return Result.success("[]");
    }
}