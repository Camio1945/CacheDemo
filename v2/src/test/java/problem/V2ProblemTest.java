package problem;

import static cache.demo.service.impl.GoodsServiceImpl.GOODS_ID_CACHE_PREFIX;

import cache.demo.service.IGoodsService;
import common.WithSpringBootTestAnnotation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 用于演示 v2 版本的问题：高并发访问一个 key 时，如果缓存中不存在，不管数据库中是否存在，都会导致同一时刻会有多个请求查询数据库，导致数据库压力增大。
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
    // 先删除缓存
    stringRedisTemplate.delete(KEY_PREFIX + MAX_ID);
    int count = 10;
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
    log.info("从控制台应该可以看到打印了多条 SQL 语句。按道理来讲，只应该打印一条，其余的查询缓存。");
  }
}
