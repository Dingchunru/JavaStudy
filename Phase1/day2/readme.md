



# Java并发编程核心机制详解

## 一、内存模型与可见性问题

### 1.1 JMM（Java内存模型）

JMM定义了线程与主内存之间的抽象关系，解决多线程环境下的**可见性、有序性、原子性**问题。

#### 内存结构
- **主内存**：所有共享变量的存储区域，线程间可见
- **工作内存**：每个线程私有的内存空间，存储主内存变量的副本
- **交互协议**：`read` → `load` → `use` → `assign` → `store` → `write`

#### 核心特性
```java
// 可见性问题示例
public class VisibilityProblem {
    private static boolean flag = true; // 无volatile修饰
    
    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            while (flag) { // 可能永远看不到主线程的修改
                // 空循环
            }
            System.out.println("Worker thread stopped");
        });
        
        worker.start();
        Thread.sleep(1000);
        flag = false; // 修改可能对其他线程不可见
    }
}
```

### 1.2 volatile关键字

`volatile`是轻量级同步机制，保证可见性和有序性，**不保证原子性**。

#### 特性详解
```java
public class VolatileDemo {
    // 可见性保证
    private volatile boolean shutdown = false;
    
    // 有序性保证（禁止指令重排）
    private volatile static Singleton instance;
    
    public void shutdown() {
        shutdown = true; // 立即写入主内存
    }
    
    public void work() {
        while (!shutdown) { // 每次从主内存读取
            // 执行任务
        }
    }
    
    // 双重检查锁（DCL）单例模式
    public static Singleton getInstance() {
        if (instance == null) {
            synchronized (Singleton.class) {
                if (instance == null) {
                    instance = new Singleton(); // volatile防止重排序
                }
            }
        }
        return instance;
    }
}
```

#### volatile内存语义
- **写操作**：立即刷新到主内存
- **读操作**：从主内存重新加载
- **内存屏障**：防止指令重排序
  - 写屏障：确保之前的操作都完成
  - 读屏障：确保之后的读操作看到最新值

## 二、原子操作与CAS

### 2.1 CAS（Compare-And-Swap）

CAS是乐观锁的核心思想，通过硬件指令实现原子操作。

```java
import java.util.concurrent.atomic.AtomicInteger;

public class CASDemo {
    private AtomicInteger counter = new AtomicInteger(0);
    
    public void increment() {
        int oldValue;
        int newValue;
        do {
            oldValue = counter.get(); // 预期值
            newValue = oldValue + 1;  // 新值
        } while (!counter.compareAndSet(oldValue, newValue)); // CAS操作
    }
    
    // ABA问题示例
    public void abaProblem() {
        AtomicInteger atomic = new AtomicInteger(10);
        
        Thread t1 = new Thread(() -> {
            int value = atomic.get(); // A = 10
            // 模拟耗时操作
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            // 此时atomic可能经历了 10→20→10
            boolean success = atomic.compareAndSet(value, 15);
            System.out.println("CAS操作结果: " + success); // 可能成功，但实际已经变化过了
        });
    }
}

// 解决ABA问题：使用版本号
import java.util.concurrent.atomic.AtomicStampedReference;

public class ABASolution {
    private AtomicStampedReference<Integer> atomicRef = 
        new AtomicStampedReference<>(10, 0);
    
    public void update() {
        int[] stampHolder = new int[1];
        int oldRef = atomicRef.get(stampHolder);
        int oldStamp = stampHolder[0];
        int newRef = oldRef + 5;
        
        // 比较引用值和版本号
        boolean success = atomicRef.compareAndSet(
            oldRef, newRef, 
            oldStamp, oldStamp + 1
        );
    }
}
```

### 2.2 Atomic原子类家族

| 类别 | 典型类 | 用途 |
|------|--------|------|
| 基本类型 | `AtomicInteger`, `AtomicLong`, `AtomicBoolean` | 原子更新基本类型 |
| 引用类型 | `AtomicReference`, `AtomicStampedReference`, `AtomicMarkableReference` | 原子更新引用，解决ABA问题 |
| 数组类型 | `AtomicIntegerArray`, `AtomicLongArray`, `AtomicReferenceArray` | 原子更新数组元素 |
| 字段更新 | `AtomicIntegerFieldUpdater`, `AtomicLongFieldUpdater`, `AtomicReferenceFieldUpdater` | 原子更新对象字段 |

## 三、线程池深度解析

### 3.1 ThreadPoolExecutor核心参数

