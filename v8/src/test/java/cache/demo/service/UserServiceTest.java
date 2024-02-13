package cache.demo.service;

import static cache.demo.cache.UserCache.USER_ACCOUNT_CACHE_PREFIX;
import static cache.demo.cache.UserCache.USER_ID_CACHE_PREFIX;

import cache.demo.cache.CacheUtil;
import cache.demo.entity.User;
import cache.demo.mapper.UserMapper;
import common.WithSpringBootTestAnnotation;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * 用户服务测试类
 *
 * @author Camio1945
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserServiceTest extends WithSpringBootTestAnnotation {
  private static final int MIN_ID = 1;
  private static final int MAX_ID = 10000;
  private static final String ID_KEY_PREFIX = USER_ID_CACHE_PREFIX + "::";
  private static final String ACCOUNT_KEY_PREFIX = USER_ACCOUNT_CACHE_PREFIX + "::";

  @Autowired IUserService userService;
  @Autowired CacheUtil cacheUtil;

  /** 引入 Mapper 是为了直接查数据库，不经过缓存 */
  @Autowired UserMapper userMapper;

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
    Assertions.assertThrows(RuntimeException.class, () -> userService.getById(null));

    // 测试正常情况，这里查的是数据库，并且会把查到的数据放入缓存
    User user = userService.getById(MIN_ID);
    Assertions.assertNotNull(user);
    Assertions.assertEquals(MIN_ID, user.getId());

    // 确认缓存中有值，并且就是我们存进去的值
    User cacheValue = getUserFromCache(ID_KEY_PREFIX + MIN_ID);
    Assertions.assertNotNull(cacheValue);
    Assertions.assertEquals(MIN_ID, cacheValue.getId().intValue());

    // 测试查询数据库中不存在的数据，如果 id 只稍微大一点点，则会查到空值，并且会把空值放入缓存
    userMapper.deleteById(MAX_ID + 1);
    int notExistsId = MAX_ID + 1;
    User notExistsUser = userService.getById(notExistsId);
    Assertions.assertNull(notExistsUser);
    Boolean hasKey = redisTemplate.hasKey(ID_KEY_PREFIX + notExistsId);
    Assertions.assertTrue(hasKey != null && hasKey);

    // 测试查询数据库中不存在的数据，如果 id 太大，则会直接抛异常，而不会放入缓存
    int bigId = Integer.MAX_VALUE;
    Assertions.assertThrows(RuntimeException.class, () -> userService.getById(bigId));
    String notExistsKey = ID_KEY_PREFIX + bigId;
    cacheValue = getUserFromCache(notExistsKey);
    Assertions.assertNull(cacheValue);
  }

  private User getUserFromCache(String key) {
    try {
      return (User) redisTemplate.opsForValue().get(key);
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
    Assertions.assertThrows(RuntimeException.class, () -> userService.add(null));

    // 先从数据库中删除指定的数据，以方便接下来添加，以免主键冲突
    int newId = MAX_ID + 1;
    userService.delete(newId);

    // 特意缓存空值，确认 redis 缓存中存在这个键
    userService.getById(newId);
    Boolean hasKey = redisTemplate.hasKey(ID_KEY_PREFIX + newId);
    Assertions.assertTrue(hasKey != null && hasKey);

    // 测试添加用户到数据库中
    User user = new User();
    // 为了测试方便，这里指定了 id ，但数据库中的 id 是自增的，实际开发时不需要也不应该指定 id，
    // 强行指定 id 时，如果该 id 在数据库中已经存在了，而并不会新增记录，而会修改记录，从业务上讲就是 Bug 了。
    user.setId(newId);
    user.setName("用户" + newId);
    user.setAccount("user" + newId);
    boolean isSuccess = userService.add(user);
    Assertions.assertTrue(isSuccess);

    // 验证添加成功后会从缓存中删除数据
    Assertions.assertFalse(cacheUtil.hasKey(ID_KEY_PREFIX + newId));
    Assertions.assertFalse(cacheUtil.hasKey(USER_ACCOUNT_CACHE_PREFIX + "::" + user.getAccount()));

    // 验证添加相同的账号时会抛异常
    user.setId(null);
    Assertions.assertThrows(RuntimeException.class, () -> userService.add(user));
  }

  @Test
  @Order(3)
  void update() {
    // 测试参数为空的情况
    Assertions.assertThrows(RuntimeException.class, () -> userService.update(null));
    User user = userService.getById(MIN_ID);
    User before = userMapper.selectById(MIN_ID);

    // 验证缓存中有值
    String key = ID_KEY_PREFIX + MIN_ID;
    Assertions.assertNotNull(getUserFromCache(key));

    // 测试更新用户信息
    String newName = "更新用户姓名" + MIN_ID;
    String newAccount = "newAccount" + MIN_ID;
    user.setName(newName);
    user.setAccount(newAccount);
    boolean isSuccess = userService.update(user);
    Assertions.assertTrue(isSuccess);
    User after = userMapper.selectById(MIN_ID);
    Assertions.assertEquals(newName, after.getName());
    Assertions.assertEquals(newAccount, after.getAccount());

    // 验证更新成功后会从缓存中删除数据
    Assertions.assertNull(getUserFromCache(key));
    Assertions.assertFalse(
        cacheUtil.hasKey(USER_ACCOUNT_CACHE_PREFIX + "::" + before.getAccount()));
    Assertions.assertFalse(cacheUtil.hasKey(USER_ACCOUNT_CACHE_PREFIX + "::" + after.getAccount()));

    // 验证把账号改成已经存在的账号时会抛异常
    user.setAccount("user2");
    Assertions.assertThrows(RuntimeException.class, () -> userService.update(user));

    // 还原账号
    user.setName("用户1");
    user.setAccount("user1");
    userService.update(user);
  }

  @Test
  @Order(4)
  void delete() {
    // 测试参数为空的情况
    Assertions.assertThrows(RuntimeException.class, () -> userService.delete(null));
    int id = MAX_ID + 1;
    User user = userService.getById(id);
    if (user != null) {
      String account = user.getAccount();
      boolean delRes = userService.delete(id);
      Assertions.assertTrue(delRes);

      // 验证数据库中已经没有数据
      user = userMapper.selectById(id);
      Assertions.assertNull(user);

      // 验证缓存中已经没有数据
      String key = ID_KEY_PREFIX + id;
      Assertions.assertNull(getUserFromCache(key));
      Assertions.assertFalse(cacheUtil.hasKey(USER_ACCOUNT_CACHE_PREFIX + "::" + account));
    }
  }

  /**
   * 应该能得到 2 条与本测试方法相关的 SQL 语句
   *
   * <pre>
   * ==>  Preparing: select id from user where account = ?
   * ==>  Preparing: SELECT id,name,account FROM user WHERE id=?
   * </pre>
   */
  @Test
  @Order(5)
  void getByIdFromAccount() {
    redisTemplate.delete(ID_KEY_PREFIX + "1");
    String account = "user1";
    redisTemplate.delete(ACCOUNT_KEY_PREFIX + account);
    User first = userService.getByAccount(account);
    Assertions.assertNotNull(first);
    User second = userService.getByAccount(account);
    Assertions.assertNotNull(second);
    log.info("查看控制台，会打印 2 条与本测试方法相关的 SQL 语句，说明缓存生效了");
  }
}
