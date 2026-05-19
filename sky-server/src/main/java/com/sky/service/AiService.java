package com.sky.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.exception.UploadFileException;
import com.alibaba.dashscope.utils.Constants;
import com.sky.config.AiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Arrays;

@Service
@Slf4j
public class AiService {

    @Autowired
    private AiConfig aiConfig;

    @PostConstruct
    public void init() {
        // 全局设置一次 API Key
        if (aiConfig.getApiKey() != null && !aiConfig.getApiKey().isEmpty()) {
            Constants.apiKey = aiConfig.getApiKey();
            log.info("AI API Key 已初始化");
        }
    }

    /**
     * 生成菜品描述
     */
    public String generateDishDescription(String dishName, String category, String flavors) {
        // 构建提示词
        String prompt = String.format(
                "你是一位资深美食编辑。请为菜品“%s”撰写一段吸引人的菜品描述。分类：%s。口味特点：%s。要求：50-80字，突出色香味和独特卖点，风格亲切自然，适合外卖平台展示。",
                dishName, category == null ? "未知" : category,
                flavors == null || flavors.isEmpty() ? "暂无特别说明" : flavors
        );

        Message userMsg = Message.builder()
                .role(Role.USER.getValue())
                .content(prompt)
                .build();

        // 构建参数（temperature 需要 Float 类型）
        GenerationParam.GenerationParamBuilder builder = GenerationParam.builder()
                .model(aiConfig.getModel())
                .messages(Arrays.asList(userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .maxTokens(aiConfig.getMaxTokens());

        // 如果配置了 temperature，转换为 Float
        if (aiConfig.getTemperature() != null) {
            builder.temperature(aiConfig.getTemperature().floatValue());
        }

        GenerationParam param = builder.build();

        try {
            Generation gen = new Generation();
            GenerationResult result = gen.call(param);
            String description = result.getOutput().getChoices().get(0).getMessage().getContent();
            log.info("AI生成菜品描述：{}", description);
            return description;
        } catch (NoApiKeyException | ApiException | InputRequiredException e) {
            log.error("调用AI生成菜品描述失败", e);
            throw new RuntimeException("AI服务暂时不可用，请稍后重试或检查API Key配置");
        }
    }
}