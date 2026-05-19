package com.sky.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.utils.Constants;
import com.sky.config.AiConfig;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AiAnalysisService {

    @Autowired
    private AiConfig aiConfig;
    @Autowired
    private ReportService reportService;      // 已有报表服务
    @Autowired
    private OrderService orderService;        // 已有订单服务（可选）

    // 预定义问题类型
    private static final String TYPE_SALES_TOP10 = "sales_top10";
    private static final String TYPE_TURNOVER_TREND = "turnover_trend";
    private static final String TYPE_USER_TREND = "user_trend";
    private static final String TYPE_ORDER_STATS = "order_stats";
    private static final String TYPE_UNKNOWN = "unknown";

    /**
     * 分析用户问题并返回结构化结果
     * @param question 用户自然语言问题
     * @return Map 包含 type, data, text 等字段
     */
    public Map<String, Object> analyze(String question) {
        // 1. 规则匹配意图
        String type = detectIntentByRule(question);
        Map<String, Object> result = new HashMap<>();

        try {
            switch (type) {
                case TYPE_SALES_TOP10:
                    LocalDate[] range = extractDateRange(question);
                    SalesTop10ReportVO top10 = reportService.getSalesTop10(range[0], range[1]);
                    result.put("type", "table");
                    result.put("data", top10);
                    result.put("text", "销量排名 TOP10（" + range[0] + " 至 " + range[1] + "）");
                    break;
                case TYPE_TURNOVER_TREND:
                    range = extractDateRange(question);
                    TurnoverReportVO turnover = reportService.getTurnoverStatistics(range[0], range[1]);
                    result.put("type", "line");
                    result.put("data", turnover);
                    result.put("text", "营业额趋势（" + range[0] + " 至 " + range[1] + "）");
                    break;
                case TYPE_USER_TREND:
                    range = extractDateRange(question);
                    UserReportVO user = reportService.getUserStatistics(range[0], range[1]);
                    result.put("type", "line");
                    result.put("data", user);
                    result.put("text", "新增用户趋势（" + range[0] + " 至 " + range[1] + "）");
                    break;
                case TYPE_ORDER_STATS:
                    range = extractDateRange(question);
                    OrderReportVO order = reportService.getOrderStatistics(range[0], range[1]);
                    result.put("type", "stats");
                    result.put("data", order);
                    result.put("text", "订单统计（" + range[0] + " 至 " + range[1] + "）");
                    break;
                default:
                    // 兜底：调用大模型生成通用回答（可选）
                    String answer = callAiForGeneralQuestion(question);
                    result.put("type", "text");
                    result.put("data", answer);
                    result.put("text", answer);
                    break;
            }
        } catch (Exception e) {
            log.error("AI分析失败", e);
            result.put("type", "text");
            result.put("data", "分析失败，请稍后重试。");
            result.put("text", "分析失败，请稍后重试。");
        }
        return result;
    }

    private String detectIntentByRule(String question) {
        String q = question.toLowerCase();
        // 销量排名相关：包含“销量排名”、“销量top”、“最好卖的”等
        if ((q.contains("销量") || q.contains("销售")) && (q.contains("排名") || q.contains("top") || q.contains("最好") || q.contains("前"))) {
            return TYPE_SALES_TOP10;
        }
        // 营业额趋势：包含“营业额”、“销售额” + “趋势”、“走势”
        if ((q.contains("营业额") || q.contains("销售额")) && (q.contains("趋势") || q.contains("走势") || q.contains("变化"))) {
            return TYPE_TURNOVER_TREND;
        }
        // 用户趋势
        if ((q.contains("用户") || q.contains("新增")) && (q.contains("趋势") || q.contains("走势"))) {
            return TYPE_USER_TREND;
        }
        // 订单统计
        if (q.contains("订单") && (q.contains("统计") || q.contains("完成率") || q.contains("数量"))) {
            return TYPE_ORDER_STATS;
        }
        return TYPE_UNKNOWN;
    }

    /**
     * 从问题中提取日期范围（简单实现）
     * 默认返回近7天；支持“上月”、“本周”、“本月”，以及明确日期区间如“2026-05-01到2026-05-07”
     */
    private LocalDate[] extractDateRange(String question) {
        String q = question.toLowerCase();
        LocalDate end = LocalDate.now().minusDays(1);
        LocalDate start = end.minusDays(29); // 默认近30天
        if (q.contains("上月")) {
            LocalDate firstDayOfLastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1);
            LocalDate lastDayOfLastMonth = firstDayOfLastMonth.withDayOfMonth(firstDayOfLastMonth.lengthOfMonth());
            start = firstDayOfLastMonth;
            end = lastDayOfLastMonth;
        } else if (q.contains("本周")) {
            start = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
            end = LocalDate.now();
        } else if (q.contains("本月")) {
            start = LocalDate.now().withDayOfMonth(1);
            end = LocalDate.now();
        } else if (q.contains("近7天") || q.contains("近七日")) {
            start = end.minusDays(6);
        } else if (q.contains("近30天")) {
            start = end.minusDays(29);
        }
        return new LocalDate[]{start, end};
    }

    /**
     * 调用大模型处理未知问题（可选，简单返回提示）
     */
    private String callAiForGeneralQuestion(String question) {
        // 设置 API Key（已在 AiConfig 中全局设置）
        if (aiConfig.getApiKey() != null && !aiConfig.getApiKey().isEmpty()) {
            Constants.apiKey = aiConfig.getApiKey();
        }
        String prompt = "你是一个餐厅运营数据分析助手。用户问题：" + question +
                "。如果问题涉及销量排名、营业额趋势、订单统计，请引导用户使用明确的关键词（如“上月销量排名”）。否则请友好告知无法回答。";
        Message userMsg = Message.builder().role(Role.USER.getValue()).content(prompt).build();
        GenerationParam param = GenerationParam.builder()
                .model(aiConfig.getModel())
                .messages(Collections.singletonList(userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .maxTokens(200)
                .temperature(0.7F)
                .build();
        try {
            Generation gen = new Generation();
            GenerationResult result = gen.call(param);
            return result.getOutput().getChoices().get(0).getMessage().getContent();
        } catch (Exception e) {
            log.error("调用AI失败", e);
            return "抱歉，我暂时无法回答这个问题。您可以尝试询问“上月销量排名”、“近7天营业额趋势”等。";
        }
    }
}