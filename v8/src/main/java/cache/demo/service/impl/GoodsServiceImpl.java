package cache.demo.service.impl;

import cache.demo.cache.GoodsCache;
import cache.demo.entity.Goods;
import cache.demo.mapper.GoodsMapper;
import cache.demo.service.IGoodsService;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.*;
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
  public IPage<Goods> getPageByStoreId(
      @NonNull Integer storeId, @NonNull Page<Goods> pageReq, String name) {
    IPage<Goods> pageRes = new Page<>(pageReq.getCurrent(), pageReq.getSize(), 0);
    List<Goods> fullList = goodsCache.getListByStoreId(storeId, goodsCache::getById);
    if (CollUtil.isEmpty(fullList)) {
      return pageRes;
    }
    int fromIndex = (int) ((pageReq.getCurrent() - 1) * pageReq.getSize());
    int toIndex = (int) (pageReq.getCurrent() * pageReq.getSize());
    Assert.isTrue(fromIndex >= 0 && toIndex >= 0, "非法分页参数");
    boolean isNameBlank = CharSequenceUtil.isBlank(name);
    List<Goods> filteredList = new ArrayList<>();
    fullList.stream()
        .filter(goods -> isNameBlank || goods.getName().contains(name))
        .forEach(filteredList::add);
    sort(pageReq, filteredList);
    int total = filteredList.size();
    pageRes.setTotal(total);
    if (fromIndex < total) {
      toIndex = Math.min(toIndex, total);
      pageRes.setRecords(filteredList.subList(fromIndex, toIndex));
    }
    return pageRes;
  }

  private static void sort(IPage<Goods> pageReq, List<Goods> list) {
    List<OrderItem> orders = pageReq.orders();
    if (CollUtil.isEmpty(orders)) {
      orders.add(OrderItem.desc("id"));
    }
    Comparator<Goods> goodsComparator =
        (goods1, goods2) -> {
          for (OrderItem order : orders) {
            int compareRes = 0;
            switch (order.getColumn()) {
              case "id":
                compareRes = goods1.getId().compareTo(goods2.getId());
                break;
              case "name":
                compareRes = goods1.getName().compareTo(goods2.getName());
                break;
              case "stock":
                compareRes = Integer.compare(goods1.getStock(), goods2.getStock());
                break;
              case "price":
                compareRes = goods1.getPrice().compareTo(goods2.getPrice());
                break;
              default:
                break;
            }
            if (compareRes != 0) {
              return order.isAsc() ? compareRes : -compareRes;
            }
          }
          return 0;
        };
    list.sort(goodsComparator);
  }

  @Override
  public Goods getByStoreIdName(@NonNull Integer storeId, @NonNull String name) {
    return goodsCache.getById(goodsCache.getIdByStoreIdName(storeId, name));
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
