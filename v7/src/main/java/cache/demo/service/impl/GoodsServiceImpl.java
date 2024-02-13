package cache.demo.service.impl;

import cache.demo.cache.GoodsCache;
import cache.demo.entity.Goods;
import cache.demo.mapper.GoodsMapper;
import cache.demo.service.IGoodsService;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.stereotype.Service;

/**
 * 商品服务实现类
 *
 * @author Camio1945
 */
@Service
@AllArgsConstructor
public class GoodsServiceImpl implements IGoodsService {
  private GoodsCache goodsCache;
  private GoodsMapper goodsMapper;

  @Override
  public Goods getById(@NonNull Integer id) {
    return goodsCache.getById(id);
  }

  @Override
  public Integer getIdByStoreIdName(@NonNull Integer storeId, @NonNull String name) {
    return goodsCache.getIdByStoreIdName(storeId, name);
  }

  @Override
  public boolean add(@NonNull Goods goods) {
    boolean success = goodsMapper.insert(goods) > 0;
    if (success) {
      GoodsCache.updateMaxAllowedIdByGoodsId(goods.getId());
      goodsCache.handleCacheAfterAdd(goods);
    }
    return success;
  }

  @Override
  public boolean update(@NonNull Goods goods) {
    Goods before = getById(goods.getId());
    boolean updateRes = goodsMapper.updateById(goods) > 0;
    if (updateRes) {
      goodsCache.handleCacheAfterUpdate(before, goods);
    }
    return updateRes;
  }

  @Override
  public boolean delete(@NonNull Integer id) {
    Goods goods = getById(id);
    boolean deleteRes = goodsMapper.deleteById(id) > 0;
    if (deleteRes) {
      goodsCache.handleCacheAfterDelete(goods);
    }
    return deleteRes;
  }

  @Override
  public void updateMaxAllowedId() {
    GoodsCache.updateMaxAllowedIdByGoodsId(goodsMapper.selectMaxId());
  }
}
