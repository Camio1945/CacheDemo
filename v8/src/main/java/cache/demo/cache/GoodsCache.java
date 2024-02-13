package cache.demo.cache;

import cache.demo.entity.Goods;
import cache.demo.mapper.GoodsMapper;
import cache.demo.util.SingleFlightUtil;
import cn.hutool.core.lang.Assert;
import java.util.*;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Service;

/**
 * 商品缓存
 *
 * @author Camio1945
 */
@Service
@AllArgsConstructor
public class GoodsCache {
  public static final String GOODS_ID_CACHE_PREFIX = "cache:goods:id";

  public static final String GOODS_STORE_ID_NAME_CACHE_PREFIX = "cache:goods:storeIdName";

  public static final String GOODS_IDS_BY_STORE_ID_CACHE_PREFIX = "cache:goods:goodsIdsByStoreId";

  /** 允许多少个空值缓存 */
  private static final int ALLOW_NULL_CACHE_NUMBER = 1000;

  private static int maxAllowedId = Integer.MAX_VALUE;

  private GoodsMapper goodsMapper;

  private RedisTemplate<String, Object> redisTemplate;

  /**
   * 更新 {@link #maxAllowedId} 的值
   *
   * <pre>
   * 代码中加上 ALLOW_NULL_CACHE_NUMBER 是为了应对集群的情况，如果不加上 ALLOW_NULL_CACHE_NUMBER，
   * 假设数据库中当前最大的 id 是 100000，
   * 张三调用了集群 1 的 add 方法，新增了一个商品，id 是 100001，JVM 1 中的 maxAllowedId 会更新为 100001，
   * 李四调用了集群 2 的 add 方法，新增了一个商品，id 是 100002，JVM 2 中的 maxAllowedId 会更新为 100002，
   * 此时王五调用了集群 1 的 getById 方法，传入的 id 是 100002，而 JVM 1 中的 maxAllowedId 是 100001，就会出错
   * </pre>
   *
   * @param id 商品 id
   */
  public static void updateMaxAllowedIdByGoodsId(int id) {
    maxAllowedId = id + ALLOW_NULL_CACHE_NUMBER;
  }

  /**
   * 根据 id 获取商品
   *
   * @param id 商品 id
   * @return 商品
   */
  @Cacheable(value = GOODS_ID_CACHE_PREFIX, key = "#id")
  public Goods getById(@NonNull Integer id) {
    Assert.isTrue(id <= maxAllowedId, "非法 id");
    String key = GOODS_ID_CACHE_PREFIX + "::" + id;
    return SingleFlightUtil.execute(key, () -> goodsMapper.selectById(id));
  }

  /**
   * 根据店铺 id 获取商品集合（按 id 倒序排列） <br>
   * 注：不要用 {@Cacheable} 注解，因为需要用到 redis 中的 zset，而不是普通的 string
   *
   * @param storeId 店铺 id
   * @param function 生成商品的函数，请传递：goodsCache::getById <br>
   *     注：这个参数只能从外面传过来，如果直接从里面调用的话，不会走缓存
   * @return 商品集合
   */
  public List<Goods> getListByStoreId(
      @NonNull Integer storeId, @NonNull IntFunction<Goods> function) {
    List<Integer> goodsIds = getGoodsIdsByStoreId(storeId);
    List<String> keys = goodsIds.stream().map(id -> GOODS_ID_CACHE_PREFIX + "::" + id).toList();
    List<Object> objects = redisTemplate.opsForValue().multiGet(keys);
    assert objects != null;
    List<Goods> goodsList = new ArrayList<>();
    for (int i = 0; i < objects.size(); i++) {
      Object obj = objects.get(i);
      if (obj == null) {
        Goods goods = function.apply(goodsIds.get(i));
        goodsList.add(goods);
      } else {
        goodsList.add((Goods) obj);
      }
    }
    return goodsList;
  }

