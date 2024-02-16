package problem;

import static cache.demo.service.impl.GoodsServiceImpl.GOODS_ID_CACHE_PREFIX;

import cache.demo.service.IGoodsService;
import cn.hutool.core.date.TimeInterval;
import common.WithSpringBootTestAnnotation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 用于演示 v2 版本的问题：见 README.md 文档。
 *
 * <pre>
 * 要查看控制台打印的 SQL 语句，需要打开 application.yml 中的如下配置：
 * mybatis-plus:
 *   configuration:
 *     log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
 * </pre>
 *
 * @author Camio1945
 */
@Slf4j
class V2ProblemTest extends WithSpringBootTestAnnotation {
  private static final int MAX_ID = 100000;
  private static final String KEY_PREFIX = GOODS_ID_CACHE_PREFIX + "::";

  @Autowired IGoodsService goodsService;
  @Autowired StringRedisTemplate stringRedisTemplate;

  @Test
  void problem() throws InterruptedException {
    log.info(
        "\n\n"
            + "要查看控制台打印的 SQL 语句，需要打开 application.yml 中的如下配置：\n"
            + "mybatis-plus:\n"
            + "  configuration:\n"
            + "    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl\n");
    // 先删除缓存
    stringRedisTemplate.delete(KEY_PREFIX + MAX_ID);
    int count = 1000;
    TimeInterval timeInterval = new TimeInterval();
    CountDownLatch countDownLatch = new CountDownLatch(count);
    List<Thread> threadList = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      Thread thread =
          Thread.startVirtualThread(
              () -> {
                try {
                  countDownLatch.await();
                } catch (InterruptedException e) {
                  log.error("InterruptedException", e);
                }
                goodsService.getById(MAX_ID);
              });
      threadList.add(thread);
      countDownLatch.countDown();
    }
    for (Thread thread : threadList) {
      thread.join();
    }
    log.info("\nsolution1 耗时：{}ms", timeInterval.intervalMs());
    log.info("从控制台应该可以看到打印了多条 SQL 语句。按道理来讲，只应该打印一条，其余的查询缓存。");
  }
}
