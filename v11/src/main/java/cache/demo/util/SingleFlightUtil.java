package cache.demo.util;

import cn.hutool.core.exceptions.ExceptionUtil;
import java.util.concurrent.*;

/**
 * 单飞工具类
 *
 * <pre>
 * 参考了<a href="https://pkg.go.dev/golang.org/x/sync/singleflight"> go 语言的 singleflight</a>
 * </pre>
 *
 * @author Camio1945
 */
public class SingleFlightUtil {

  private static final ConcurrentHashMap<String, FutureTask> KEY_TO_FUTURE_TASK_MAP =
      new ConcurrentHashMap<>();

  private SingleFlightUtil() {}

  public static <T> T execute(String key, Callable<T> fn) {
    FutureTask<T> task = new FutureTask<>(fn);
    FutureTask<T> existingTask = KEY_TO_FUTURE_TASK_MAP.putIfAbsent(key, task);
    try {
      // 如果 key 不存在，则 existingTask 为 null，此时需要执行 task.run()，否则直接返回 existingTask.get()
      if (existingTask == null) {
        T res;
        try {
          task.run();
          res = task.get();
        } finally {
          KEY_TO_FUTURE_TASK_MAP.remove(key);
        }
        return res;
      } else {
        return existingTask.get();
      }
    } catch (InterruptedException e) {
      // 这里的代码在单元测试时覆盖不到，可以忽略
      Thread.currentThread().interrupt();
      throw ExceptionUtil.wrapRuntime(e);
    } catch (ExecutionException e) {
      // 这里的代码在单元测试时覆盖不到，可以忽略
      throw ExceptionUtil.wrapRuntime(e);
    }
  }
}
