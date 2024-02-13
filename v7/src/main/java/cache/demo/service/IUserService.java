package cache.demo.service;

import cache.demo.entity.User;
import lombok.NonNull;

/**
 * 用户服务接口
 *
 * @author Camio1945
 */
public interface IUserService {

  /**
   * 根据 id 获取用户信息
   *
   * @param id 用户 id
   * @return 用户信息
   */
  User getById(@NonNull Integer id);

  /**
   * 根据账号获取用户 id
   *
   * @param account 账号
   * @return 用户 id
   */
  Integer getIdByAccount(@NonNull String account);

  /**
   * 新增用户
   *
   * @param user 用户信息
   * @return true 表示新增成功，false 表示新增失败
   */
  boolean add(@NonNull User user);

  /**
   * 更新用户信息
   *
   * @param user 用户信息
   * @return true 表示更新成功，false 表示更新失败
   */
  boolean update(@NonNull User user);

  /**
   * 根据 id 删除用户信息
   *
   * @param id 用户 id
   * @return true 表示删除成功，false 表示删除失败
   */
  boolean delete(@NonNull Integer id);

  /** 更新最大允许的 id */
  void updateMaxAllowedId();
}
