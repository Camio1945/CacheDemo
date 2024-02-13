package cache.demo.service.impl;

import cache.demo.entity.Goods;
import cache.demo.mapper.GoodsMapper;
import cache.demo.service.IGoodsService;
import cache.demo.util.SingleFlightUtil;
import cn.hutool.core.lang.Assert;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 商品服务实现类
 *
 * @author Camio1945
 */
@Service
@AllArgsConstructor
public class GoodsServiceImpl implements IGoodsService {
  private GoodsMapper goodsMapper;
  public static final String GOODS_ID_CACHE_PREFIX = "cache:goods:id";
  private static int maxAllowedId = Integer.MAX_VALUE;

  /** 允许多少个空值缓存 */
  private static final int ALLOW_NULL_CACHE_NUMBER = 1000;

  @Override
  @Cacheable(value = GOODS_ID_CACHE_PREFIX, key = "#id")
  public Goods getById(@NonNull Integer id) {
    Assert.isTrue(id <= maxAllowedId, "非法 id");
    String key = GOODS_ID_CACHE_PREFIX + "::" + id;
    return SingleFlightUtil.execute(key, () -> goodsMapper.selectById(id));
  }

  @Override
  @CacheEvict(value = GOODS_ID_CACHE_PREFIX, key = "#goods.id")
  public boolean add(@NonNull Goods goods) {
    boolean success = goodsMapper.insert(goods) > 0;
    if (success) {
      updateMaxAllowedIdByGoodsId(goods.getId());
    }
    return success;
  }

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
  private static void updateMaxAllowedIdByGoodsId(int id) {
    maxAllowedId = id + ALLOW_NULL_CACHE_NUMBER;
  }

  @Override
  @CacheEvict(value = GOODS_ID_CACHE_PREFIX, key = "#goods.id")
  public boolean update(@NonNull Goods goods) {
    return goodsMapper.updateById(goods) > 0;
  }

  @Override
  @CacheEvict(value = GOODS_ID_CACHE_PREFIX, key = "#id")
  public boolean delete(@NonNull Integer id) {
    return goodsMapper.deleteById(id) > 0;
  }

  @Override
  public void updateMaxAllowedId() {
    updateMaxAllowedIdByGoodsId(goodsMapper.selectMaxId());
  }
}
