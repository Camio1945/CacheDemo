package cache.demo.service.impl;

import cache.demo.cache.UserCache;
import cache.demo.entity.User;
import cache.demo.mapper.UserMapper;
import cache.demo.service.IUserService;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现类
 *
 * @author Camio1945
 */
@Service
@AllArgsConstructor
public class UserServiceImpl implements IUserService {
  private UserCache userCache;
  private UserMapper userMapper;

  @Override
  public User getById(@NonNull Integer id) {
    return userCache.getById(id);
  }

  public User getByAccount(@NonNull String account) {
    return userCache.getById(userCache.getIdByAccount(account));
  }

  @Override
  public boolean add(@NonNull User user) {
    boolean success = userMapper.insert(user) > 0;
    if (success) {
      UserCache.updateMaxAllowedIdByUserId(user.getId());
      userCache.handleCacheAfterAdd(user);
    }
    return success;
  }

  @Override
  public boolean update(@NonNull User user) {
    User before = getById(user.getId());
    boolean updateRes = userMapper.updateById(user) > 0;
    if (updateRes) {
      userCache.handleCacheAfterUpdate(before, user);
    }
    return updateRes;
  }

  @Override
  public boolean delete(@NonNull Integer id) {
    User before = getById(id);
    boolean deleteRes = userMapper.deleteById(id) > 0;
    if (deleteRes) {
      userCache.handleCacheAfterDelete(before);
    }
    return deleteRes;
  }

  @Override
  public void updateMaxAllowedId() {
    UserCache.updateMaxAllowedIdByUserId(userMapper.selectMaxId());
  }
}
