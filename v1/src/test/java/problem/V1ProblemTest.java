package problem;

import static cache.demo.service.impl.GoodsServiceImpl.GOODS_ID_CACHE_PREFIX;

import cache.demo.mapper.GoodsMapper;
import cache.demo.service.IGoodsService;
import cn.hutool.core.date.TimeInterval;
import common.WithSpringBootTestAnnotation;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 用于演示 v1 版本的问题：存在缓存穿透的问题。
 *
 * <pre>
 * ID 为 100001 的商品不存在，但是每次查询都会去查询数据库，当频繁访问时，会导致数据库压力增大。
 * 注：由于 IDE、Java、MySQL 都运行在同一台电脑上，数据库占用的 CPU 不会太大，但也会明显上升。
 * </pre>
 *
 * @author Camio1945
 */
@Slf4j
class V1ProblemTest extends WithSpringBootTestAnnotation {
  private static final int MAX_ID = 100000;
  private static final String KEY_PREFIX = GOODS_ID_CACHE_PREFIX + "::";

  @Autowired IGoodsService goodsService;
  @Autowired GoodsMapper goodsMapper;
  @Autowired StringRedisTemplate stringRedisTemplate;

  @BeforeEach
  void beforeEach() {
    // 删除一些缓存
    stringRedisTemplate.delete(KEY_PREFIX + MAX_ID);
    stringRedisTemplate.delete(KEY_PREFIX + (MAX_ID + 1));

    // 删除不应该存在的数据
    goodsService.delete(MAX_ID + 1);

    // 这里先查一遍，防止初始化数据库连接的耗时影响测试结果（这里与缓存无关）
    goodsMapper.selectById(MAX_ID);
    goodsMapper.selectById(MAX_ID + 1);
  }

  @Test
  void problem() {
    log.info("test: 耗时 20 秒左右，请耐心等待...");
    int times = 10000;

    // 先测试一下正常情况，数据库中存在，查了一次之后，后续就会查缓存
    TimeInterval timeInterval = new TimeInterval();
    IntStream.range(0, times).forEach(i -> goodsService.getById(MAX_ID));
    long timeWithExistingGoods = timeInterval.intervalMs();
    log.info("test: 查询存在的商品耗时: {} ms\n", timeWithExistingGoods);

    // 再测试一下有问题的情况，数据库中不存在，每次都会去查数据库
    timeInterval = new TimeInterval();
    IntStream.range(0, times).forEach(i -> goodsService.getById(MAX_ID + 1));
    long timeWithNotExistingGoods = timeInterval.intervalMs();
    log.info("test: 查询不存在的商品耗时: {} ms\n", timeWithNotExistingGoods);

    // 对比一下两种情况的耗时
    Assertions.assertTrue(timeWithExistingGoods < timeWithNotExistingGoods);
  }
}
