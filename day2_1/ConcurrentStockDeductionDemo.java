import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

//（电商抢购场景）
public class ConcurrentStockDeductionDemo {

    // 商品库存（核心共享资源）：使用AtomicInteger保证原子性
    private final AtomicInteger stock = new AtomicInteger(100);

    // 抢购结果记录（线程安全的并发容器）
    private final Map<String, String> resultMap = new ConcurrentHashMap<>();

    // 可重入锁（用于复杂业务逻辑的同步，比如扣减+记录结果的原子操作）
    private final ReentrantLock lock = new ReentrantLock(true); // 公平锁，避免线程饥饿

    // 线程池（模拟用户请求线程）：核心参数根据业务调整
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            20, // 核心线程数（处理常驻请求）
            100, // 最大线程数（应对峰值请求）
            60, // 非核心线程空闲超时时间
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000), // 任务队列
            new ThreadFactory() { // 自定义线程工厂，方便排查问题
                private final AtomicInteger threadNum = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("抢购线程-" + threadNum.getAndIncrement());
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：超出容量时由调用线程执行
    );

    /**
     * 库存扣减方法（核心业务逻辑）
     * @param userId 用户ID
     * @return 抢购结果
     */
    public String deductStock(String userId) {
        // 加锁：保证「扣减库存+记录结果」的原子性
        lock.lock();
        try {
            // 1. 检查库存
            if (stock.get() <= 0) {
                String result = userId + "：抢购失败，库存不足";
                resultMap.put(userId, result);
                return result;
            }

            // 2. 扣减库存（AtomicInteger的getAndDecrement保证原子性）
            int remainStock = stock.getAndDecrement();
            String result = userId + "：抢购成功，剩余库存：" + (remainStock - 1);
            resultMap.put(userId, result);
            return result;
        } 
        finally {
            // 必须在finally中释放锁，避免死锁
            lock.unlock();
        }
    }

    /**
     * 模拟批量用户抢购
     * @param userCount 用户数量
     */
    public void simulateRushPurchase(int userCount) {
        // 提交抢购任务到线程池
        for (int i = 1; i <= userCount; i++) {
            String userId = "用户" + i;
            executor.submit(() -> {
                try {
                    // 模拟网络延迟/业务处理耗时
                    TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(10));
                    String result = deductStock(userId);
                    System.out.println(result);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println(userId + "：抢购请求被中断");
                }
            });
        }

        // 关闭线程池（等待所有任务执行完成）
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                executor.shutdownNow(); // 超时强制关闭
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        // 输出最终统计结果
        System.out.println("\n===== 抢购结束 =====");
        System.out.println("初始库存：100");
        System.out.println("剩余库存：" + stock.get());
        long successCount = resultMap.values().stream().filter(s -> s.contains("成功")).count();
        System.out.println("抢购成功人数：" + successCount);
        System.out.println("抢购失败人数：" + (userCount - successCount));
    }

    public static void main(String[] args) {
        // 模拟1000个用户抢购100件商品
        ConcurrentStockDeductionDemo demo = new ConcurrentStockDeductionDemo();
        demo.simulateRushPurchase(1000);
    }
}