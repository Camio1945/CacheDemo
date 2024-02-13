package cache.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 用户
 *
 * @author Camio1945
 */
@Data
public class User {
  @TableId(type = IdType.AUTO)
  private Integer id;
  private String name;
  private String account;
}
