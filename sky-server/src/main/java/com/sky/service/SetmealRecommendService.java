package com.sky.service;

import com.sky.entity.OrderDetail;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SetmealRecommendService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;

    // 缓存：菜品名称 -> 推荐菜品列表（按共现频次降序）
    private Map<String, List<String>> recommendCache = new HashMap<>();

    @PostConstruct
    public void init() {
        refreshRecommendation();
    }

    // 每天凌晨3点刷新缓存
    @Scheduled(cron = "0 0 3 * * ?")
    public void refreshRecommendation() {
        log.info("开始刷新套餐推荐缓存");
        try {
            LocalDateTime end = LocalDateTime.now();
            LocalDateTime start = end.minusDays(30);
            // 1. 获取近30天的订单ID列表
            List<Long> orderIds = orderMapper.getOrderIdsByTimeRange(start, end);
            if (orderIds == null || orderIds.isEmpty()) {
                log.warn("近30天无订单");
                recommendCache.clear();
                return;
            }
            // 2. 根据订单ID批量查询所有订单明细
            List<OrderDetail> details = orderDetailMapper.getByOrderIds(orderIds);
            if (details == null || details.isEmpty()) {
                log.warn("无订单明细");
                recommendCache.clear();
                return;
            }
            log.info("订单明细数量: {}", details.size());

            // 3. 按订单ID分组，得到每个订单包含的菜品名称集合
            Map<Long, List<String>> orderDishes = details.stream()
                    .collect(Collectors.groupingBy(
                            OrderDetail::getOrderId,
                            Collectors.mapping(OrderDetail::getName, Collectors.toList())
                    ));
            log.info("有效订单数量: {}", orderDishes.size());

            // 4. 统计两两菜品的共现次数
            Map<String, Map<String, Integer>> cooccurrence = new HashMap<>();
            for (List<String> dishes : orderDishes.values()) {
                if (dishes.size() < 2) continue; // 单菜品订单跳过
                for (int i = 0; i < dishes.size(); i++) {
                    // 同一个订单中，对所有菜品两两组合进行计数
                    for (int j = i + 1; j < dishes.size(); j++) {
                        String a = dishes.get(i);
                        String b = dishes.get(j);
                        // 对称增加计数
                        cooccurrence.computeIfAbsent(a, k -> new HashMap<>())
                                .merge(b, 1, Integer::sum);
                        cooccurrence.computeIfAbsent(b, k -> new HashMap<>())
                                .merge(a, 1, Integer::sum);
                    }
                }
            }

            log.info("共现统计结果: {}", cooccurrence);

            // 5. 为每个菜品生成推荐列表（取共现频次最高的前3个菜品）
            Map<String, List<String>> newCache = new HashMap<>();
            for (Map.Entry<String, Map<String, Integer>> entry : cooccurrence.entrySet()) {
                String dish = entry.getKey();
                List<Map.Entry<String, Integer>> list = new ArrayList<>(entry.getValue().entrySet());
// 降序排序
                list.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));
                List<String> recommended = list.stream().limit(3).map(Map.Entry::getKey).collect(Collectors.toList());
                newCache.put(dish, recommended);
                log.info("菜品【{}】推荐: {}", dish, recommended);
            }
            recommendCache = newCache;
            log.info("推荐缓存刷新完成，共{}个菜品有推荐", newCache.size());
        } catch (Exception e) {
            log.error("刷新推荐缓存失败", e);
        }
    }

    /**
     * 根据已选菜品列表，推荐可能搭配的菜品
     * @param selectedDishes 已选菜品名称列表
     * @return 推荐的菜品名称列表（按推荐度降序）
     */
    public List<String> getRecommendationsForSet(List<String> selectedDishes) {
        if (selectedDishes == null || selectedDishes.isEmpty()) {
            return Collections.emptyList();
        }
        // 对已选的每个菜品，从缓存中取出其推荐列表，累计得分（共现次数）
        Map<String, Integer> score = new HashMap<>();
        for (String dish : selectedDishes) {
            List<String> recs = recommendCache.getOrDefault(dish, Collections.emptyList());
            for (String r : recs) {
                if (!selectedDishes.contains(r)) {
                    score.merge(r, 1, Integer::sum);
                }
            }
        }
        // 按得分降序排序，返回前5个
        return score.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }
}