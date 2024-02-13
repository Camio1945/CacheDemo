package solution;

import cache.demo.cache.CacheUtil;
import cache.demo.service.IGoodsService;
import common.WithSpringBootTestAnnotation;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 用于演示怎么解决 v5 版本的问题。详见 README.md
 *
 * @author Camio1945
 */
@Slf4j
class V5SolutionTest extends WithSpringBootTestAnnotation {

  private static final int MIN_ID = 1;

  @Autowired IGoodsService goodsService;
  @Autowired CacheUtil cacheUtil;

  @Test
  void solution() {
    double hitRatioPercentageBefore = cacheUtil.getHitRatioPercentage();
    int count = 100000;
    IntStream.range(0, count).parallel().forEach(i -> goodsService.getById(MIN_ID));
    double hitRatioPercentageAfter = cacheUtil.getHitRatioPercentage();
    log.info("hitRatioPercentageBefore: {}%", hitRatioPercentageBefore);
    log.info("hitRatioPercentageAfter : {}%", hitRatioPercentageAfter);
    Assertions.assertTrue(
        hitRatioPercentageAfter >= hitRatioPercentageBefore, "命中率（大概率）应该有所提高（除非原来已经高于99.999%）");
  }
}
