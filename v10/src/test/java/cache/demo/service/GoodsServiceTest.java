package cache.demo.service;

import static cache.demo.cache.GoodsCache.*;

import cache.demo.cache.CacheUtil;
import cache.demo.cache.GoodsCache;
import cache.demo.entity.Goods;
import cache.demo.mapper.GoodsMapper;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import common.WithSpringBootTestAnnotation;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * 商品服务测试类
 *
 * @author Camio1945
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GoodsServiceTest extends WithSpringBootTestAnnotation {
  private static final int MIN_ID = 1;

  private static final int MAX_ID = 100000;

  private static final String KEY_PREFIX = GOODS_ID_CACHE_PREFIX + "::";

  @Autowired IGoodsService goodsService;
  @Autowired GoodsCache goodsCache;
  @Autowired CacheUtil cacheUtil;

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

    // 测试查询数据库中不存在的数据，如果 id 只稍微大一点点，则会查到空值，并且会把空值放入缓存
    goodsMapper.deleteById(MAX_ID + 1);
    int notExistsId = MAX_ID + 1;
    Goods notExistsGoods = goodsService.getById(notExistsId);
    Assertions.assertNull(notExistsGoods);
    Boolean hasKey = redisTemplate.hasKey(KEY_PREFIX + notExistsId);
    Assertions.assertTrue(hasKey != null && hasKey);

    // 测试查询数据库中不存在的数据，如果 id 太大，则会直接抛异常，而不会放入缓存
    int bigId = Integer.MAX_VALUE;
    Assertions.assertThrows(RuntimeException.class, () -> goodsService.getById(bigId));
    String notExistsKey = KEY_PREFIX + bigId;
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
    Assertions.assertTrue(cacheUtil.hasKey(KEY_PREFIX + newId));

    // 测试添加商品到数据库中
    Goods goods = new Goods();
    // 为了测试方便，这里指定了 id ，但数据库中的 id 是自增的，实际开发时不需要也不应该指定 id，
    // 强行指定 id 时，如果该 id 在数据库中已经存在了，而并不会新增记录，而会修改记录，从业务上讲就是 Bug 了。
    goods.setId(newId);
    int storeId = 1;
    goods.setStoreId(storeId);
    goods.setName("店铺1的商品" + newId);
    goods.setStock(100);
    goods.setPrice(new BigDecimal("100.00"));
    boolean isSuccess = goodsService.add(goods);
    Assertions.assertTrue(isSuccess);

    // 验证添加成功后会从缓存中删除数据
    Assertions.assertFalse(cacheUtil.hasKey(KEY_PREFIX + newId));
    Assertions.assertFalse(cacheUtil.hasKey(GOODS_IDS_BY_STORE_ID_CACHE_PREFIX + "::" + storeId));
    Assertions.assertFalse(
        cacheUtil.hasKey(
            GOODS_STORE_ID_NAME_CACHE_PREFIX + "::" + storeId + ":" + goods.getName()));
  }

  @Test
  @Order(3)
  void update() {
    // 测试参数为空的情况
    Assertions.assertThrows(RuntimeException.class, () -> goodsService.update(null));
    Goods goods = goodsService.getById(MIN_ID);
    Goods before = goodsService.getById(MIN_ID);

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
    Goods after = goodsMapper.selectById(MIN_ID);
    Assertions.assertEquals(newStoreId, after.getStoreId());
    Assertions.assertEquals(newName, after.getName());
    Assertions.assertEquals(newStock, after.getStock());
    Assertions.assertEquals(newPrice.doubleValue(), after.getPrice().doubleValue());

    // 验证更新成功后会从缓存中删除数据
    Assertions.assertFalse(cacheUtil.hasKey(key));
    key = GOODS_IDS_BY_STORE_ID_CACHE_PREFIX + "::" + before.getStoreId();
    Assertions.assertFalse(cacheUtil.hasKey(key));
    key = GOODS_IDS_BY_STORE_ID_CACHE_PREFIX + "::" + after.getStoreId();
    Assertions.assertFalse(cacheUtil.hasKey(key));
    key = GOODS_STORE_ID_NAME_CACHE_PREFIX + "::" + before.getStoreId() + ":" + before.getName();
    Assertions.assertFalse(cacheUtil.hasKey(key));
    key = GOODS_STORE_ID_NAME_CACHE_PREFIX + "::" + after.getStoreId() + ":" + after.getName();
    Assertions.assertFalse(cacheUtil.hasKey(key));
  }

  @Test
  @Order(4)
  void delete() {
    // 测试参数为空的情况
    Assertions.assertThrows(RuntimeException.class, () -> goodsService.delete(null));
    int id = MAX_ID + 1;

    Goods goods = goodsService.getById(id);
    if (goods != null) {
      Integer storeId = goods.getStoreId();
      String name = goods.getName();
      boolean delRes = goodsService.delete(id);
      Assertions.assertTrue(delRes);

      // 验证数据库中已经没有数据
      goods = goodsMapper.selectById(id);
      Assertions.assertNull(goods);

      // 验证缓存中已经没有数据
      String key = KEY_PREFIX + id;
      Assertions.assertNull(getGoodsFromCache(key));
      Assertions.assertFalse(cacheUtil.hasKey(GOODS_IDS_BY_STORE_ID_CACHE_PREFIX + "::" + storeId));
      key = GOODS_STORE_ID_NAME_CACHE_PREFIX + "::" + storeId + ":" + name;
      Assertions.assertFalse(cacheUtil.hasKey(key));
    }
  }

  @Test
  @Order(5)
  void getIdByStoreIdName() {
    Integer storeId = 1;
    String name = "店铺1的商品10";
    redisTemplate.delete(GOODS_STORE_ID_NAME_CACHE_PREFIX + "::" + storeId + ":" + name);
    Integer id = goodsService.getIdByStoreIdName(storeId, name);
    Assertions.assertNotNull(id);
    Assertions.assertEquals(10, id.intValue());
  }

  @Test
  @Order(6)
  void getPageByStoreId() {
    Integer storeId = 100;
    int size = 10;
    Page<Goods> pageReq = new Page<>(1, size);
    IPage<Goods> pageRes = goodsService.getPageByStoreId(storeId, pageReq, null);
    List<Goods> goodsList = pageRes.getRecords();
    // 验证返回的结果不为空，且数量正确
    Assertions.assertEquals(size, goodsList.size());
    // 验证返回的结果是按 id 降序排列的
    for (int i = 0; i < goodsList.size() - 1; i++) {
      Assertions.assertTrue(goodsList.get(i).getId() > goodsList.get(i + 1).getId());
    }

    // 验证返回的结果是按 name 升序排列的
    pageReq.orders().clear();
    pageReq.addOrder(OrderItem.asc("name"));
    pageRes = goodsService.getPageByStoreId(storeId, pageReq, null);
    goodsList = pageRes.getRecords();
    for (int i = 0; i < goodsList.size() - 1; i++) {
      Assertions.assertTrue(
          goodsList.get(i).getName().compareTo(goodsList.get(i + 1).getName()) <= 0);
    }

    // 验证返回的结果是按 stock 降序排列的
    pageReq.orders().clear();
    pageReq.addOrder(OrderItem.desc("stock"));
    pageRes = goodsService.getPageByStoreId(storeId, pageReq, null);
    goodsList = pageRes.getRecords();
    for (int i = 0; i < goodsList.size() - 1; i++) {
      Assertions.assertTrue(goodsList.get(i).getStock() >= goodsList.get(i + 1).getStock());
    }

    // 验证返回的结果是按 price 升序排列的
    pageReq.orders().clear();
    pageReq.addOrder(OrderItem.asc("price"));
    pageRes = goodsService.getPageByStoreId(storeId, pageReq, null);
    goodsList = pageRes.getRecords();
    for (int i = 0; i < goodsList.size() - 1; i++) {
      Assertions.assertTrue(
          goodsList.get(i).getPrice().compareTo(goodsList.get(i + 1).getPrice()) <= 0);
    }

    // 验证查询出来的商品名称都包含指定的字符串
    pageReq.orders().clear();
    pageReq.addOrder(OrderItem.desc("id"));
    pageReq.setCurrent(2);
    String name = "0";
    pageRes = goodsService.getPageByStoreId(storeId, pageReq, name);
    goodsList = pageRes.getRecords();
    for (Goods goods : goodsList) {
      Assertions.assertTrue(goods.getName().contains(name));
    }

    // 新增一个商品，再查一次，删除该商品，再查一次，以增加代码覆盖率
    Goods newGoods = new Goods();
    BeanUtil.copyProperties(goodsList.get(0), newGoods, "id");
    newGoods.setName(IdUtil.nanoId() + name);
    goodsService.add(newGoods);
    pageReq.orders().clear();
    pageReq.addOrder(OrderItem.desc("id"));
    pageReq.setCurrent(1);
    pageRes = goodsService.getPageByStoreId(storeId, pageReq, name);
    Optional<Goods> any =
        pageRes.getRecords().stream()
            .filter(goods -> goods.getId().intValue() == newGoods.getId())
            .findAny();
    Assertions.assertTrue(any.isPresent());
    goodsService.delete(newGoods.getId());
    pageRes = goodsService.getPageByStoreId(storeId, pageReq, name);
    any =
        pageRes.getRecords().stream()
            .filter(goods -> goods.getId().intValue() == newGoods.getId())
            .findAny();
    Assertions.assertFalse(any.isPresent());
  }

  /** 性能测试，无并发 */
  @Test
  @Order(7)
  void getPageByStoreIdPerformanceTest1() {
    flushDb();
    // 预热一下，让缓存中有数据
    Integer storeId = 100;
    goodsCache.getListByStoreId(storeId, goodsCache::getById);
    int size = 10;
    int times = 100;
    TimeInterval interval = new TimeInterval();
    int totalWithCache = 0;
    for (int i = 0; i <= times; i++) {
      IPage<Goods> pageRes = goodsService.getPageByStoreId(storeId, new Page<>(1, size), i + "");
      Assertions.assertFalse(pageRes.getRecords().isEmpty());
      totalWithCache += pageRes.getRecords().size();
    }
    long timeWithCache = interval.intervalMs();
    log.info("第 1 次查询耗时（有缓存逻辑，无并发）：{} ms", timeWithCache);

    interval = new TimeInterval();
    int totalWithoutCache = 0;
    for (int i = 0; i <= times; i++) {
      LambdaQueryWrapper<Goods> wrapper =
          new LambdaQueryWrapper<Goods>()
              .eq(Goods::getStoreId, storeId)
              .like(Goods::getName, i + "");
      IPage<Goods> pageRes = goodsMapper.selectPage(new Page<>(1, size), wrapper);
      Assertions.assertFalse(pageRes.getRecords().isEmpty());
      totalWithoutCache += pageRes.getRecords().size();
    }
    long timeWithoutCache = interval.intervalMs();
    log.info("第 2 次查询耗时（无缓存逻辑，无并发）：{} ms", timeWithoutCache);
    Assertions.assertEquals(totalWithCache, totalWithoutCache, "两次查询的总数量不一致");
    Assertions.assertTrue(timeWithCache < timeWithoutCache, "有缓存逻辑的查询耗时应该小于无缓存逻辑的查询耗时");
  }

  /** 性能测试，有并发 */
  @Test
  @Order(8)
  void getPageByStoreIdPerformanceTest2() {
    flushDb();
    Integer storeId = 100;
    // 预热一下，让缓存中有数据
    goodsCache.getListByStoreId(storeId, goodsCache::getById);

    int size = 10;
    int times = 100;
    TimeInterval interval = new TimeInterval();
    AtomicInteger totalWithCache = new AtomicInteger(0);
    IntStream.range(0, times + 1)
        .parallel()
        .forEach(
            i -> {
              IPage<Goods> pageRes =
                  goodsService.getPageByStoreId(storeId, new Page<>(1, size), i + "");
              Assertions.assertFalse(pageRes.getRecords().isEmpty());
              totalWithCache.addAndGet(pageRes.getRecords().size());
            });
    long timeWithCache = interval.intervalMs();
    log.info("第 3 次查询耗时（有缓存逻辑，有并发）：{} ms", timeWithCache);

    interval = new TimeInterval();
    AtomicInteger totalWithoutCache = new AtomicInteger(0);
    IntStream.range(0, times + 1)
        .parallel()
        .forEach(
            i -> {
              LambdaQueryWrapper<Goods> wrapper =
                  new LambdaQueryWrapper<Goods>()
                      .eq(Goods::getStoreId, storeId)
                      .like(Goods::getName, i + "");
              IPage<Goods> pageRes = goodsMapper.selectPage(new Page<>(1, size), wrapper);
              totalWithoutCache.addAndGet(pageRes.getRecords().size());
            });
    long timeWithoutCache = interval.intervalMs();
    log.info("第 4 次查询耗时（无缓存逻辑，有并发）：{} ms", timeWithoutCache);
    Assertions.assertEquals(totalWithCache.get(), totalWithoutCache.get(), "两次查询的总数量不一致");
    Assertions.assertTrue(timeWithCache < timeWithoutCache, "有缓存逻辑的查询耗时应该小于无缓存逻辑的查询耗时");
  }

  private void flushDb() {
    RedisConnection connection =
        Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection();
    RedisServerCommands redisServerCommands = connection.serverCommands();
    redisServerCommands.flushDb(RedisServerCommands.FlushOption.SYNC);
  }
}
