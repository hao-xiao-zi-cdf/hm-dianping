package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.ILock;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private ILock lock;

    @Resource
    @Qualifier("RedissonClient")
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static DefaultRedisScript<Long> SECKILL_SCRIPT;

    private IVoucherOrderService proxy;

    private static final BlockingQueue<VoucherOrder> queue = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        //加载脚本文件
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        //设置返回类型
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //异步下单
    public class AsynchronousOrderPlacement implements Runnable{
        @Override
        public void run() {
            while(true){
                try {
                    //1.从阻塞队列中获取订单
                    VoucherOrder take = queue.take();
                    //2.创建订单
                    handleVoucherOrder(take);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


    /**
     * 优惠劵秒杀
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {

        //0.获取用户id
        Long userId = UserHolder.getUser().getId();

        //1.执行lua脚本
        Long ret = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );

        int result = ret.intValue();
//
        //2.判断返回值，是否有购买资格
        if(result != 0){
            //2.1返回非0值，不具有购买资格
            return Result.fail(result == 1 ? "库存不足" : "一人只能购买一单");
        }

        //2.2.创建订单记录，将userId,OrderId,VoucherId封装成订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //2.1设置订单id
        long orderID = redisIdWorker.generateSituationID("order");
        voucherOrder.setId(orderID);
        //2.2设置用户id
        voucherOrder.setUserId(UserHolder.getUser().getId());
        //2.3设置优惠劵id
        voucherOrder.setVoucherId(voucherId);

        //3.放入阻塞队列
        queue.add(voucherOrder);

        //TODO获取代理对象（事务）
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //4.返回订单id
        return Result.ok(0);
    }

    /**
     * 创建订单
     * @param voucherOrder
     */
    public void handleVoucherOrder(VoucherOrder voucherOrder){
        Long id = voucherOrder.getUserId();
        //获取锁
        RLock lock = redissonClient.getLock("order:" + id);
        //尝试加锁
        boolean flag = lock.tryLock();
        if(!flag){
            log.error("创建订单异常");
        }
        try{
            proxy.createVoucherOrder(voucherOrder);
        }finally {
            lock.unlock();
        }
    }
        /**
         * 优惠券秒杀
         * @param voucherId
         * @return
         */
//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //1.根据id查询优惠劵信息
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//
//        //2.判断优惠劵时间是否开启
//        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("优惠劵秒杀活动未开始");
//        }
//
//        //3.判断优惠劵时间是否结束
//        if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())) {
//            return Result.fail("优惠劵秒杀活动已结束");
//        }
//
//        //4.判断优惠劵是否还有库存
//        if (seckillVoucher.getStock() < 1) {
//            return Result.fail("优惠劵库存不足");
//        }
//
//        Long id = UserHolder.getUser().getId();
////        synchronized (id.toString().intern()){
////            //获取代理对象（事务）
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
////        boolean flag = lock.tryLock("order:" + id, 1200L);
//        //获取锁
//        RLock lock = redissonClient.getLock("order:" + id);
//        //尝试加锁
//        boolean flag = lock.tryLock();
//        if(!flag){
//            return Result.fail("请稍后重试！");
//        }
//        try{
//            //获取代理对象（事务）
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            lock.unlock();
//        }
//    }

        /**
         * 创建新订单
         *
         * @param voucherOrder
         * @return
         */
        //synchronized加在方法上，this指向方法调用者，即VoucherOrderServiceImpl实例，由于spring管理该对象，所以指向的实例为同一个
        //多线程访问时，this指向的是同一个
        @Override
        @Transactional
        public void createVoucherOrder (VoucherOrder voucherOrder){
            Long id = voucherOrder.getUserId();
            //5.一人一单，判断订单表中是否已经存在记录
            Integer count = query().eq("user_id", id).eq("voucher_id", voucherOrder.getVoucherId()).count();
            if (count > 0) {
                log.error("每个用户只限购买一单哦");
            }

            //6.修改优惠劵的数量
            boolean flag = seckillVoucherService.update()
                    .setSql("stock= stock -1")
                    .eq("voucher_id", voucherOrder.getVoucherId())
                    .gt("stock", 0).update();
            if (!flag) {
                log.error("优惠劵库存不足");
            }

            //7.新增订单
            save(voucherOrder);
        }
}