  /**
   * 根据店铺 id 获取商品 id 集合（倒序排列） <br>
   * 注：不要用 {@Cacheable} 注解，因为需要用到 redis 中的 zset，而不是普通的 string
   *
   * @param storeId 店铺 id
   * @return 商品 id
   */
  private List<Integer> getGoodsIdsByStoreId(@NonNull Integer storeId) {
    String key = GOODS_IDS_BY_STORE_ID_CACHE_PREFIX + "::" + storeId;
    return SingleFlightUtil.execute(
        key,
        () -> {
          ZSetOperations<String, Object> zSetOperations = redisTemplate.opsForZSet();
          Set<Object> values = zSetOperations.reverseRange(key, 0, -1);
          if (values != null && !values.isEmpty()) {
            return values.stream().map(Integer.class::cast).toList();
          }
          List<Integer> goodsIds = goodsMapper.selectIdsByStoreId(storeId);
          if (goodsIds != null && !goodsIds.isEmpty()) {
            Set<ZSetOperations.TypedTuple<Object>> set =
                goodsIds.stream()
                    .map(id -> ZSetOperations.TypedTuple.of((Object) id, id.doubleValue()))
                    .collect(Collectors.toSet());
            zSetOperations.add(key, set);
          }
          return goodsIds;
        });
  }

  /**
   * 根据店铺 id 和商品名称获取商品 id
   *
   * @param storeId 店铺 id
   * @param name 商品名称
   * @return 商品 id
   */
  @Cacheable(value = GOODS_STORE_ID_NAME_CACHE_PREFIX, key = "#storeId + ':' + #name")
  public Integer getIdByStoreIdName(@NonNull Integer storeId, @NonNull String name) {
    String key = GOODS_STORE_ID_NAME_CACHE_PREFIX + "::" + storeId + ":" + name;
    return SingleFlightUtil.execute(key, () -> goodsMapper.selectIdByStoreIdName(storeId, name));
  }

  /**
   * 新增操作后的缓存处理
   *
   * @param after 新增的商品
   */
  public void handleCacheAfterAdd(Goods after) {
    Integer id = after.getId();
    List<String> keys =
        List.of(
            GOODS_ID_CACHE_PREFIX + "::" + id,
            GOODS_IDS_BY_STORE_ID_CACHE_PREFIX + "::" + after.getStoreId(),
            GOODS_STORE_ID_NAME_CACHE_PREFIX + "::" + after.getStoreId() + ":" + after.getName());
    redisTemplate.delete(keys);
  }

  /**
   * 删除操作后的缓存处理
   *
   * @param before 删除前的商品
   */
  public void handleCacheAfterDelete(Goods before) {
    List<String> keys =
        List.of(
            GOODS_ID_CACHE_PREFIX + "::" + before.getId(),
            GOODS_IDS_BY_STORE_ID_CACHE_PREFIX + "::" + before.getStoreId(),
            GOODS_STORE_ID_NAME_CACHE_PREFIX + "::" + before.getStoreId() + ":" + before.getName());
    redisTemplate.delete(keys);
  }

  /**
   * 更新操作后的缓存处理
   *
   * @param before 更新前的商品
   * @param after 更新后的商品
   */
  public void handleCacheAfterUpdate(Goods before, Goods after) {
    List<String> keys =
        List.of(
            GOODS_ID_CACHE_PREFIX + "::" + after.getId(),
            GOODS_IDS_BY_STORE_ID_CACHE_PREFIX + "::" + before.getStoreId(),
            GOODS_IDS_BY_STORE_ID_CACHE_PREFIX + "::" + after.getStoreId(),
            GOODS_STORE_ID_NAME_CACHE_PREFIX + "::" + before.getStoreId() + ":" + before.getName(),
            GOODS_STORE_ID_NAME_CACHE_PREFIX + "::" + after.getStoreId() + ":" + after.getName());
    redisTemplate.delete(keys);
  }
}
