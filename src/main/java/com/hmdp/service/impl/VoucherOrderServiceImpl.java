package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    /**
     * 优惠券秒杀
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.根据id查询优惠劵信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        //2.判断优惠劵时间是否开启
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("优惠劵秒杀活动未开始");
        }

        //3.判断优惠劵时间是否结束
        if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())) {
            return Result.fail("优惠劵秒杀活动已结束");
        }

        //4.判断优惠劵是否还有库存
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("优惠劵库存不足");
        }

        //5.修改优惠劵的数量
        boolean flag = seckillVoucherService.update()
                .setSql("stock= stock -1")
                .eq("voucher_id", voucherId)
                .gt("stock",0).update();
        if(!flag){
            return Result.fail("优惠劵库存不足");
        }

        //6.创建用户-订单记录
        VoucherOrder voucherOrder = new VoucherOrder();
        //设置订单id
        long orderID = redisIdWorker.generateSituationID("order");
        voucherOrder.setId(orderID);
        //设置用户id
        voucherOrder.setUserId(UserHolder.getUser().getId());
        //设置优惠劵id
        voucherOrder.setVoucherId(voucherId);
        //新增记录
        save(voucherOrder);

        //7.返回订单id
        return Result.ok(orderID);
    }
}
