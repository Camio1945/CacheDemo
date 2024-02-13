package cache.demo.common;

import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.dialects.MySqlDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Plus 配置类
 *
 * @author Camio1945
 */
@Configuration
public class MyBatisPlusConfig {

  /** 分页配置 */
  @Bean
  public PaginationInnerInterceptor paginationInterceptor() {
    PaginationInnerInterceptor page = new PaginationInnerInterceptor();
    page.setDialect(new MySqlDialect());
    return page;
  }
}
