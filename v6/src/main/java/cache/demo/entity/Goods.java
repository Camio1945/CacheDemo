package cache.demo.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

/**
 * 商品
 *
 * @author Camio1945
 */
@Data
public class Goods {
  @TableId(type = IdType.AUTO)
  private Integer id;
  private Integer storeId;
  private String name;
  private int stock;
  private BigDecimal price;
}
