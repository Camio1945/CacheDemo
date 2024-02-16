package cache.demo.cache;

import cache.demo.entity.Weibo;
import cache.demo.mapper.WeiboMapper;
import cache.demo.util.SingleFlightUtil;
import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.time.Duration;
import java.util.*;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

/**
 * 微博缓存
 *
 * @author Camio1945
 */
@Service
@AllArgsConstructor
public class WeiboCache {
  public static final String WEIBO_ID_CACHE_PREFIX = "cache:weibo:id";
  public static final String WEIBO_IDS_BY_USER_ID_CACHE_PREFIX = "cache:weibo:weiboIdsByUserId";

  /** 每个用户缓存多少条微博 */
  public static final int CACHE_WEIBO_SIZE_EACH_USER = 50;

  /** 允许多少个空值缓存 */
  private static final int ALLOW_NULL_CACHE_NUMBER = 1000;

  private static int maxAllowedId = Integer.MAX_VALUE;

  private WeiboMapper weiboMapper;
  private RedisTemplate<String, Object> redisTemplate;
  private RedisCacheConfiguration cacheConfiguration;

  /**
   * 根据 id 获取微博
   *
   * @param id 微博 id
   * @return 微博
   */
  @Cacheable(value = WEIBO_ID_CACHE_PREFIX, key = "#id")
  public Weibo getById(@NonNull Integer id) {
    Assert.isTrue(id <= maxAllowedId, "非法 id");
    String key = WEIBO_ID_CACHE_PREFIX + "::" + id;
    return SingleFlightUtil.execute(key, () -> weiboMapper.selectById(id));
  }

  /**
   * 根据用户 id 获取微博列表（指定分页）
   *
   * @param userId 用户 id
   * @param pageReq 分页参数
   * @param function 根据 id 获取微博的函数，请传递：weiboCache::getById <br>
   *     注：这个参数只能从外面传过来，如果直接从里面调用的话，不会走缓存
   * @return 微博列表
   */
  public List<Weibo> getLatestWeiboListByUserId(
      @NonNull Integer userId, IPage<Weibo> pageReq, @NonNull IntFunction<Weibo> function) {
    int fromIndex = (int) ((pageReq.getCurrent() - 1) * pageReq.getSize());
    List<Integer> weiboIds =
        getWeiboIdsByUserId(userId).stream().skip(fromIndex).limit(pageReq.getSize()).toList();
    List<String> keys = weiboIds.stream().map(id -> WEIBO_ID_CACHE_PREFIX + "::" + id).toList();
    List<Object> objects = redisTemplate.opsForValue().multiGet(keys);
    assert objects != null;
    List<Weibo> weiboList = new ArrayList<>();
    for (int i = 0; i < objects.size(); i++) {
      Object obj = objects.get(i);
      if (obj == null) {
        Weibo weibo = function.apply(weiboIds.get(i));
        weiboList.add(weibo);
      } else {
        weiboList.add((Weibo) obj);
      }
    }
    return weiboList;
  }

  /**
   * 根据用户 id 获取微博 id 集合（倒序排列） <br>
   * 注：不要用 {@Cacheable} 注解，因为需要用到 redis 中的 zset，而不是普通的 string
   *
   * @param userId 用户 id
   * @return 微博 id
   */
  private List<Integer> getWeiboIdsByUserId(@NonNull Integer userId) {
    String key = WEIBO_IDS_BY_USER_ID_CACHE_PREFIX + "::" + userId;
    return SingleFlightUtil.execute(
        key,
        () -> {
          ZSetOperations<String, Object> zSetOperations = redisTemplate.opsForZSet();
          Set<Object> values = zSetOperations.reverseRange(key, 0, -1);
          if (values != null && !values.isEmpty()) {
            return values.stream().map(Integer.class::cast).toList();
          }
          List<Integer> weiboIds =
              weiboMapper.selectLatestIdsByUserId(userId, CACHE_WEIBO_SIZE_EACH_USER);
          if (weiboIds != null && !weiboIds.isEmpty()) {
            Set<ZSetOperations.TypedTuple<Object>> set =
                weiboIds.stream()
                    .map(id -> ZSetOperations.TypedTuple.of((Object) id, id.doubleValue()))
                    .collect(Collectors.toSet());
            zSetOperations.add(key, set);
            Duration timeToLive = cacheConfiguration.getTtlFunction().getTimeToLive(key, set);
            redisTemplate.expire(key, timeToLive);
          }
          return weiboIds;
        });
  }

  /**
   * 新增操作后的缓存处理
   *
   * @param after 新增的微博
   */
  public void handleCacheAfterAdd(Weibo after) {
    redisTemplate.delete(WEIBO_ID_CACHE_PREFIX + "::" + after.getId());
    ZSetOperations<String, Object> zSetOperations = redisTemplate.opsForZSet();
    String key = WEIBO_IDS_BY_USER_ID_CACHE_PREFIX + "::" + after.getUserId();
    Long size = zSetOperations.size(key);
    // 如果缓存中的条数为 0 ，说明很有可能还没有查询过，这个时候触发一次查询
    if (size == null || size == 0) {
      getWeiboIdsByUserId(after.getUserId());
    }
    // 新微博加到缓存中，并设置缓存过期时间
    zSetOperations.add(key, after.getId(), after.getId().doubleValue());
    Duration timeToLive = cacheConfiguration.getTtlFunction().getTimeToLive(key, after.getId());
    redisTemplate.expire(key, timeToLive);
    // 如果缓存中的微博数量超过指定条数，那么删除最旧的微博
    if (size != null && size >= CACHE_WEIBO_SIZE_EACH_USER) {
      zSetOperations.popMin(key, (size + 1) - CACHE_WEIBO_SIZE_EACH_USER);
    }
  }

  /**
   * 删除操作后的缓存处理
   *
   * @param before 删除前的微博
   */
  public void handleCacheAfterDelete(Weibo before) {
    List<String> keys =
        List.of(
            WEIBO_ID_CACHE_PREFIX + "::" + before.getId(),
            WEIBO_IDS_BY_USER_ID_CACHE_PREFIX + "::" + before.getUserId());
    redisTemplate.delete(keys);
  }

  /**
   * 更新操作后的缓存处理
   *
   * @param before 更新前的微博
   * @param after 更新后的微博
   */
  public void handleCacheAfterUpdate(Weibo before, Weibo after) {
    List<String> keys = List.of(WEIBO_ID_CACHE_PREFIX + "::" + after.getId());
    redisTemplate.delete(keys);
  }

  /**
   * 更新 {@link #maxAllowedId} 的值
   *
   * @param id 微博 id
   */
  public static void updateMaxAllowedIdByWeiboId(int id) {
    maxAllowedId = id + ALLOW_NULL_CACHE_NUMBER;
  }
}
