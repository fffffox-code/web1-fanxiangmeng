package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.AiAnalysisService;
import com.sky.service.AiService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/ai")
@Api(tags = "AI辅助功能")
@Slf4j
public class AiController {

    @Autowired
    private AiService aiService;

    @Autowired
    private AiAnalysisService aiAnalysisService;

    @PostMapping("/generateDishDesc")
    @ApiOperation("AI生成菜品描述")
    public Result<String> generateDishDesc(@RequestBody Map<String, String> params) {
        String dishName = params.get("dishName");
        String category = params.get("category");
        String flavors = params.get("flavors");
        if (dishName == null || dishName.trim().isEmpty()) {
            return Result.error("菜品名称不能为空");
        }
        String description = aiService.generateDishDescription(dishName, category, flavors);
        return Result.success(description);
    }

    @PostMapping("/analyze")
    @ApiOperation("AI运营数据分析")
    public Result<Map<String, Object>> analyze(@RequestBody Map<String, String> payload) {
        String question = payload.get("question");
        log.info("AI分析问题：{}", question);
        Map<String, Object> result = aiAnalysisService.analyze(question);
        return Result.success(result);

    }
}
