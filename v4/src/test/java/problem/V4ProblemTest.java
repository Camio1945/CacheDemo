package problem;

import static cache.demo.service.impl.GoodsServiceImpl.GOODS_ID_CACHE_PREFIX;

import cache.demo.service.IGoodsService;
import cn.hutool.core.collection.ConcurrentHashSet;
import common.WithSpringBootTestAnnotation;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.core.*;

/**
 * 用于演示 v4 版本的问题：
 *
 * <pre>
 *
 * </pre>
 *
 * @author Camio1945
 */
@Slf4j
class V4ProblemTest extends WithSpringBootTestAnnotation {
  private static final String KEY_PREFIX = GOODS_ID_CACHE_PREFIX + "::";
  @Autowired IGoodsService goodsService;
  @Autowired StringRedisTemplate stringRedisTemplate;

  @Test
  void problem() throws InterruptedException {
    flushDb();
    int count = 50;
    // 执行以下这一段代码时，会从数据库中加载数据，并存入缓存中
    loadFromDbToCache(count);
    // 获取失效时间
    Set<Long> expireTimes = getExpireTimes(count);
    Assertions.assertEquals(1, expireTimes.size(), "应该在同一秒内过期，但是实际上没有：" + expireTimes);
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

  private static void joinThread(List<Thread> threadList) throws InterruptedException {
    for (Thread thread : threadList) {
      thread.join();
    }
  }

  private void flushDb() {
    RedisConnection connection =
        Objects.requireNonNull(stringRedisTemplate.getConnectionFactory()).getConnection();
    RedisServerCommands redisServerCommands = connection.serverCommands();
    redisServerCommands.flushDb(RedisServerCommands.FlushOption.SYNC);
  }
}
