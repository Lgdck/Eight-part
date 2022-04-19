<a name="index">**Index**</a>

<a href="#0">Java并发应用</a>  
&emsp;<a href="#1">1. 生产者与消费者模型</a>  
&emsp;&emsp;<a href="#2">1.1. synchronize </a>  
&emsp;&emsp;&emsp;<a href="#3">1.1.1. 基于synchronize 方法</a>  
&emsp;&emsp;&emsp;<a href="#4">1.1.2. 使用synchronize 锁对象</a>  
&emsp;&emsp;&emsp;<a href="#5">1.1.3. 问题代码</a>  
&emsp;&emsp;<a href="#6">1.2. 基于ReentrantLock 结合 condition</a>  
&emsp;&emsp;<a href="#7">1.3. 基于BlockingQueue</a>  
&emsp;<a href="#8">2. 多线程顺序输出</a>  
&emsp;&emsp;<a href="#9">2.1. 基于Synchronize锁对象</a>  
&emsp;&emsp;<a href="#10">2.2. 基于Reentrant Lock </a>  
&emsp;<a href="#11">3. 线程安全的类定义</a>  
&emsp;&emsp;<a href="#12">3.1. 枚举类为什么是线程安全？</a>  
&emsp;<a href="#13">4. 单订单重复退款请求</a>  
&emsp;&emsp;<a href="#14">4.1. 分布式锁的处理方案</a>  
# <a name="0">Java并发应用</a><a style="float:right;text-decoration:none;" href="#index">[Top]</a>

## <a name="1">生产者与消费者模型</a><a style="float:right;text-decoration:none;" href="#index">[Top]</a>


### <a name="2">synchronize </a><a style="float:right;text-decoration:none;" href="#index">[Top]</a>

#### <a name="3">基于synchronize 方法</a><a style="float:right;text-decoration:none;" href="#index">[Top]</a>

```
    public synchronized void produce() throws InterruptedException {
        while (present) {
            wait();
        }
        System.out.println(Thread.currentThread() + " Producer produce meal " + count);
        count++;
        this.setPresent(true);
        notifyAll();
    }

    public synchronized void consume() throws InterruptedException {
        while (!present) {
            wait();
        }
        System.out.println(Thread.currentThread() + " consumer present meal" + count);
        this.setPresent(false);
        notifyAll();

    }
```

#### <a name="4">使用synchronize 锁对象</a><a style="float:right;text-decoration:none;" href="#index">[Top]</a>

```
    public void createByObject() throws InterruptedException {
        synchronized (this) {
            while (present) {
                wait();
            }
        }
        count++;
        System.out.println(Thread.currentThread() + " Producer produce meal " + count);
        this.setPresent(true);
        synchronized (this) {
            notifyAll();
        }
    }

    public void consumerByObject() throws InterruptedException {
        synchronized (this) {
            while (!present) {
                wait();
            }
        }
        System.out.println(Thread.currentThread() + " consumer present meal " + count);
        this.setPresent(false);
        synchronized (this) {
            notifyAll();
        }
    }
```

- 更进一步细分锁粒度，区分生产者与消费者对象。


#### <a name="5">问题代码</a><a style="float:right;text-decoration:none;" href="#index">[Top]</a>
```
    public void createByObject() throws InterruptedException {
        while (present) {
            // 轻量级锁等待的时候，如果另个线程先获取锁，并notifyAll
            // 那么可能两个线程都进入wait状态
            synchronized (this) {
                wait();
            }
        }
        count++;
        System.out.println(Thread.currentThread() + " Producer produce meal " + count);
        this.setPresent(true);
        synchronized (this) {
            notifyAll();
        }

    }

    public void consumerByObject() throws InterruptedException {
        while (!present) {
            // 轻量级锁等待的时候，如果另个线程先获取锁，并notifyAll
            // 那么可能两个线程都进入wait状态
            synchronized (this) {
                wait();
            }
        }
        System.out.println(Thread.currentThread() + " consumer present meal " + count);
        this.setPresent(false);
        synchronized (this) {
            notifyAll();
        }
    }

    // 正确做法 将synchronize放循环外
   public void createByObject() throws InterruptedException {
        synchronized (this) {
            while (present) {
                wait();
            }
        }
        ... 
    }
```


### <a name="6">基于ReentrantLock 结合 condition</a><a style="float:right;text-decoration:none;" href="#index">[Top]</a>
- 使用condition作为等待队列
```
class Consumer implements Runnable {

    private ReentrantLock lock;

    private Condition condition;

    public Consumer(ReentrantLock lock, Condition condition) {
        this.lock = lock;
        this.condition = condition;
    }

    @Override
    public void run() {

        try {
            while (!Thread.interrupted()) {
                try {
                    lock.lock();
                    while (!ProConDemo.flag){
                        condition.await();
                    }
                    System.out.println( Thread.currentThread()+ " consumer shout !!!!");
                    ProConDemo.flag = false;
                    condition.signalAll();
                }
                finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class Producer implements Runnable {

    private ReentrantLock lock;

    private Condition condition;

    public Producer(ReentrantLock lock, Condition condition) {
        this.lock = lock;
        this.condition = condition;
    }

    @Override
    public void run() {
        try {
            while (!Thread.interrupted()) {
                try {
                    lock.lock();
                    while (ProConDemo.flag){
                        condition.await();
                    }
                    System.out.println( Thread.currentThread()+ " producer shout~~~~");
                    ProConDemo.flag = true;
                    condition.signalAll();
                }
                finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

```

