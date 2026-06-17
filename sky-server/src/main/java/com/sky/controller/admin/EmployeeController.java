package com.sky.controller.admin;

import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.properties.JwtProperties;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.EmployeeService;
import com.sky.utils.JwtUtil;
import com.sky.vo.EmployeeLoginVO;
import com.sky.vo.LoginResponseVO;
import io.jsonwebtoken.Claims;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Api(tags = "员工管理模块")
@RestController
@RequestMapping("/admin/employee")
@Slf4j
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private JwtProperties jwtProperties;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;  // 注入 Redis
    @Qualifier("redisTemplate")
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 登录（双Token版本）
     */
    @PostMapping("/login")
    public Result<LoginResponseVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) {
        log.info("员工登录：{}", employeeLoginDTO);
        Employee employee = employeeService.login(employeeLoginDTO);

        // 1. 生成 accessToken
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        String accessToken = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);

        // 2. 生成 refreshToken
        String refreshToken = JwtUtil.createJWT(
                jwtProperties.getAdminRefreshSecretKey(),
                jwtProperties.getAdminRefreshTtl(),
                claims);

        // 3. 将 refreshToken 存入 Redis（关键：使用 stringRedisTemplate）
        String refreshKey = "refresh_token:" + refreshToken;
        stringRedisTemplate.opsForValue().set(refreshKey,
                employee.getId().toString(),
                jwtProperties.getAdminRefreshTtl(),
                TimeUnit.MILLISECONDS);

        // 4. 维护 userId -> refreshToken 的映射（方便后续吊销）
        String userRefreshKey = "user_refresh:" + employee.getId();
        stringRedisTemplate.opsForValue().set(userRefreshKey,
                refreshToken,
                jwtProperties.getAdminRefreshTtl(),
                TimeUnit.MILLISECONDS);

        // 5. 封装返回
        LoginResponseVO response = LoginResponseVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();

        return Result.success(response);
    }
    /**
     * 退出登录（吊销 refreshToken）
     */
    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader(value = "refreshToken", required = false) String refreshToken) {
        Long empId = BaseContext.getCurrentId();
        if (empId != null && refreshToken != null) {
            // 删除 refreshToken 及其映射
            stringRedisTemplate.delete("refresh_token:" + refreshToken);
            stringRedisTemplate.delete("user_refresh:" + empId);
            log.info("员工 {} 登出，已吊销 refreshToken", empId);
        }
        return Result.success();
    }

    /**
     * 新增员工
     * @param employeeDTO
     * @return
     */
    @PostMapping
    @ApiOperation("新增员工")
    public Result save(@RequestBody EmployeeDTO employeeDTO) {
        log.info("新增员工“{}", employeeDTO);
        System.out.println("当前线程的id"+Thread.currentThread().getId());
        employeeService.save(employeeDTO);
        return Result.success();
    }


    /**
     *
     * @param employeePageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("员工分页查询")
    public Result<PageResult>page(EmployeePageQueryDTO employeePageQueryDTO) {
        log.info("员工分页查询，参数为：{}", employeePageQueryDTO);
        PageResult pageResult=employeeService.pageQuery(employeePageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 启用禁用员工账号
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("启用禁用员工账号")
    public Result startOrStop(@PathVariable Integer status,Long id)  {
        log.info("启用禁用员工账号:{}，{}",status,id);
        employeeService.startOrStop(status,id);
        return Result.success();
    }

    /**
     * 根据id查询员工信息
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询员工信息")
    public Result<Employee>getById(@PathVariable Long id) {
        Employee employee=employeeService.getById(id);
        return Result.success(employee);
    }

    /**
     * 编辑员工信息
     * @param employeeDTO
     * @return
     */
    @PutMapping
    @ApiOperation("编辑员工信息")
    public Result update(@RequestBody EmployeeDTO employeeDTO){
        log.info("编辑员工信息：{}",employeeDTO);
        employeeService.update(employeeDTO);
        return Result.success();
    }

    @PostMapping("/refresh")
    @ApiOperation("刷新AccessToken")
    public Result<Map<String, String>> refresh(@RequestHeader("Refresh-Token") String refreshToken) {
        log.info("刷新token: {}", refreshToken);
        try {
            // 1. 检查 Redis 中是否存在该 refreshToken
            String refreshKey = "refresh_token:" + refreshToken;
            if (Boolean.FALSE.equals(stringRedisTemplate.hasKey(refreshKey))) {
                return Result.error("Refresh token 无效或已过期");
            }

            // 2. 解析 refreshToken 获取员工ID
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminRefreshSecretKey(), refreshToken);
            Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());

            // 3. 生成新的 accessToken
            Map<String, Object> newClaims = new HashMap<>();
            newClaims.put(JwtClaimsConstant.EMP_ID, empId);
            String newAccessToken = JwtUtil.createJWT(
                    jwtProperties.getAdminSecretKey(),
                    jwtProperties.getAdminTtl(),
                    newClaims);

            // 使用 HashMap 替代 Map.of (Java 8 兼容)
            Map<String, String> result = new HashMap<>();
            result.put("accessToken", newAccessToken);
            return Result.success(result);
        } catch (Exception e) {
            log.error("刷新token失败", e);
            return Result.error("Refresh token 验证失败");
        }
    }
}
