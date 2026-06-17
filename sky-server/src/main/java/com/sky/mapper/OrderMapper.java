package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {
    /**
     * 插入订单数据
     * @param orders
     */
     void insert(Orders orders);



    /**
     * 根据订单号查询订单
     * @param number 订单号
     * @return 订单对象
     */
    Orders getByNumber(String number);

    /**
     * 更新订单信息
     * @param orders 订单对象
     */
    void update(Orders orders);


    /**
     * 分页条件查询并按下单时间排序
     * @param ordersPageQueryDTO
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);


    /**
     * 根据id查询订单
     * @param id
     */
    @Select("select * from orders where id=#{id}")
    Orders getById(Long id);

    /**
     * 根据状态统计订单数量
     * @param status
     */
    @Select("select count(id) from orders where status = #{status}")
    Integer countStatus(Integer status);


    /**
     *
     * @param status
     * @param orderTime
     * @return
     */

    /**
     * 根据订单状态和时间查询订单
     * @param status
     * @param orderTime
     * @return
     */
    @Select("select * from orders where status = #{status} and order_time < #{orderTime}")
List<Orders> getByStatusAndOrderTimelT(Integer status, LocalDateTime orderTime);


    /**
     * 根据动态条件统计营业额
     * @param map
     * @return
     */
    Double sumByMap(Map map);

    /**
     * 根据动态条件统计订单数据
     * @param map
     * @return
     */
    Integer countByMap(Map map);


    /**
     * 统计指定时间内的销量排名前10
     * @param begin
     * @param end
     * @return
     */
    List<GoodsSalesDTO>getSalesTop10(LocalDateTime begin, LocalDateTime end);


    // 查询指定时间范围内已取消的订单（状态为已取消，且取消时间在区间内）
    @Select("SELECT * FROM orders WHERE status = 6 AND cancel_time BETWEEN #{start} AND #{end}")
    List<Orders> getCancelledOrdersBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * 获取指定时间范围内的订单ID列表
     * @param start 开始时间
     * @param end 结束时间
     * @return 订单ID列表
     */
    @Select("SELECT id FROM orders WHERE order_time BETWEEN #{start} AND #{end}")
    List<Long> getOrderIdsByTimeRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

}
