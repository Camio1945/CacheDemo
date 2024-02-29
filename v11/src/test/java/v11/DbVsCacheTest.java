package v11;

import static cache.demo.cache.WeiboCache.*;

import cache.demo.cache.CacheUtil;
import cache.demo.mapper.*;
import cache.demo.service.*;
import cn.hutool.core.date.*;
import common.WithSpringBootTestAnnotation;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.*;

/**
 * 微博表中有 1000 条数据，商品表中有 10 万条数据，都不算非常大的表。 <br>
 * 这样的表，如果根据主键查询，速度会很快。如果查缓存，速度也会很快。 <br>
 * 这次测试是想对比一下他们在单线程和多线程下的性能差异。 <br>
 *
 * @author Camio1945
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DbVsCacheTest extends WithSpringBootTestAnnotation {
  private static final int MIN_ID = 1;
  private static final int MAX_WEIBO_ID = 1000;
  private static final int MAX_GOODS_ID = 100000;

  @Autowired IWeiboService weiboService;
  @Autowired IUserService userService;
  @Autowired IGoodsService goodsService;
  @Autowired CacheUtil cacheUtil;

  /** 引入 Mapper 是为了直接查数据库，不经过缓存 */
  @Autowired WeiboMapper weiboMapper;

  @Autowired GoodsMapper goodsMapper;

  @Autowired RedisTemplate<String, Object> redisTemplate;

  @BeforeEach
  void beforeEach() {
    // 先查询一次，确保缓存中有值
    IntStream.range(0, MAX_WEIBO_ID + 1).parallel().forEach(i -> weiboService.getById(i));
    IntStream.range(0, MAX_GOODS_ID + 1).parallel().forEach(i -> goodsService.getById(i));
  }

  @Test
  @Order(1)
  void weiboTest() throws InterruptedException {
    int[] threadCounts = {1, 4, 16, 64};
    for (int threadCount : threadCounts) {
      dbVsCacheMultiThreads(threadCount, true);
    }
    System.out.println();
    for (int threadCount : threadCounts) {
      dbVsCacheMultiThreads(threadCount, false);
    }
  }

  /**
   * 多线程对比数据库查询和缓存查询的性能
   *
   * @param threadCount 线程数
   * @param isWeibo 是否是微博表，true 表示是微博表，false 表示是商品表
   * @throws InterruptedException
   */
  void dbVsCacheMultiThreads(int threadCount, boolean isWeibo) throws InterruptedException {
    TimeInterval timeInterval = new TimeInterval();
    CountDownLatch countDownLatchDb = new CountDownLatch(threadCount);
    List<Thread> threadList = new ArrayList<>();
    Random random = new Random();
    int totalRound = 1000;
    for (int i = 0; i < threadCount; i++) {
      Thread thread =
          Thread.startVirtualThread(
              () -> {
                awaitCountDownLatch(countDownLatchDb);
                if (isWeibo) {
                  for (int j = MIN_ID; j <= totalRound; j++) {
                    weiboMapper.selectById(random.nextInt(MAX_WEIBO_ID) + 1);
                  }
                } else {
                  for (int j = MIN_ID; j <= totalRound; j++) {
                    goodsMapper.selectById(random.nextInt(MAX_GOODS_ID) + 1);
                  }
                }
              });
      threadList.add(thread);
      countDownLatchDb.countDown();
    }
    joinThreads(threadList);

    long dbTime = timeInterval.intervalMs();

    timeInterval = new TimeInterval();
    CountDownLatch countDownLatchCache = new CountDownLatch(threadCount);
    threadList = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
      Thread thread =
          Thread.startVirtualThread(
              () -> {
                awaitCountDownLatch(countDownLatchCache);
                if (isWeibo) {
                  for (int j = MIN_ID; j <= totalRound; j++) {
                    weiboService.getById(random.nextInt(MAX_WEIBO_ID) + 1);
                  }
                } else {
                  for (int j = MIN_ID; j <= totalRound; j++) {
                    goodsService.getById(random.nextInt(MAX_GOODS_ID) + 1);
                  }
                }
              });
      threadList.add(thread);
      countDownLatchCache.countDown();
    }
    joinThreads(threadList);

    long cacheTime = timeInterval.intervalMs();
    log.info(
        "{} ：{} 线程数据库查询耗时：{} ms，缓存查询耗时：{} ms",
        isWeibo ? "微博" : "商品",
        threadCount,
        dbTime,
        cacheTime);
  }

  private void awaitCountDownLatch(CountDownLatch countDownLatch) {
    try {
      countDownLatch.await();
    } catch (InterruptedException e) {
      log.error("InterruptedException", e);
    }
  }

  private static void joinThreads(List<Thread> threadList) throws InterruptedException {
    for (Thread thread : threadList) {
      thread.join();
    }
  }
}