```java
public class ThreadPoolConfig {
    public ThreadPoolExecutor createThreadPool() {
        return new ThreadPoolExecutor(
            // 核心参数
            5,     // corePoolSize: 核心线程数，常驻线程
            10,    // maximumPoolSize: 最大线程数
            60L,   // keepAliveTime: 非核心线程空闲存活时间
            TimeUnit.SECONDS, // 时间单位
            
            // 任务队列（缓冲作用）
            new ArrayBlockingQueue<>(100), // 有界队列
            
            // 线程工厂
            new CustomThreadFactory(),
            
            // 拒绝策略
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
    
    // 常用预定义线程池（不推荐直接使用，根据场景自定义）
    public void predefindPools() {
        // 1. 固定大小线程池
        ExecutorService fixedPool = Executors.newFixedThreadPool(10);
        
        // 2. 单线程池
        ExecutorService singlePool = Executors.newSingleThreadExecutor();
        
        // 3. 缓存线程池
        ExecutorService cachedPool = Executors.newCachedThreadPool();
        
        // 4. 定时任务线程池
        ScheduledExecutorService scheduledPool = 
            Executors.newScheduledThreadPool(5);
    }
}
```

### 3.2 任务队列类型对比

| 队列类型 | 特性 | 适用场景 |
|----------|------|----------|
| `ArrayBlockingQueue` | 有界队列，FIFO，数组实现 | 流量控制，防止资源耗尽 |
| `LinkedBlockingQueue` | 可选有界/无界，链表实现 | 吞吐量优先，无界可能OOM |
| `SynchronousQueue` | 不存储元素，直接传递 | 高并发，任务处理快 |
| `PriorityBlockingQueue` | 优先级队列 | 任务有优先级差异 |
| `DelayedWorkQueue` | 延迟队列 | 定时任务，周期性任务 |

### 3.3 拒绝策略

```java
public class RejectionPolicy {
    // 1. AbortPolicy（默认）: 抛出RejectedExecutionException
    ThreadPoolExecutor.AbortPolicy abortPolicy = 
        new ThreadPoolExecutor.AbortPolicy();
    
    // 2. CallerRunsPolicy: 由调用者线程执行
    ThreadPoolExecutor.CallerRunsPolicy callerRunsPolicy = 
        new ThreadPoolExecutor.CallerRunsPolicy();
    
    // 3. DiscardPolicy: 静默丢弃任务
    ThreadPoolExecutor.DiscardPolicy discardPolicy = 
        new ThreadPoolExecutor.DiscardPolicy();
    
    // 4. DiscardOldestPolicy: 丢弃队列中最老的任务
    ThreadPoolExecutor.DiscardOldestPolicy discardOldestPolicy = 
        new ThreadPoolExecutor.DiscardOldestPolicy();
    
    // 5. 自定义策略
    RejectedExecutionHandler customPolicy = (r, executor) -> {
        // 记录日志
        System.out.println("任务被拒绝: " + r);
        // 重试机制
        if (!executor.isShutdown()) {
            try {
                executor.getQueue().put(r); // 阻塞式重试
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    };
}
```

## 四、锁机制深度剖析

### 4.1 synchronized原理

```java
public class SynchronizedAnalysis {
    // 1. 实例方法锁
    public synchronized void instanceMethod() {
        // 锁对象是this
    }
    
    // 2. 静态方法锁
    public static synchronized void staticMethod() {
        // 锁对象是Class对象
    }
    
    // 3. 同步代码块
    public void syncBlock() {
        Object lock = new Object();
        synchronized(lock) { // 显式指定锁对象
            // 临界区
        }
    }
    
    // 4. 锁升级过程（JDK1.6优化）
    public void lockUpgrade() {
        // 无锁 → 偏向锁 → 轻量级锁 → 重量级锁
        // - 偏向锁: 单线程访问时，记录线程ID
        // - 轻量级锁: 多线程竞争但不激烈时，CAS自旋
        // - 重量级锁: 竞争激烈时，操作系统互斥量
    }
}
```

### 4.2 ReentrantLock vs synchronized

