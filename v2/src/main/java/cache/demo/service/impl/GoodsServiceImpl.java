package cache.demo.service.impl;

import cache.demo.entity.Goods;
import cache.demo.mapper.GoodsMapper;
import cache.demo.service.IGoodsService;
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

  @Override
  @Cacheable(value = GOODS_ID_CACHE_PREFIX, key = "#id")
  public Goods getById(@NonNull Integer id) {
    return goodsMapper.selectById(id);
  }

  @Override
  @CacheEvict(value = GOODS_ID_CACHE_PREFIX, key = "#goods.id")
  public boolean add(@NonNull Goods goods) {
    return goodsMapper.insert(goods) > 0;
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
}
