package solution;

import static cache.demo.service.impl.GoodsServiceImpl.GOODS_ID_CACHE_PREFIX;

import cache.demo.mapper.GoodsMapper;
import cache.demo.service.IGoodsService;
import cn.hutool.core.collection.ConcurrentHashSet;
import common.WithSpringBootTestAnnotation;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 用于演示怎么解决 v4 版本的问题。
 *
 * <pre>
 * 问题详见 V4ProblemTest.java
 * 解决办法：
 *     设置过期时间时，给一个随机的偏移量。虽然是在同一时间进入的缓存，
 *     但是失效时间大概率是不一样的，把单一时间点的压力分散到了不同的时间点。
 *     详见 {@link cache.demo.common.RandomOffsetTtlFunction}
 *
 * </pre>
 *
 * @author Camio1945
 */
@Slf4j
class V4SolutionTest extends WithSpringBootTestAnnotation {
  private static final String KEY_PREFIX = GOODS_ID_CACHE_PREFIX + "::";

  @Autowired IGoodsService goodsService;
  @Autowired GoodsMapper goodsMapper;
  @Autowired StringRedisTemplate stringRedisTemplate;

  @Test
  void solution() throws InterruptedException {
    flushDb();
    int count = 50;
    // 执行以下这一段代码时，会从数据库中加载数据，并存入缓存中
    loadFromDbToCache(count);
    // 获取失效时间
    Set<Long> expireTimes = getExpireTimes(count);
    Assertions.assertEquals(count, expireTimes.size(), "应该（大概率）在不同的时间点过期，但是实际上遇到了在同一秒过期的情况");
  }

  private void flushDb() {
    RedisConnection connection =
        Objects.requireNonNull(stringRedisTemplate.getConnectionFactory()).getConnection();
    RedisServerCommands redisServerCommands = connection.serverCommands();
    redisServerCommands.flushDb(RedisServerCommands.FlushOption.SYNC);
  }

  private void loadFromDbToCache(int count) throws InterruptedException {
    // 使用 CountDownLatch 来让不同商品的失效时间尽量保持一致
    CountDownLatch countDownLatch = new CountDownLatch(count);
    List<Thread> threadList = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      final int id = i;
      Thread thread =
          Thread.startVirtualThread(
              () -> {
                try {
                  countDownLatch.await();
                } catch (InterruptedException e) {
                  log.error("InterruptedException", e);
                }
                goodsService.getById(id);
              });
      threadList.add(thread);
      countDownLatch.countDown();
    }
    joinThread(threadList);
  }

  private Set<Long> getExpireTimes(int count) throws InterruptedException {
    Set<Long> expireTimes = new ConcurrentHashSet<>();
    // 使用 CountDownLatch 来让查询尽量同时开始
    CountDownLatch countDownLatch = new CountDownLatch(count);
    List<Thread> threadList = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      final int id = i;
      Thread thread =
          Thread.startVirtualThread(
              () -> {
                try {
                  countDownLatch.await();
                } catch (InterruptedException e) {
                  log.error("InterruptedException", e);
                }
                expireTimes.add(stringRedisTemplate.getExpire(KEY_PREFIX + id));
              });
      threadList.add(thread);
      countDownLatch.countDown();
    }
    joinThread(threadList);
    return expireTimes;
  }

  private static void joinThread(List<Thread> threadList) throws InterruptedException {
    for (Thread thread : threadList) {
      thread.join();
    }
  }
}
