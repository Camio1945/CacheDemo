package cache.demo.cache;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.NumberUtil;
import java.util.Objects;
import java.util.Properties;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 缓存工具类
 *
 * @author Camio1945
 */
@Service
@AllArgsConstructor
public class CacheUtil {
  private final StringRedisTemplate stringRedisTemplate;

  public double getHitRatioPercentage() {
    RedisConnection connection =
        Objects.requireNonNull(stringRedisTemplate.getConnectionFactory()).getConnection();
    RedisServerCommands redisServerCommands = connection.serverCommands();
    Properties info = redisServerCommands.info();
    assert info != null;
    long keyspaceHits = Convert.toLong(info.getProperty("keyspace_hits"));
    long keyspaceMisses = Convert.toLong(info.getProperty("keyspace_misses"));
    long total = keyspaceHits + keyspaceMisses;
    if (total <= 0) {
      return -1;
    }
    return NumberUtil.div(keyspaceHits * (float) 100, total, 4);
  }

  /**
   * 判断 key 是否存在
   *
   * @param key 键
   * @return 是否存在
   */
  public boolean hasKey(@NonNull String key) {
    Boolean hasKey = stringRedisTemplate.hasKey(key);
    return hasKey != null && hasKey;
  }
}