```java
import java.util.concurrent.locks.*;

public class LockComparison {
    private final Object syncLock = new Object();
    private final ReentrantLock reentrantLock = new ReentrantLock();
    private final Condition condition = reentrantLock.newCondition();
    
    // 1. 基本使用对比
    public void basicUsage() {
        // synchronized方式
        synchronized(syncLock) {
            // 自动释放锁
            System.out.println("synchronized block");
        }
        
        // ReentrantLock方式
        reentrantLock.lock();
        try {
            System.out.println("ReentrantLock block");
        } finally {
            reentrantLock.unlock(); // 必须手动释放
        }
    }
    
    // 2. 可中断锁
    public void interruptibleLock() throws InterruptedException {
        // synchronized不可中断
        synchronized(syncLock) {
            // 等待期间无法被中断
        }
        
        // ReentrantLock可中断
        reentrantLock.lockInterruptibly();
        try {
            // 等待锁时可响应中断
        } finally {
            reentrantLock.unlock();
        }
    }
    
    // 3. 尝试获取锁（避免死锁）
    public boolean tryLockExample() {
        // 立即返回结果
        if (reentrantLock.tryLock()) {
            try {
                return true;
            } finally {
                reentrantLock.unlock();
            }
        }
        return false;
    }
    
    // 4. 超时获取锁
    public boolean timeoutLock() throws InterruptedException {
        // 等待最多1秒
        if (reentrantLock.tryLock(1, TimeUnit.SECONDS)) {
            try {
                return true;
            } finally {
                reentrantLock.unlock();
            }
        }
        return false;
    }
    
    // 5. 公平锁 vs 非公平锁
    public void fairLock() {
        // 公平锁（按等待顺序获取）
        ReentrantLock fairLock = new ReentrantLock(true);
        
        // 非公平锁（可插队，吞吐量高）
        ReentrantLock unfairLock = new ReentrantLock(false); // 默认
    }
    
    // 6. 条件变量（精准唤醒）
    public void conditionExample() throws InterruptedException {
        reentrantLock.lock();
        try {
            // 等待条件
            while (!conditionMet()) {
                condition.await(); // 释放锁并等待
            }
            
            // 处理业务
            
            // 唤醒特定线程
            condition.signal(); // 唤醒一个
            // condition.signalAll(); // 唤醒所有
        } finally {
            reentrantLock.unlock();
        }
    }
    
    private boolean conditionMet() {
        return true;
    }
}
```

### 4.3 锁性能对比与选择策略

| 场景 | 推荐方案 | 理由 |
|------|----------|------|
| 简单同步场景 | `synchronized` | 语法简洁，自动释放，JVM优化好 |
| 需要可中断 | `ReentrantLock` | 支持`lockInterruptibly()` |
| 需要超时控制 | `ReentrantLock` | 支持`tryLock(timeout)` |
| 需要公平性 | `ReentrantLock(true)` | 可配置公平锁 |
| 需要多个条件 | `ReentrantLock` + `Condition` | 支持多个等待队列 |
| 读多写少 | `ReentrantReadWriteLock` | 读写分离，提高并发 |
| 高性能统计 | `LongAdder` | 分散竞争，适合统计场景 |

## 五、生产者消费者模型实现

### 5.1 多种实现方式对比

```java
import java.util.concurrent.*;

// 1. wait/notify实现（经典方式）
class ProducerConsumerWaitNotify {
    private final Queue<Integer> queue = new LinkedList<>();
    private final int MAX_SIZE = 10;
    
    public void produce() throws InterruptedException {
        synchronized(queue) {
            while (queue.size() == MAX_SIZE) {
                queue.wait(); // 等待队列不满
            }
            queue.add(1);
            queue.notifyAll(); // 通知消费者
        }
    }
    
    public void consume() throws InterruptedException {
        synchronized(queue) {
            while (queue.isEmpty()) {
                queue.wait(); // 等待队列不空
            }
            queue.poll();
            queue.notifyAll(); // 通知生产者
        }
    }
}

// 2. ReentrantLock + Condition实现（精准通知）
class ProducerConsumerLockCondition {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();
    private final Queue<Integer> queue = new LinkedList<>();
    private final int MAX_SIZE = 10;
    
    public void produce() throws InterruptedException {
        lock.lock();
        try {
            while (queue.size() == MAX_SIZE) {
                notFull.await(); // 等待队列不满
            }
            queue.add(1);
            notEmpty.signal(); // 只通知消费者
        } finally {
            lock.unlock();
        }
    }
    
    public void consume() throws InterruptedException {
        lock.lock();
        try {
            while (queue.isEmpty()) {
                notEmpty.await(); // 等待队列不空
            }
            queue.poll();
            notFull.signal(); // 只通知生产者
        } finally {
            lock.unlock();
        }
    }
}

// 3. BlockingQueue实现（最简单）
class ProducerConsumerBlockingQueue {
    private final BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(10);
    
    public void produce() throws InterruptedException {
        queue.put(1); // 自动阻塞
    }
    
    public void consume() throws InterruptedException {
        queue.take(); // 自动阻塞
    }
}

// 4. Semaphore实现（信号量控制）
class ProducerConsumerSemaphore {
    private final Semaphore notEmpty = new Semaphore(0);
    private final Semaphore notFull = new Semaphore(10);
    private final Semaphore mutex = new Semaphore(1);
    private final Queue<Integer> queue = new LinkedList<>();
    
    public void produce() throws InterruptedException {
        notFull.acquire(); // 确保队列不满
        mutex.acquire();   // 互斥访问
        try {
            queue.add(1);
        } finally {
            mutex.release();
            notEmpty.release(); // 通知可消费
        }
    }
    
    public void consume() throws InterruptedException {
        notEmpty.acquire(); // 确保队列不空
        mutex.acquire();
        try {
            queue.poll();
        } finally {
            mutex.release();
            notFull.release(); // 通知可生产
        }
    }
}
```

