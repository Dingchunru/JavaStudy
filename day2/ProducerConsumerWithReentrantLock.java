import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

// 缓冲区（共享资源）
class BufferWithLock {
    private final int capacity;
    private final Queue<Integer> queue;
    private final ReentrantLock lock;
    private final Condition notFull;
    private final Condition notEmpty;

    public BufferWithLock(int capacity) {
        this.capacity = capacity;
        this.queue = new LinkedList<>();
        this.lock = new ReentrantLock(true);
        this.notFull = lock.newCondition();
        this.notEmpty = lock.newCondition();
    }

    public void produce(int data) throws InterruptedException {
        lock.lock();
        try {
            while (queue.size() == capacity) {
                System.out.println("缓冲区满，生产者等待 | 当前容量：" + queue.size());
                notFull.await();
            }
            queue.offer(data);
            System.out.println("生产数据：" + data + " | 当前缓冲区大小：" + queue.size());
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public void consume() throws InterruptedException {
        lock.lock();
        try {
            while (queue.isEmpty()) {
                System.out.println("缓冲区空，消费者等待 | 当前容量：" + queue.size());
                notEmpty.await();
            }
            int data = queue.poll();
            System.out.println("消费数据：" + data + " | 当前缓冲区大小：" + queue.size());
            notFull.signal();
        } finally {
            lock.unlock();
        }
    }
}

// 生产者线程
class Producer implements Runnable {
    private final BufferWithLock buffer;
    private int data = 0;
    private volatile boolean stop = false;

    public Producer(BufferWithLock buffer) {
        this.buffer = buffer;
    }

    public void stop() {
        this.stop = true;
    }

    @Override
    public void run() {
        try {
            while (!stop) {
                buffer.produce(data);
                data++;
                Thread.sleep(500);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("生产者线程被中断");
        }
        System.out.println("生产者线程正常退出");
    }
}

// 消费者线程
class Consumer implements Runnable {
    private final BufferWithLock buffer;
    private volatile boolean stop = false;

    public Consumer(BufferWithLock buffer) {
        this.buffer = buffer;
    }

    public void stop() {
        this.stop = true;
    }

    @Override
    public void run() {
        try {
            while (!stop) {
                buffer.consume();
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("消费者线程被中断");
        }
        System.out.println("消费者线程正常退出");
    }
}

public class ProducerConsumerWithReentrantLock {
    public static void main(String[] args) {
        // 1. 创建缓冲区（容量5）
        BufferWithLock buffer = new BufferWithLock(5);

        // 2. 创建生产者、消费者实例
        Producer producer = new Producer(buffer);
        Consumer consumer = new Consumer(buffer);

        // 3. 创建线程池
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2,
                4,
                10,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // 4. 提交生产/消费任务
        executor.submit(producer);
        executor.submit(consumer);

        // 5. 模拟运行10秒后，优雅停止
        try {
            Thread.sleep(10000);
            producer.stop();
            consumer.stop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 6. 关闭线程池
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                System.out.println("线程池强制关闭");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        System.out.println("程序执行完毕");
    }
}