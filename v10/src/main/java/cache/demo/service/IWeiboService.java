package cache.demo.service;

import cache.demo.entity.Weibo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.NonNull;

/**
 * 微博服务接口
 *
 * @author Camio1945
 */
public interface IWeiboService {

  /**
   * 根据 id 获取微博信息
   *
   * @param id 微博 id
   * @return 微博信息
   */
  Weibo getById(@NonNull Integer id);

  /**
   * 根据用户 id 获取最新的微博信息（分页）
   *
   * @param userId 用户 id
   * @param pageReq 分页信息<br>
   *                其中的排序信息会忽略，因为这个方法的目的就是查最新的
   *                其中的 searchCount 会忽略并会被强制设置为 false ，微博场景下一般不查询总数
   * @return 微博信息
   */
  IPage<Weibo> getLatestPageByUserId(@NonNull Integer userId, Page<Weibo> pageReq);

  /**
   * 新增微博
   *
   * @param weibo 微博信息
   * @return true 表示新增成功，false 表示新增失败
   */
  boolean add(@NonNull Weibo weibo);

  /**
   * 更新微博信息
   *
   * @param weibo 微博信息
   * @return true 表示更新成功，false 表示更新失败
   */
  boolean update(@NonNull Weibo weibo);

  /**
   * 更新微博信息（已废弃，仅用于演示）
   *
   * @param weibo 微博信息
   * @return true 表示更新成功，false 表示更新失败
   */
  @Deprecated(since = "v10", forRemoval = true)
  boolean updateDeprecated(@NonNull Weibo weibo);

  /**
   * 根据 id 删除微博信息
   *
   * @param id 微博 id
   * @return true 表示删除成功，false 表示删除失败
   */
  boolean delete(@NonNull Integer id);

  /** 更新最大允许的 id */
  void updateMaxAllowedId();
}
