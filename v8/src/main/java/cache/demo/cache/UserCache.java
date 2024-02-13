package cache.demo.cache;

import cache.demo.entity.User;
import cache.demo.mapper.UserMapper;
import cache.demo.util.SingleFlightUtil;
import cn.hutool.core.lang.Assert;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 用户缓存
 *
 * @author Camio1945
 */
@Service
@AllArgsConstructor
public class UserCache {
  public static final String USER_ID_CACHE_PREFIX = "cache:user:id";
  public static final String USER_ACCOUNT_CACHE_PREFIX = "cache:user:account";

  /** 允许多少个空值缓存 */
  private static final int ALLOW_NULL_CACHE_NUMBER = 1000;

  private static int maxAllowedId = Integer.MAX_VALUE;

  private UserMapper userMapper;
  private RedisTemplate<String, Object> redisTemplate;

  /**
   * 根据 id 获取用户
   *
   * @param id 用户 id
   * @return 用户
   */
  @Cacheable(value = USER_ID_CACHE_PREFIX, key = "#id")
  public User getById(@NonNull Integer id) {
    Assert.isTrue(id <= maxAllowedId, "非法 id");
    String key = USER_ID_CACHE_PREFIX + "::" + id;
    return SingleFlightUtil.execute(key, () -> userMapper.selectById(id));
  }

  /**
   * 根据账号获取用户 id
   *
   * @param account 账号
   * @return 用户 id
   */
  @Cacheable(value = USER_ACCOUNT_CACHE_PREFIX, key = "#account")
  public Integer getIdByAccount(@NonNull String account) {
    String key = USER_ACCOUNT_CACHE_PREFIX + "::" + account;
    return SingleFlightUtil.execute(key, () -> userMapper.selectIdByAccount(account));
  }

  /**
   * 新增操作后的缓存处理
   *
   * @param after 新增的用户
   */
  public void handleCacheAfterAdd(User after) {
    List<String> keys =
        List.of(
            USER_ID_CACHE_PREFIX + "::" + after.getId(),
            USER_ACCOUNT_CACHE_PREFIX + "::" + after.getAccount());
    redisTemplate.delete(keys);
  }

  /**
   * 删除操作后的缓存处理
   *
   * @param before 删除前的用户
   */
  public void handleCacheAfterDelete(User before) {
    List<String> keys =
        List.of(
            USER_ID_CACHE_PREFIX + "::" + before.getId(),
            USER_ACCOUNT_CACHE_PREFIX + "::" + before.getAccount());
    redisTemplate.delete(keys);
  }

  /**
   * 更新操作后的缓存处理
   *
   * @param before 更新前的用户
   * @param after 更新后的用户
   */
  public void handleCacheAfterUpdate(User before, User after) {
    List<String> keys =
        List.of(
            USER_ID_CACHE_PREFIX + "::" + after.getId(),
            USER_ACCOUNT_CACHE_PREFIX + "::" + before.getAccount(),
            USER_ACCOUNT_CACHE_PREFIX + "::" + after.getAccount());
    redisTemplate.delete(keys);
  }

  /**
   * 更新 {@link #maxAllowedId} 的值
   *
   * @param id 用户 id
   */
  public static void updateMaxAllowedIdByUserId(int id) {
    maxAllowedId = id + ALLOW_NULL_CACHE_NUMBER;
  }
}
