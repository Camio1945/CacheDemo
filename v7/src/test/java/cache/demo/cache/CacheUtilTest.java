package cache.demo.cache;

import common.WithSpringBootTestAnnotation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CacheUtilTest extends WithSpringBootTestAnnotation {
  @Autowired CacheUtil cacheUtil;

  @Test
  void getHitRatioPercentage() {
    double hitRatioPercentage = cacheUtil.getHitRatioPercentage();
    Assertions.assertTrue(hitRatioPercentage >= 0, "命中率应该大于等于0");
  }
}
