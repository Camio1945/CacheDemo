package cache.demo.mapper;

import cache.demo.entity.Goods;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
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

  /**
   * 根据店铺 id 和商品名称获取商品 id
   *
   * @param storeId 店铺 id
   * @param name 商品名称
   * @return 商品 id
   */
  @Select("select id from goods where store_id = #{storeId} and name = #{name} limit 1 ")
  Integer selectIdByStoreIdName(Integer storeId, String name);

  /**
   * 根据店铺 id 获取商品 id 集合
   *
   * @param storeId 店铺 id
   * @return 商品 id
   */
  @Select("select id from goods where store_id = #{storeId} order by id desc")
  List<Integer> selectIdsByStoreId(Integer storeId);
}