- 进阶：细分生产者队列与消费者队列

### <a name="7">基于BlockingQueue</a><a style="float:right;text-decoration:none;" href="#index">[Top]</a>
使用阻塞队列实现生产者与消费者模型
- 消费者：`queue.take();`
- 生产者：`queue.put(obj)`

## <a name="8">多线程顺序输出</a><a style="float:right;text-decoration:none;" href="#index">[Top]</a>
### <a name="9">基于Synchronize锁对象</a><a style="float:right;text-decoration:none;" href="#index">[Top]</a>
```
class Thread1 implements Runnable {

    private Object obj;

    public Thread1(Object obj) {
        this.obj = obj;
    }

    @Override
    public void run() {

        try {
            while (!Thread.interrupted()) {
                synchronized (obj) {
                    while (!SynchronizeObject.flag) {
                        obj.wait();
                    }
                    System.out.println(Thread.currentThread() + " this is thread1");
                    SynchronizeObject.flag = false;
                    obj.notify();
                }
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class Thread2 implements Runnable {

    private Object obj;

    public Thread2(Object obj) {
        this.obj = obj;
    }

    @Override
    public void run() {

        try {
            while (!Thread.interrupted()) {
                synchronized (obj) {
                    while (SynchronizeObject.flag) {
                        obj.wait();
                    }
                    System.out.println(Thread.currentThread() + " this is thread2~~");
                    SynchronizeObject.flag = true;
                    obj.notify();
                }
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
```


### <a name="10">基于Reentrant Lock </a><a style="float:right;text-decoration:none;" href="#index">[Top]</a>
方法1：设立一个flag，防止非公平锁抢锁输出，导致的顺序混乱问题。

方法2： 使用Reentrant Lock 公平锁
```

ReentrantLock lock = new ReentrantLock(true);

    public void run() {

        while (!Thread.interrupted()) {
            try {
                lock.lock();
                System.out.println(Thread.currentThread() + " consumer shout !!!!");
            } finally {
                lock.unlock();
            }
        }
    }
```


## <a name="11">线程安全的类定义</a><a style="float:right;text-decoration:none;" href="#index">[Top]</a>
1. 无状态的类：没有任何成员变量的类，如无任何方法的枚举类型。
2. 让类不可变	
   1. 加final关键字
   2. 不提供修改成员变量，也不提供获取成员变量方法
3. 使用volatile，保证类的可见性，不能保证线程安全。适合一写多读的场景
4. 加锁和CAS，使用synchronized、lock、原子变量AtomicInteger等
   1. 如StringBuffer 修改的方法都使用synchronize修饰。
   2. 如concurrentHashMap 使用自旋加CAS修改。
   3. 使用Atomic包的基本类型，如AtomicInteger、AtomicReference、AtmoicStampReference修饰变量。
   

### <a name="12">枚举类为什么是线程安全？</a><a style="float:right;text-decoration:none;" href="#index">[Top]</a>

普通的一个枚举类
```
public enum t {
    SPRING,SUMMER,AUTUMN,WINTER;
}
```

反编译后的代码
```
public final class T extends Enum
{
    private T(String s, int i)
    {
        super(s, i);
    }
    public static T[] values()
    {
        T at[];
        int i;
        T at1[];
        System.arraycopy(at = ENUM$VALUES, 0, at1 = new T[i = at.length], 0, i);
        return at1;
    }

    public static T valueOf(String s)
    {
        return (T)Enum.valueOf(demo/T, s);
    }

    public static final T SPRING;
    public static final T SUMMER;
    public static final T AUTUMN;
    public static final T WINTER;
    private static final T ENUM$VALUES[];
    static
    {
        SPRING = new T("SPRING", 0);
        SUMMER = new T("SUMMER", 1);
        AUTUMN = new T("AUTUMN", 2);
        WINTER = new T("WINTER", 3);
        ENUM$VALUES = (new T[] {
            SPRING, SUMMER, AUTUMN, WINTER
        });
    }
}
```

1. `public final class T extends Enum`，说明，该类是继承了Enum类的，同时final关键字告诉我们，这个类也是不能被继承的。
2. 类中的几个属性和方法都是static final类型的，说明static类型的属性会在类被加载之后被初始化便不可修改。
> 创建一个enum类型是线程安全的。


- 相关资料：https://www.cnblogs.com/z00377750/p/9177097.html

## <a name="13">单订单重复退款请求</a><a style="float:right;text-decoration:none;" href="#index">[Top]</a>
1. synchronize修饰退款方法。 
2. 缩小synchronize锁范围，使用对象锁。对象锁，创建弱引用的一个订单ID对象，放到同一的锁对象资源池中。
   - 清理锁对象可以使用守护线程的方法，基于Unsafe的包操作去清除。
3. 分布式应用，使用分布式锁来处理。


### <a name="14">分布式锁的处理方案</a><a style="float:right;text-decoration:none;" href="#index">[Top]</a>

1. 数据库锁，数据库乐观锁，数据库悲观锁。

2. redis 锁 或者 ZooKeeper锁

3. 使用消息队列顺序消费，保证不重复退款