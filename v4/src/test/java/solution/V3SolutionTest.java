package solution;

import cache.demo.mapper.GoodsMapper;
import cache.demo.service.IGoodsService;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.unit.DataSizeUtil;
import common.WithSpringBootTestAnnotation;
import java.util.*;
import java.util.stream.LongStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 用于演示怎么解决 v3 版本的问题。
 *
 * <pre>
 * 问题详见 V3ProblemTest.java
 * 解决办法（不同业务的解决办法不一样，这里仅仅只是提供一个思路）：
 *    项目启动时（见{@link cache.demo.listener.ApplicationEventListener}），
 *    查询商品表中的最大 id，然后加上一个保险值（比如 1000），得到的结果存入静态变量中，
 *    每次新增商品时，更新这个最大的 id，也需要加上一个保险值（防止集群中的变量来不及更新）。
 *    每次查询商品时，如果商品 id 大于这个上限值，则直接抛异常。
 *    一般来说，正常的业务是不会访问不存在的 id 的，对于恶意请求，抛异常也无所谓。
 * </pre>
 *
 * @author Camio1945
 */
@Slf4j
class V3SolutionTest extends WithSpringBootTestAnnotation {
  private static final int MAX_ID = 100000;

  @Autowired IGoodsService goodsService;

  @Autowired GoodsMapper goodsMapper;

  @Autowired StringRedisTemplate stringRedisTemplate;

  @Test
  void solution() {
    flushDb();
    long usedMemoryBefore = getUsedMemory();
    int totalRound = 10000;
    int eachRoundTimes = Integer.MAX_VALUE / totalRound;
    LongStream.range(MAX_ID, (MAX_ID + eachRoundTimes + 1))
        .parallel()
        .forEach(
            i -> {
              try {
                goodsService.getById((int) i);
              } catch (Exception e) {
                // 这里的异常直接忽略掉
              }
            });
    long usedMemoryAfter = getUsedMemory();
    long eachRoundIncreasedMemory = (usedMemoryAfter - usedMemoryBefore);
    log.info("eachRoundTimes          : {}", Convert.numberToSimple(eachRoundTimes));
    log.info("usedMemoryBefore        : {}", DataSizeUtil.format(usedMemoryBefore));
    log.info("usedMemoryAfter         : {}", DataSizeUtil.format(usedMemoryAfter));
    log.info("eachRoundIncreasedMemory: {}", DataSizeUtil.format(eachRoundIncreasedMemory));
    // 第一轮允许缓存 1000 个空对象，因此还是会有一些内存增加，但是是很小的，小于 300KB，后面的轮次就不会有内存增加了
    Assertions.assertTrue(eachRoundIncreasedMemory < 300 * 1024);
  }

  private void flushDb() {
    RedisConnection connection =
        Objects.requireNonNull(stringRedisTemplate.getConnectionFactory()).getConnection();
    RedisServerCommands redisServerCommands = connection.serverCommands();
    redisServerCommands.flushDb(RedisServerCommands.FlushOption.SYNC);
  }

  private long getUsedMemory() {
    RedisConnection connection =
        Objects.requireNonNull(stringRedisTemplate.getConnectionFactory()).getConnection();
    RedisServerCommands redisServerCommands = connection.serverCommands();
    Properties info = redisServerCommands.info();
    assert info != null;
    return Convert.toLong(info.getProperty("used_memory"));
  }
}
