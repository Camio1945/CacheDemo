package solution;

import static cache.demo.service.impl.GoodsServiceImpl.GOODS_ID_CACHE_PREFIX;

import cache.demo.common.RedisConfig;
import cache.demo.mapper.GoodsMapper;
import cache.demo.service.IGoodsService;
import cache.demo.service.impl.GoodsServiceImpl;
import cn.hutool.core.date.TimeInterval;
import common.WithSpringBootTestAnnotation;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 用于演示怎么解决 v1 版本的问题。
 *
 * <pre>
 * 问题详见 V1ProblemTest.java
 * 解决方案：{@link GoodsServiceImpl#getById(Integer)} 方法中，会缓存空值。
 * 同时还有两个地方需要注意：
 *   1. application.yml 中的 spring.cache.redis.cache-null-values 应该设置为 true
 *   2. {@link RedisConfig#cacheConfiguration()} 方法中不能写 .disableCachingNullValues()，否则会导致缓存空值失效。
 * </pre>
 *
 * @author Camio1945
 */
@Slf4j
class V1SolutionTest extends WithSpringBootTestAnnotation {
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
  void solution() {
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

    // 对比一下两种情况的耗时（两边都是查缓存，但是返回空值的不需要再通过 json 字符串转换成对象，因此节省了时间）
    Assertions.assertTrue(timeWithNotExistingGoods < timeWithExistingGoods);
  }
}
