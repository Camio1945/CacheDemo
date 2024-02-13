package cache.demo.service.impl;

import static cache.demo.cache.WeiboCache.CACHE_WEIBO_SIZE_EACH_USER;

import cache.demo.cache.WeiboCache;
import cache.demo.entity.Weibo;
import cache.demo.mapper.WeiboMapper;
import cache.demo.service.IWeiboService;
import cn.hutool.core.lang.Assert;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.springframework.stereotype.Service;

/**
 * 微博服务实现类
 *
 * @author Camio1945
 */
@Service
@AllArgsConstructor
public class WeiboServiceImpl implements IWeiboService {
  private WeiboCache weiboCache;
  private WeiboMapper weiboMapper;

  @Override
  public IPage<Weibo> getLatestPageByUserId(@NonNull Integer userId, Page<Weibo> pageReq) {
    pageReq.setSearchCount(false);
    long size = pageReq.getSize();
    Assert.isTrue(
        size > 0 && CACHE_WEIBO_SIZE_EACH_USER % size == 0,
        "分页参数非法，每页大小必须是 " + CACHE_WEIBO_SIZE_EACH_USER + " 的约数");
    int toIndex = (int) (pageReq.getCurrent() * size);
    if (toIndex < CACHE_WEIBO_SIZE_EACH_USER) {
      List<Weibo> weiboList =
          weiboCache.getLatestWeiboListByUserId(userId, pageReq, weiboCache::getById);
      return pageReq.setRecords(weiboList);
    } else {
      LambdaQueryWrapper<Weibo> queryWrapper =
          new LambdaQueryWrapper<Weibo>().eq(Weibo::getUserId, userId).orderByDesc(Weibo::getId);
      return weiboMapper.selectPage(pageReq, queryWrapper);
    }
  }

  @Override
  public boolean add(@NonNull Weibo weibo) {
    boolean success = weiboMapper.insert(weibo) > 0;
    if (success) {
      WeiboCache.updateMaxAllowedIdByWeiboId(weibo.getId());
      weiboCache.handleCacheAfterAdd(weibo);
    }
    return success;
  }

  @Override
  public boolean update(@NonNull Weibo weibo) {
    Weibo before = getById(weibo.getId());
    Assert.equals(weibo.getUserId(), before.getUserId(), "不允许修改微博所属的用户");
    boolean updateRes = weiboMapper.updateById(weibo) > 0;
    if (updateRes) {
      weiboCache.handleCacheAfterUpdate(before, weibo);
    }
    return updateRes;
  }

  @Override
  public Weibo getById(@NonNull Integer id) {
    return weiboCache.getById(id);
  }

  @Override
  public boolean delete(@NonNull Integer id) {
    Weibo before = getById(id);
    boolean deleteRes = weiboMapper.deleteById(id) > 0;
    if (deleteRes) {
      weiboCache.handleCacheAfterDelete(before);
    }
    return deleteRes;
  }

  @Override
  public void updateMaxAllowedId() {
    WeiboCache.updateMaxAllowedIdByWeiboId(weiboMapper.selectMaxId());
  }
}
