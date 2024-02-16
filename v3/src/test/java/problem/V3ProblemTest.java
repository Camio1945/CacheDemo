package problem;


import cache.demo.service.IGoodsService;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.unit.DataSizeUtil;
import common.WithSpringBootTestAnnotation;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.LongStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.core.*;

/**
 * 用于演示 v3 版本的问题：
 *
 * <pre>
 * 商品 id 是整数，而且是自增的，商品数量有限，比如我的例子里面是 10 万个，
 * 而整数的范围是 21 亿多，如果有恶意用户依次访问这 21 亿不存在的商品 id，
 * 由于我们为了解决缓存穿透问题而缓存了空值，那么这 21 亿不存在的 id 也会在缓存中存在，从而导致内存暴增。
 * </pre>
 *
 * @author Camio1945
 */
@Slf4j
class V3ProblemTest extends WithSpringBootTestAnnotation {
  private static final int MAX_ID = 100000;

  @Autowired IGoodsService goodsService;
  @Autowired StringRedisTemplate stringRedisTemplate;

  /**
   * 提醒：
   *
   * <pre>
   * 请先注释掉 application.yml 中的以下配置，以防日志刷屏：
   * mybatis-plus:
   *   configuration:
   *     log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
   * </pre>
   */
  @Test
  void problem() {
    flushDb();
    long usedMemoryBefore = getUsedMemory();
    int totalRound = 100000;
    int eachRoundTimes = Integer.MAX_VALUE / totalRound;
    LongStream.range(MAX_ID, (MAX_ID + eachRoundTimes + 1))
        .parallel()
        .forEach(i -> goodsService.getById((int) i));
    long usedMemoryAfter = getUsedMemory();
    long eachRoundIncreasedMemory = (usedMemoryAfter - usedMemoryBefore);
    long totalIncreasedMemory = eachRoundIncreasedMemory * totalRound;
    log.info("eachRoundTimes          : {}", Convert.numberToSimple(eachRoundTimes));
    log.info("usedMemoryBefore        : {}", DataSizeUtil.format(usedMemoryBefore));
    log.info("usedMemoryAfter         : {}", DataSizeUtil.format(usedMemoryAfter));
    log.info("eachRoundIncreasedMemory: {}", DataSizeUtil.format(eachRoundIncreasedMemory));
    // 这个预估跟实际值有较大的偏差，保守估计应该在 300GB 以上
    log.info("totalIncreasedMemory    : {}", DataSizeUtil.format(totalIncreasedMemory));
    // 每个空值的大小大约是 60 字节，如：***org.springframework.cache.support.NullValue***
    int nullValueByteAtLeast = 60;
    Assertions.assertTrue(
        usedMemoryAfter > (usedMemoryBefore + (eachRoundTimes * nullValueByteAtLeast)));
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
