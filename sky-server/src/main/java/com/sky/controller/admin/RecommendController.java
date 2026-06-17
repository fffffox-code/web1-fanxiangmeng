package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.SetmealRecommendService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/ai/recommend")
@Api(tags = "智能推荐")
@Slf4j
public class RecommendController {

    @Autowired
    private SetmealRecommendService recommendService;

    @PostMapping("/setmeal")
    @ApiOperation("根据已选菜品推荐搭配")
    public Result<List<String>> recommendSetmeal(@RequestBody List<String> selectedDishes) {
        log.info("智能推荐：已选菜品 {}", selectedDishes);
        List<String> recommendations = recommendService.getRecommendationsForSet(selectedDishes);
        return Result.success(recommendations);
    }
}