package cache.demo.service;

import cache.demo.entity.Goods;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.NonNull;

/**
 * 商品服务接口
 *
 * @author Camio1945
 */
public interface IGoodsService {

  /**
   * 根据 id 获取商品信息（单飞版本）
   *
   * @param id 商品 id
   * @return 商品信息
   */
  Goods getById(@NonNull Integer id);

  /**
   * 根据店铺 id 获取商品信息（分页）
   *
   * @param storeId 店铺 id
   * @param pageReq 分页信息
   * @param name 商品名称
   * @return 商品信息
   */
  IPage<Goods> getPageByStoreId(@NonNull Integer storeId, @NonNull Page<Goods> pageReq, String name);

  /**
   * 根据店铺 id 和商品名称获取商品
   *
   * @param storeId 店铺 id
   * @param name 商品名称
   * @return 商品
   */
   Goods getByStoreIdName(@NonNull Integer storeId, @NonNull String name);

  /**
   * 新增商品
   *
   * @param goods 商品信息
   * @return true 表示新增成功，false 表示新增失败
   */
  boolean add(@NonNull Goods goods);

  /**
   * 更新商品信息
   *
   * @param goods 商品信息
   * @return true 表示更新成功，false 表示更新失败
   */
  boolean update(@NonNull Goods goods);

  /**
   * 根据 id 删除商品信息
   *
   * @param id 商品 id
   * @return true 表示删除成功，false 表示删除失败
   */
  boolean delete(@NonNull Integer id);

  /** 更新最大允许的 id */
  void updateMaxAllowedId();
}
