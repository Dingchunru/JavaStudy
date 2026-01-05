// 基于 synchronized + wait()/notifyAll()

import java.util.LinkedList;
import java.util.Queue;

// 缓冲区（共享资源）
class Buffer {
    // 缓冲区最大容量，阻塞队列
    private final int MAx_CAPACITY=5;
    private final Queue<Integer> queue=new LinkedList<>();

    // 生产者方法（向缓冲区写入数据）
    // synchronized保证同一时间只能有同一个进程访问该方法，
    public synchronized void produce(int data) throws InterruptedException {
        while(queue.size()==MAx_CAPACITY){
            System.out.println("缓冲区已满，生产者["+Thread.currentThread().getName()+"]生产："+data+"，缓冲区大小："+queue.size());
            wait();     //释放锁，线程进入等待状态
        }
        queue.add(data);
        System.out.println("生产者["+Thread.currentThread().getName()+ "]生产：" + data + "，缓冲区大小：" + queue.size());
        notifyAll();
    }

    // 消费者方法（从缓冲区读出数据）
    public synchronized int consume() throws InterruptedException {
        while(queue.isEmpty()){
            System.out.println("缓冲区为空，消费者[" + Thread.currentThread().getName() + "]阻塞");
            wait();     // 缓冲区为空则等待
        }
        int data=queue.poll();
        System.out.println("消费者[" + Thread.currentThread().getName() + "]消费：" + data + "，缓冲区大小：" + queue.size());
        notifyAll();
        return data;
    }
}

// 生产者线程
class Producer implements Runnable {
    private final Buffer buffer;
    private int data=0;

    public Producer(Buffer buffer){
        this.buffer=buffer;
    }

    @Override
    public void run(){
        try{
            while(true){
                buffer.produce(data);
                data++;
                Thread.sleep(500);
            }
        }
        catch(InterruptedException e){
            Thread.currentThread().interrupt();
            System.out.println("生产者进程中断");
        }
    }
}

// 消费者线程（实现Runnable接口）
class Consumer implements Runnable {
    private final Buffer buffer;
    
    public Consumer(Buffer buffer){
        this.buffer=buffer;
    }

    @Override
    public void run(){
        try{
            while(true){
                buffer.consume();
                Thread.sleep(1000);
            }
        }
        catch(InterruptedException e){
            Thread.currentThread().interrupt();
            System.out.println("消费者进程中断");
        }
    }
}

// 测试类（主线程）
public class ProducerConsumer {
    public static void main(String[] args) {
        //  声明缓冲区
        Buffer buffer=new Buffer();
        
        // 通过构造器创建线程
        Thread producer1=new Thread(new Producer(buffer),"p1");
        Thread producer2=new Thread(new Producer(buffer),"p1");
        Thread consumer1=new Thread(new Consumer(buffer),"c1");
        Thread consumer2=new Thread(new Consumer(buffer),"c2");

        //启动所有超线程
        producer1.start();
        producer2.start();
        consumer1.start();
        consumer2.start();

        //运行10000ms
        try{
            Thread.sleep(10000);
        }
        catch(InterruptedException e){
            e.printStackTrace();
        }
        producer1.interrupt();
        producer2.interrupt();
        consumer1.interrupt();
        consumer2.interrupt();
    }
}