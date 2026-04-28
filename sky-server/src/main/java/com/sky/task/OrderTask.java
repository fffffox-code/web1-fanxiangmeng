package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@EnableScheduling
public class OrderTask {
    private final OrderMapper orderMapper;

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
}
