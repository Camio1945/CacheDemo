package cache.demo;

import java.io.Serializable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动器
 *
 * @author Camio1945
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class CacheDemoApplication implements Serializable {

  public static void main(String[] args) {
    SpringApplication.run(CacheDemoApplication.class, args);
  }
}