### 5.2 性能与适用场景

| 实现方式 | 优点 | 缺点 | 适用场景 |
|----------|------|------|----------|
| wait/notify | 原生支持，简单 | 需要手动同步，易出错 | 简单同步需求 |
| Lock+Condition | 精准唤醒，灵活 | 代码较复杂 | 多条件复杂同步 |
| BlockingQueue | 最简洁，线程安全 | 功能受限 | 简单生产者消费者 |
| Semaphore | 控制并发数灵活 | 组合使用较复杂 | 资源池，限流 |

## 六、最佳实践与注意事项

### 6.1 并发编程原则

1. **避免锁竞争**
   ```java
   // 错误示例：锁粒度太粗
   public synchronized void processAll() {
       // 长时间操作
   }
   
   // 正确示例：减小锁粒度
   public void process() {
       // 无锁操作
       synchronized(this) {
           // 只保护必要部分
       }
   }
   ```

2. **使用并发集合**
   ```java
   // 线程安全集合（推荐）
   ConcurrentHashMap<String, Object> concurrentMap = new ConcurrentHashMap<>();
   CopyOnWriteArrayList<String> copyOnWriteList = new CopyOnWriteArrayList<>();
   
   // 同步包装器（性能较差）
   Collections.synchronizedMap(new HashMap<>());
   ```

3. **避免死锁**
   ```java
   // 1. 按固定顺序获取锁
   public void transfer(Account from, Account to, int amount) {
       Account first = from.id < to.id ? from : to;
       Account second = from.id < to.id ? to : from;
       
       synchronized(first) {
           synchronized(second) {
               // 转账操作
           }
       }
   }
   
   // 2. 使用tryLock超时
   public boolean transferWithTimeout(ReentrantLock lock1, ReentrantLock lock2) {
       while (true) {
           if (lock1.tryLock()) {
               try {
                   if (lock2.tryLock(100, TimeUnit.MILLISECONDS)) {
                       try {
                           return true;
                       } finally {
                           lock2.unlock();
                       }
                   }
               } finally {
                   lock1.unlock();
               }
           }
           // 随机等待，避免活锁
           Thread.sleep((long)(Math.random() * 10));
       }
   }
   ```

### 6.2 性能优化技巧

1. **锁分离**
   ```java
   // 读写锁分离
   ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
   Lock readLock = rwLock.readLock();
   Lock writeLock = rwLock.writeLock();
   ```

2. **无锁编程**
   ```java
   // 使用原子类代替锁
   AtomicLong counter = new AtomicLong();
   
   // 累加操作（无锁）
   public long increment() {
       return counter.incrementAndGet();
   }
   
   // 高并发统计使用LongAdder
   LongAdder adder = new LongAdder();
   adder.increment();
   ```

3. **ThreadLocal避免竞争**
   ```java
   private static ThreadLocal<SimpleDateFormat> dateFormat = 
       ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));
   ```

## 七、常见问题排查

### 7.1 并发问题诊断

1. **死锁检测**
   ```bash
   # 查看线程栈
   jstack <pid>
   
   # 查找死锁信息
   Found one Java-level deadlock:
   =============================
   "Thread-1":
     waiting to lock monitor 0x00007f...
   ```

2. **性能分析工具**
   - **JConsole/VisualVM**: 监控线程状态
   - **JMC (Java Mission Control)**: 详细性能分析
   - **Arthas**: 在线诊断工具

### 7.2 常见并发Bug模式

| Bug类型 | 现象 | 解决方案 |
|---------|------|----------|
| 竞态条件 | 结果不确定，依赖执行时序 | 同步或使用原子操作 |
| 死锁 | 线程互相等待，无进展 | 按顺序获取锁，使用超时 |
| 活锁 | 线程不断重试，无进展 | 引入随机退避 |
| 线程饥饿 | 某些线程永远得不到执行 | 使用公平锁，调整优先级 |
| 内存泄漏 | 线程局部变量未清理 | 及时清理ThreadLocal |
