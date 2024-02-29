package cache.demo.listener;

import cache.demo.service.*;
import cn.hutool.core.thread.ThreadUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.stereotype.Service;

/**
 * 应用事件监听器
 *
 * @author HuKaiXuan
 */
@Slf4j
@Service
@AllArgsConstructor
public class ApplicationEventListener implements ApplicationListener {
  private IGoodsService goodsService;
  private IUserService userService;
  private IWeiboService weiboService;

  @Override
  public void onApplicationEvent(ApplicationEvent event) {
    // 项目启动完成事件
    if (event instanceof ApplicationReadyEvent) {
      log.info("项目启动完成，监听器 " + ApplicationEventListener.class + " 开始执行");
      updateMaxAllowedId();
      return;
    }
    // 项目停止和应用关闭事件
    if ((event instanceof ContextStoppedEvent) || (event instanceof ContextClosedEvent)) {
      log.info("项目停止和应用关闭事件");
    }
  }

  private void updateMaxAllowedId() {
    ThreadUtil.execute(
        () -> {
          goodsService.updateMaxAllowedId();
          userService.updateMaxAllowedId();
          weiboService.updateMaxAllowedId();
        });
  }
}
