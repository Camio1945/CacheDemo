package cache.demo.service;

import cache.demo.entity.Goods;
import lombok.NonNull;

/**
 * 商品服务接口
 *
 * @author Camio1945
 */
public interface IGoodsService {

  /**
   * 根据 id 获取商品信息（同步版本）
   *
   * @param id 商品 id
   * @return 商品信息
   * @deprecated 性能不好，请使用 {@link #getById(Integer)} 方法
   */
  @Deprecated(since = "v3")
  Goods getByIdWithSync(@NonNull Integer id);

  /**
   * 根据 id 获取商品信息（单飞版本）
   *
   * @param id 商品 id
   * @return 商品信息
   */
  Goods getById(@NonNull Integer id);

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
}
