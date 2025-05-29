package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.DistributedLock;
import com.hmdp.utils.ILock;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

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

    @Autowired
    private ILock lock;

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

        Long id = UserHolder.getUser().getId();
//        synchronized (id.toString().intern()){
//            //获取代理对象（事务）
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }
        boolean flag = lock.tryLock("userID:" + id, 1200L);
        if(!flag){
            return Result.fail("请稍后重试！");
        }
        try{
            //获取代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally {
            lock.unLock("userID:" + id);
        }
    }

    /**
     * 创建新订单
     * @param voucherId
     * @return
     */
    //synchronized加在方法上，this指向方法调用者，即VoucherOrderServiceImpl实例，由于spring管理该对象，所以指向的实例为同一个
    //多线程访问时，this指向的是同一个
    @Transactional
    @Override
    public Result createVoucherOrder(Long voucherId) {
        Long id = UserHolder.getUser().getId();
        //5.一人一单，判断订单表中是否已经存在记录
//        synchronized (id.toString().intern()){
            Integer count = query().eq("user_id", id).eq("voucher_id", voucherId).count();
            if (count > 0) {
                return Result.fail("每个用户只限购买一单哦");
            }

            //6.修改优惠劵的数量
            boolean flag = seckillVoucherService.update()
                    .setSql("stock= stock -1")
                    .eq("voucher_id", voucherId)
                    .gt("stock",0).update();
            if(!flag){
                return Result.fail("优惠劵库存不足");
            }

            //7.创建用户-订单记录
            VoucherOrder voucherOrder = new VoucherOrder();
            //7.1设置订单id
            long orderID = redisIdWorker.generateSituationID("order");
            voucherOrder.setId(orderID);
            //7.2设置用户id
            voucherOrder.setUserId(UserHolder.getUser().getId());
            //7.3设置优惠劵id
            voucherOrder.setVoucherId(voucherId);
            //7.4新增记录
            save(voucherOrder);

            //8.返回订单id
            return Result.ok(orderID);
//        }
    }
}
