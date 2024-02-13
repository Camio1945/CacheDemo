package cache.demo.entity;

import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GoodsTest {
  @Test
  void testEntity() {
    Goods goods = new Goods();
    goods.setId(1);
    goods.setStoreId(1);
    goods.setName("Camio");
    goods.setStock(100);
    goods.setPrice(new BigDecimal(1));
    Assertions.assertEquals(1, goods.getId().intValue());
    Assertions.assertEquals(1, goods.getStoreId().intValue());
    Assertions.assertEquals("Camio", goods.getName());
    Assertions.assertEquals(100, goods.getStock());
    Assertions.assertEquals(new BigDecimal(1), goods.getPrice());
  }
}
