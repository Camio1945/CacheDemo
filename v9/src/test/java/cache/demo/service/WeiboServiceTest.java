package cache.demo.service;

import static cache.demo.cache.WeiboCache.*;

import cache.demo.cache.CacheUtil;
import cache.demo.entity.User;
import cache.demo.entity.Weibo;
import cache.demo.mapper.WeiboMapper;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import common.WithSpringBootTestAnnotation;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * 微博服务测试类
 *
 * @author Camio1945
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WeiboServiceTest extends WithSpringBootTestAnnotation {
  private static final int MIN_ID = 1;
  private static final int MAX_ID = 1000;
  private static final String ID_KEY_PREFIX = WEIBO_ID_CACHE_PREFIX + "::";

  @Autowired IWeiboService weiboService;
  @Autowired IUserService userService;
  @Autowired CacheUtil cacheUtil;

  /** 引入 Mapper 是为了直接查数据库，不经过缓存 */
  @Autowired WeiboMapper weiboMapper;

  @Autowired RedisTemplate<String, Object> redisTemplate;

  @BeforeEach
  void beforeEach() {
    // 删除一些缓存，并验证删除成功
    int[] ids = {MIN_ID, MAX_ID + 1};
    for (int id : ids) {
      String key = ID_KEY_PREFIX + id;
      redisTemplate.delete(key);
      Boolean hasKey = redisTemplate.hasKey(key);
      Assertions.assertTrue(hasKey != null && !hasKey);
    }
  }

  @Test
  @Order(1)
  void getById() {
    // 测试参数为空的情况
    Assertions.assertThrows(RuntimeException.class, () -> weiboService.getById(null));

    // 测试正常情况，这里查的是数据库，并且会把查到的数据放入缓存
    Weibo weibo = weiboService.getById(MIN_ID);
    Assertions.assertNotNull(weibo);
    Assertions.assertEquals(MIN_ID, weibo.getId());

    // 确认缓存中有值，并且就是我们存进去的值
    Weibo cacheValue = getWeiboFromCache(ID_KEY_PREFIX + MIN_ID);
    Assertions.assertNotNull(cacheValue);
    Assertions.assertEquals(MIN_ID, cacheValue.getId().intValue());

    // 测试查询数据库中不存在的数据，如果 id 只稍微大一点点，则会查到空值，并且会把空值放入缓存
    weiboMapper.deleteById(MAX_ID + 1);
    int notExistsId = MAX_ID + 1;
    Weibo notExistsWeibo = weiboService.getById(notExistsId);
    Assertions.assertNull(notExistsWeibo);
    Boolean hasKey = redisTemplate.hasKey(ID_KEY_PREFIX + notExistsId);
    Assertions.assertTrue(hasKey != null && hasKey);

    // 测试查询数据库中不存在的数据，如果 id 太大，则会直接抛异常，而不会放入缓存
    int bigId = Integer.MAX_VALUE;
    Assertions.assertThrows(RuntimeException.class, () -> weiboService.getById(bigId));
    String notExistsKey = ID_KEY_PREFIX + bigId;
    cacheValue = getWeiboFromCache(notExistsKey);
    Assertions.assertNull(cacheValue);
  }

  private Weibo getWeiboFromCache(String key) {
    try {
      return (Weibo) redisTemplate.opsForValue().get(key);
    } catch (RuntimeException e) {
      if (e instanceof SerializationException) {
        String message = e.getMessage();
        if (message != null && message.contains("Could not read JSON")) {
          return null;
        }
      }
      throw e;
    }
  }

  @Test
  @Order(2)
  void add() {
    // 测试参数为空的情况
    Assertions.assertThrows(RuntimeException.class, () -> weiboService.add(null));

    // 先从数据库中删除指定的数据，以方便接下来添加，以免主键冲突
    int newId = MAX_ID + 1;
    weiboService.delete(newId);

    // 特意缓存空值，确认 redis 缓存中存在这个键
    weiboService.getById(newId);
    Boolean hasKey = redisTemplate.hasKey(ID_KEY_PREFIX + newId);
    Assertions.assertTrue(hasKey != null && hasKey);

    // 测试添加微博到数据库中
    Weibo weibo = new Weibo();
    // 为了测试方便，这里指定了 id ，但数据库中的 id 是自增的，实际开发时不需要也不应该指定 id，
    // 强行指定 id 时，如果该 id 在数据库中已经存在了，而并不会新增记录，而会修改记录，从业务上讲就是 Bug 了。
    weibo.setId(newId);
    weibo.setUserId(1);
    weibo.setContent("微博内容" + newId);
    boolean isSuccess = weiboService.add(weibo);
    Assertions.assertTrue(isSuccess);

    // 验证添加成功后会从缓存中删除数据
    Assertions.assertFalse(cacheUtil.hasKey(ID_KEY_PREFIX + newId));

    // 新增之后，验证查询用户最新的微博数量是正确的
    IPage<Weibo> latestPageByUserId = weiboService.getLatestPageByUserId(1, new Page<>(1, 10));
    List<Weibo> records = latestPageByUserId.getRecords();
    Assertions.assertEquals(10, records.size());
  }

  @Test
  @Order(3)
  void update() {
    // 测试参数为空的情况
    Assertions.assertThrows(RuntimeException.class, () -> weiboService.update(null));
    Weibo weibo = weiboService.getById(MIN_ID);
    Weibo before = weiboMapper.selectById(MIN_ID);

    // 验证缓存中有值
    String key = ID_KEY_PREFIX + MIN_ID;
    Assertions.assertNotNull(getWeiboFromCache(key));

    // 测试更新微博信息
    Integer newUserId = 2;
    String newContent = "新微博内容" + MIN_ID;
    weibo.setUserId(newUserId);
    weibo.setContent(newContent);
    // 不允许修改博所属的用户
    Assertions.assertThrows(RuntimeException.class, () -> weiboService.update(weibo));
    weibo.setUserId(before.getUserId());
    boolean isSuccess = weiboService.update(weibo);

    Assertions.assertTrue(isSuccess);
    Weibo after = weiboMapper.selectById(MIN_ID);
    Assertions.assertEquals(before.getUserId(), after.getUserId());
    Assertions.assertEquals(newContent, after.getContent());

    // 验证更新成功后会从缓存中删除数据
    Assertions.assertNull(getWeiboFromCache(key));

    // 还原
    weibo.setUserId(1);
    weibo.setContent("用户1的微博1");
    weiboService.update(weibo);
  }

  @Test
  @Order(4)
  void delete() {
    // 测试参数为空的情况
    Assertions.assertThrows(RuntimeException.class, () -> weiboService.delete(null));
    Weibo weibo = new Weibo();
    weibo.setUserId(1);
    weibo.setContent("待删除的微博");
    weiboService.add(weibo);
    int id = weibo.getId();
    boolean delRes = weiboService.delete(id);
    Assertions.assertTrue(delRes);

    // 验证数据库中已经没有数据
    weibo = weiboMapper.selectById(id);
    Assertions.assertNull(weibo);

    // 验证缓存中已经没有数据
    String key = ID_KEY_PREFIX + id;
    Assertions.assertNull(getWeiboFromCache(key));
  }

  @Test
  @Order(5)
  void getLatestPageByUserId() {
    // 新建一个用户来测试
    User user = new User();
    user.setAccount(IdUtil.nanoId());
    user.setName("待删除的用户");
    userService.add(user);
    Integer userId = user.getId();
    int size = 10;

    // 验证如果分页 size 不是 50 的约数，就会抛异常
    Assertions.assertThrows(
        RuntimeException.class, () -> weiboService.getLatestPageByUserId(userId, new Page<>(1, 3)));

    // 验证新用户的微博数量为零
    IPage<Weibo> pageRes = weiboService.getLatestPageByUserId(userId, new Page<>(1, size));
    Assertions.assertEquals(0, pageRes.getRecords().size());
    String key = WEIBO_IDS_BY_USER_ID_CACHE_PREFIX + "::" + userId;

    // 新增一些微博，并测试在不同数量下，分页查出的第一条都是最新的微博
    List<Integer> weiboIdList = new ArrayList<>();
    for (int i = 1; i <= 55; i++) {
      Weibo weibo = new Weibo();
      weibo.setUserId(userId);
      weibo.setContent("用户" + userId + "的微博" + i);
      weiboService.add(weibo);
      weiboIdList.add(weibo.getId());
      if (i % 5 == 0) {
        pageRes = weiboService.getLatestPageByUserId(userId, new Page<>(1, size));
        Assertions.assertEquals(i == 5 ? 5 : size, pageRes.getRecords().size());
        Assertions.assertEquals(weibo.getId(), pageRes.getRecords().get(0).getId());
        log.info("查询结果：" + pageRes.getRecords());
      }
      // 验证缓存中的数量小于指定值（即新添加微博后，如果超过数量，需要从缓存中删除旧微博）
      Long cacheSize = redisTemplate.opsForZSet().size(key);
      Assertions.assertNotNull(cacheSize);
      Assertions.assertTrue(cacheSize.intValue() <= CACHE_WEIBO_SIZE_EACH_USER);
    }

    // 提升代码覆盖率，删除缓存，但数据库中还存在
    redisTemplate.delete(key);
    pageRes = weiboService.getLatestPageByUserId(userId, new Page<>(1, size));
    Assertions.assertEquals(size, pageRes.getRecords().size());
    Assertions.assertEquals(
        weiboIdList.get(weiboIdList.size() - 1), pageRes.getRecords().get(0).getId());

    // 删除刚刚新增的测试数据
    userService.delete(userId);
    weiboMapper.deleteBatchIds(weiboIdList);
  }

}
