package cache.demo.mapper;

import cache.demo.entity.Goods;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品数据库操作
 *
 * @author Camio1945
 */
@Mapper
public interface GoodsMapper extends BaseMapper<Goods> {

}
