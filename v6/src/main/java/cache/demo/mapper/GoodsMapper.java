package cache.demo.mapper;

import cache.demo.entity.Goods;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 商品数据库操作
 *
 * @author Camio1945
 */
@Mapper
public interface GoodsMapper extends BaseMapper<Goods> {

  /**
   * 查询最大 id
   *
   * @return 最大 id
   */
  @Select("select ifnull(max(id), 0) from goods")
  int selectMaxId();
}
