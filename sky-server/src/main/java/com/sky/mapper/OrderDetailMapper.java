package com.sky.mapper;

import com.sky.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderDetailMapper {
    /**
     * 批量插入订单明细数据
     * @param orderDetailList
     */
    void insertBatch(List<OrderDetail> orderDetailList);

    /**
     * 根据订单id查询订单明细
     * @param orderId
     * @return
     */
    @Select("select * from order_detail where order_id = #{orderId}")
    List<OrderDetail> getByOrderId(Long orderId);

    @Select("SELECT * FROM order_detail WHERE create_time BETWEEN #{start} AND #{end}")
    List<OrderDetail> getByTimeRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);


    /**
     * 根据订单ID列表批量查询订单明细
     * @param orderIds 订单ID列表
     * @return 订单明细列表
     */
    @Select("<script>" +
            "SELECT * FROM order_detail WHERE order_id IN " +
            "<foreach collection='orderIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<OrderDetail> getByOrderIds(@Param("orderIds") List<Long> orderIds);
}
