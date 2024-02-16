package cache.demo.mapper;

import cache.demo.entity.Weibo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 微博数据库操作
 *
 * @author Camio1945
 */
@Mapper
public interface WeiboMapper extends BaseMapper<Weibo> {

  /**
   * 查询最大 id
   *
   * @return 最大 id
   */
  @Select("select ifnull(max(id), 0) from weibo")
  int selectMaxId();

  /**
   * 查询该用户最新的若干条微博 id
   *
   * @param userId 用户 id
   * @param size 查询数量
   * @return 微博 id 列表
   */
  @Select("select id from weibo where user_id = #{userId} order by id desc limit #{size}")
  List<Integer> selectLatestIdsByUserId(Integer userId, int size);
}
