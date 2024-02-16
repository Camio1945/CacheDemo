package solution;

import static cache.demo.service.impl.GoodsServiceImpl.GOODS_ID_CACHE_PREFIX;

import cache.demo.mapper.GoodsMapper;
import cache.demo.service.IGoodsService;
import cache.demo.service.impl.GoodsServiceImpl;
import cn.hutool.core.date.TimeInterval;
import common.WithSpringBootTestAnnotation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 用于演示怎么解决 v2 版本的问题。
 *
 * <pre>
 * 问题详见 V2ProblemTest.java
 * 解决方案 1（不推荐）：
 *     在 {@link GoodsServiceImpl#getByIdWithSync(Integer)} 方法上的 @Cacheable 注解中设置 sync = true。
 *     相当于把目异步变成同步。
 * 解决方案 2（推荐）：
 *     在 {@link GoodsServiceImpl#getById(Integer)} 方法中，
 *     使用 {@link cache.demo.util.SingleFlightUtil#execute(String, Callable)} 方法来包装，
 *     保证只会有一个线程访问一次数据库，其他线程会共享那个线程的结果。
 * 性能：方案 2 的耗时大约是方案 1 的 1/4，推荐方案 2。
 *
 * 提醒：
 * 请先放开 application.yml 中的以下配置，以显示 SQL 日志：
 * mybatis-plus:
 *   configuration:
 *     log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
 * </pre>
 *
 * @author Camio1945
 */
@Slf4j
class V2SolutionTest extends WithSpringBootTestAnnotation {
  private static final int MAX_ID = 100000;
  private static final String KEY_PREFIX = GOODS_ID_CACHE_PREFIX + "::";
  private static final int COUNT = 1000;

  @Autowired IGoodsService goodsService;
  @Autowired GoodsMapper goodsMapper;
  @Autowired StringRedisTemplate stringRedisTemplate;

  @BeforeEach
  void setUp() {
    // 先做一次数据库操作，让数据库连接池初始化
    goodsMapper.deleteById(MAX_ID + 1);
    // 再删除缓存
    stringRedisTemplate.delete(KEY_PREFIX + MAX_ID);
  }

  /**
   * @deprecated 仅用于演示与对比，不推荐使用。推荐使用 {@link #solution2()} 方法。
   */
  @Test
  @Deprecated
  void solution1() throws InterruptedException {
    printHintLog("solution1");
    TimeInterval timeInterval = new TimeInterval();
    CountDownLatch countDownLatch = new CountDownLatch(COUNT);
    List<Thread> threadList = new ArrayList<>();
    for (int i = 0; i < COUNT; i++) {
      Thread thread =
          Thread.startVirtualThread(
              () -> {
                awaitCountDownLatch(countDownLatch);
                goodsService.getByIdWithSync(MAX_ID);
              });
      threadList.add(thread);
      countDownLatch.countDown();
    }
    joinThreads(threadList);
    log.info("\nsolution1 耗时：{}ms", timeInterval.intervalMs());
    log.info("从控制台应该能看到只打印了一条 SQL 语句。");
  }

  private static void printHintLog(String method) {
    log.info(
        "\n\n\n\n\n\n\n{} begin...\n"
            + "要查看控制台打印的 SQL 语句，需要打开 application.yml 中的如下配置：\n"
            + "mybatis-plus:\n"
            + "  configuration:\n"
            + "    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl\n",
        method);
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

  @Test
  void solution2() throws InterruptedException {
    printHintLog("solution2");
    TimeInterval timeInterval = new TimeInterval();
    CountDownLatch countDownLatch = new CountDownLatch(COUNT);
    List<Thread> threadList = new ArrayList<>();
    for (int i = 0; i < COUNT; i++) {
      Thread thread =
          Thread.startVirtualThread(
              () -> {
                awaitCountDownLatch(countDownLatch);
                goodsService.getById(MAX_ID);
              });
      threadList.add(thread);
      countDownLatch.countDown();
    }
    joinThreads(threadList);
    log.info("\nsolution2 耗时：{}ms", timeInterval.intervalMs());
    log.info("从控制台应该能看到只打印了一条 SQL 语句。");
  }
}
