package com.sky.task;

import com.alibaba.fastjson.JSON;
import com.sky.entity.AbnormalOrderReport;
import com.sky.entity.Orders;
import com.sky.mapper.AbnormalOrderReportMapper;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
@EnableScheduling
public class OrderTask {
    private final OrderMapper orderMapper;
    @Autowired
    private AbnormalOrderReportMapper abnormalOrderReportMapper;

    public OrderTask(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    /**
     * 处理超时订单的方法
     */
    @Scheduled(cron = "0 * * * * ?")//每分钟触发一次
    public void processTimeoutOrder() {
        log.info("定时处理超时单:{)", LocalDateTime.now());

        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);
// select * from orders where status = ? and order_time < (当il时间 - 15分)
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimelT(Orders.PENDING_PAYMENT, time);
        if (ordersList != null && ordersList.size() > 0) {
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason("i单超时.自动消");
                orders.setCancelTime(LocalDateTime.now());
                orderMapper.update(orders);
            }
        }
    }


    @Scheduled(cron = "0 0 1 * * ?")//天凌1点发
    public void processDeliveryOrder() {
        log.info("定时处理处于派送中的订单:()", LocalDateTime.now());
        LocalDateTime time = LocalDateTime.now().plusMinutes(-60);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimelT(Orders.DELIVERY_IN_PROGRESS, time);
        if (ordersList != null && ordersList.size() > 0) {
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.COMPLETED);
                orderMapper.update(orders);
            }
        }
    }
    // 每天凌晨2点执行异常订单分析（避开业务高峰）
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点执行
    public void analyzeAbnormalOrders() {
        log.info("开始分析异常订单，日期：{}", LocalDate.now().minusDays(1));
        LocalDate reportDate = LocalDate.now().minusDays(1);
        LocalDateTime start = reportDate.atStartOfDay();
        LocalDateTime end = reportDate.atTime(23, 59, 59);

        // 查询当天所有已取消的订单（状态为6，且取消时间在当天）
        List<Orders> cancelledOrders = orderMapper.getCancelledOrdersBetween(start, end);
        if (cancelledOrders == null || cancelledOrders.isEmpty()) {
            log.info("当日无异常订单，跳过报告生成");
            return;
        }

        int totalCancelled = cancelledOrders.size();
        int totalRejected = (int) cancelledOrders.stream()
                .filter(o -> "商家拒单".equals(o.getRejectionReason()) || "商家拒单".equals(o.getCancelReason()))
                .count();
        int totalTimeout = (int) cancelledOrders.stream()
                .filter(o -> o.getCancelReason() != null && o.getCancelReason().contains("超时"))
                .count();

        // 平均取消时长（分钟）
        double avgMinutes = cancelledOrders.stream()
                .filter(o -> o.getCancelTime() != null && o.getOrderTime() != null)
                .mapToLong(o -> java.time.Duration.between(o.getOrderTime(), o.getCancelTime()).toMinutes())
                .average()
                .orElse(0);
        int avgCancelTime = (int) Math.round(avgMinutes);

        // 统计取消原因TOP1
        Map<String, Long> reasonCount = cancelledOrders.stream()
                .map(o -> o.getCancelReason() != null ? o.getCancelReason() : "未知原因")
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        String topReason = reasonCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("无");

        // 生成详细JSON（订单号、取消原因、取消时间、金额）
        List<Map<String, Object>> details = cancelledOrders.stream()
                .map(o -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("orderNumber", o.getNumber());
                    m.put("cancelReason", o.getCancelReason());
                    m.put("cancelTime", o.getCancelTime() != null ? o.getCancelTime().toString() : "");
                    m.put("amount", o.getAmount());
                    return m;
                }).collect(Collectors.toList());
        String detailJson = JSON.toJSONString(details);

        // 保存报告
        AbnormalOrderReport report = new AbnormalOrderReport();
        report.setReportDate(reportDate);
        report.setTotalCancelled(totalCancelled);
        report.setTotalRejected(totalRejected);
        report.setTotalTimeout(totalTimeout);
        report.setAvgCancelTime(avgCancelTime);
        report.setTopCancelReason(topReason);
        report.setDetailJson(detailJson);

        abnormalOrderReportMapper.insert(report);
        log.info("异常订单分析完成，共{}条取消订单，已保存报告", totalCancelled);
    }
}
