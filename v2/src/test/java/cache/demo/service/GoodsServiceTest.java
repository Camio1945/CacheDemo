package cache.demo.service;

import static cache.demo.service.impl.GoodsServiceImpl.GOODS_ID_CACHE_PREFIX;

import cache.demo.entity.Goods;
import cache.demo.mapper.GoodsMapper;
import common.WithSpringBootTestAnnotation;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * 商品服务测试类
 *
 * @author Camio1945
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GoodsServiceTest extends WithSpringBootTestAnnotation {
  private static final int MIN_ID = 1;

  private static final int MAX_ID = 100000;

  private static final String KEY_PREFIX = GOODS_ID_CACHE_PREFIX + "::";

  @Autowired IGoodsService goodsService;

  /** 引入 Mapper 是为了直接查数据库，不经过缓存 */
  @Autowired GoodsMapper goodsMapper;

  @Autowired RedisTemplate<String, Object> redisTemplate;

  @BeforeEach
  void beforeEach() {
    // 删除一些缓存，并验证删除成功
    int[] ids = {MIN_ID, MAX_ID + 1};
    for (int id : ids) {
      String key = KEY_PREFIX + id;
      redisTemplate.delete(key);
      Boolean hasKey = redisTemplate.hasKey(key);
      Assertions.assertTrue(hasKey != null && !hasKey);
    }
  }

  @Test
  @Order(1)
  void getById() {
    // 测试参数为空的情况
    Assertions.assertThrows(RuntimeException.class, () -> goodsService.getById(null));

    // 测试正常情况，这里查的是数据库，并且会把查到的数据放入缓存
    Goods goods = goodsService.getById(MIN_ID);
    Assertions.assertNotNull(goods);
    Assertions.assertEquals(MIN_ID, goods.getId());

    // 确认缓存中有值，并且就是我们存进去的值
    Goods cacheValue = getGoodsFromCache(KEY_PREFIX + MIN_ID);
    Assertions.assertNotNull(cacheValue);
    Assertions.assertEquals(MIN_ID, cacheValue.getId().intValue());

    // 测试查询数据库中不存在的数据，缓存中查出来的会是 null，但 redis 中还是会有这个键
    int notExistsId = Integer.MAX_VALUE;
    Goods notExistsGoods = goodsService.getById(notExistsId);
    Assertions.assertNull(notExistsGoods);
    Boolean hasKey = redisTemplate.hasKey(KEY_PREFIX + notExistsId);
    Assertions.assertTrue(hasKey != null && hasKey);

    // 验证缓存中没有值
    String notExistsKey = KEY_PREFIX + notExistsId;
    cacheValue = getGoodsFromCache(notExistsKey);
    Assertions.assertNull(cacheValue);
  }

  private Goods getGoodsFromCache(String key) {
    try {
      return (Goods) redisTemplate.opsForValue().get(key);
    } catch (RuntimeException e) {
      if (e instanceof SerializationException) {
        String message = e.getMessage();
        if (message != null && message.contains("Could not read JSON")) {
          return null;
        }
      }
      throw e;
    }
  }

  @Test
  @Order(2)
  void add() {
    // 测试参数为空的情况
    Assertions.assertThrows(RuntimeException.class, () -> goodsService.add(null));

    // 先从数据库中删除指定的数据，以方便接下来添加，以免主键冲突
    int newId = MAX_ID + 1;
    goodsService.delete(newId);

    // 特意缓存空值，确认 redis 缓存中存在这个键
    goodsService.getById(newId);
    Boolean hasKey = redisTemplate.hasKey(KEY_PREFIX + newId);
    Assertions.assertTrue(hasKey != null && hasKey);

    // 测试添加商品到数据库中
    Goods goods = new Goods();
    // 为了测试方便，这里指定了 id ，但数据库中的 id 是自增的，实际开发时不需要也不应该指定 id，
    // 强行指定 id 时，如果该 id 在数据库中已经存在了，而并不会新增记录，而会修改记录，从业务上讲就是 Bug 了。
    goods.setId(newId);
    goods.setStoreId(1);
    goods.setName("店铺1的商品" + newId);
    goods.setStock(100);
    goods.setPrice(new BigDecimal("100.00"));
    boolean isSuccess = goodsService.add(goods);
    Assertions.assertTrue(isSuccess);

    // 验证添加成功后会从缓存中删除数据
    hasKey = redisTemplate.hasKey(KEY_PREFIX + newId);
    Assertions.assertTrue(hasKey == null || !hasKey);
  }

  @Test
  @Order(3)
  void update() {
    // 测试参数为空的情况
    Assertions.assertThrows(RuntimeException.class, () -> goodsService.update(null));
    Goods goods = goodsService.getById(MIN_ID);

    // 验证缓存中有值
    String key = KEY_PREFIX + MIN_ID;
    Assertions.assertNotNull(getGoodsFromCache(key));

    // 测试更新商品信息
    int newStoreId = Integer.MAX_VALUE;
    String newName = "更新商品名称" + MIN_ID;
    int newStock = Integer.MAX_VALUE;
    BigDecimal newPrice = new BigDecimal("1000000.00");
    goods.setStoreId(newStoreId);
    goods.setName(newName);
    goods.setStock(newStock);
    goods.setPrice(newPrice);
    boolean isSuccess = goodsService.update(goods);
    Assertions.assertTrue(isSuccess);
    Goods updatedGoods = goodsMapper.selectById(MIN_ID);
    Assertions.assertEquals(newStoreId, updatedGoods.getStoreId());
    Assertions.assertEquals(newName, updatedGoods.getName());
    Assertions.assertEquals(newStock, updatedGoods.getStock());
    Assertions.assertEquals(newPrice.doubleValue(), updatedGoods.getPrice().doubleValue());

    // 验证更新成功后会从缓存中删除数据
    Assertions.assertNull(getGoodsFromCache(key));
  }

  @Test
  @Order(4)
  void delete() {
    // 测试参数为空的情况
    Assertions.assertThrows(RuntimeException.class, () -> goodsService.delete(null));
    int id = MAX_ID + 1;
    Goods goods = goodsService.getById(id);
    if (goods != null) {
      boolean delRes = goodsService.delete(id);
      Assertions.assertTrue(delRes);

      // 验证数据库中已经没有数据
      goods = goodsMapper.selectById(id);
      Assertions.assertNull(goods);

      // 验证缓存中已经没有数据
      String key = KEY_PREFIX + id;
      Assertions.assertNull(getGoodsFromCache(key));
    }
  }
}
