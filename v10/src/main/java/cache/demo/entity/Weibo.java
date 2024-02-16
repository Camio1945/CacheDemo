package cache.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

/**
 * 微博
 *
 * @author Camio1945
 */
@Data
public class Weibo {
  @TableId(type = IdType.AUTO)
  private Integer id;
  private Integer userId;
  private String content;
}
