package cache.demo.mapper;

import cache.demo.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户数据库操作
 *
 * @author Camio1945
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

  /**
   * 查询最大 id
   *
   * @return 最大 id
   */
  @Select("select ifnull(max(id), 0) from user")
  int selectMaxId();

  /**
   * 根据账号查询 id
   *
   * @param account 账号
   * @return id
   */
  @Select("select id from user where account = #{account}")
  Integer selectIdByAccount(String account);

}
