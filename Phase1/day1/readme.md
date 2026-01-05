# Java数据类型与核心接口详解

## 一、基本数据类型（8种）

| 类型 | 大小 | 范围/默认值 | 说明 |
|------|------|------------|------|
| **byte** | 1字节 | -128 ~ 127 | 最小整型 |
| **short** | 2字节 | -32,768 ~ 32,767 | 短整型 |
| **int** | 4字节 | -2³¹ ~ 2³¹-1 | 默认整型 |
| **long** | 8字节 | -2⁶³ ~ 2⁶³-1 | 长整型，后缀L |
| **float** | 4字节 | ±3.4E38 | 单精度浮点，后缀F |
| **double** | 8字节 | ±1.8E308 | 双精度浮点，默认浮点 |
| **char** | 2字节 | '\u0000' ~ '\uffff' | Unicode字符 |
| **boolean** | 未明确定义 | true/false | 布尔值 |

**特点：**
- 直接存储在栈内存
- 性能高，无方法调用
- 默认值（类变量）：数字类型为0，boolean为false，char为'\u0000'

## 二、引用数据类型

### 1. 类（Class）
- **内置类**：
  - `String`、`Object`
  - 包装类：`Integer`、`Boolean`等
  - 集合类：`ArrayList`、`HashMap`等
- **自定义类**：开发者定义的类

### 2. 接口（Interface）
- **内置接口**：`List`、`Map`、`Runnable`、`Serializable`等
- **自定义接口**：开发者定义的接口

### 3. 数组（Array）
- 基本类型数组：`int[]`、`char[]`、`boolean[]`
- 引用类型数组：`String[]`、`Student[]`
- 多维数组：`int[][]`、`String[][]`

### 4. 枚举（Enum）
### 5. 注解（Annotation）
### 6. 泛型类型（Type Parameter）

---

## 三、String详解

### 核心特性：不可变性
```java
String str = "Hello";
str = str + " World";  // 创建新对象，原对象不变
```

### 创建方式对比
| 方式 | 代码示例 | 存储位置 | 对象复用 |
|------|----------|----------|----------|
| 字面量 | `String s1 = "hello"` | 字符串常量池 | 复用已有对象 |
| new创建 | `String s2 = new String("hello")` | 堆内存 | 新建对象 |

### 常用方法
```java
// 比较内容
str1.equals(str2);

// 计算哈希值
str.hashCode();

// 字符串操作（返回新对象）
String sub = str.substring(1);      // 截取
String[] arr = str.split(",");      // 分割
String replaced = str.replace("a", "b"); // 替换

// 常量池操作
String interned = str.intern();     // 入池并返回引用
```

### 性能优化
- **适用**：字符串内容不频繁变化，需要线程安全
- **不适用**：频繁字符串拼接/修改（产生大量临时对象）
- **替代方案**：
  - `StringBuilder`：单线程环境，性能最高
  - `StringBuffer`：多线程环境，线程安全

---

## 四、Integer详解

### 创建方式
```java
// 方式1：new创建（不推荐，跳过缓存）
Integer i1 = new Integer(100);

// 方式2：valueOf()（推荐，使用缓存池）
Integer i2 = Integer.valueOf(100);

// 方式3：自动装箱（底层调用valueOf）
Integer i3 = 100;  // Integer.valueOf(100)
```

### 缓存池机制
- 范围：-128 ~ 127
- 此范围内的Integer对象复用，之外的创建新对象
- 可通过JVM参数调整上限：`-XX:AutoBoxCacheMax=<size>`

### 常用方法
```java
int val = i.intValue();                 // 拆箱
int num = Integer.parseInt("123");      // 字符串转int
String str = Integer.toString(123);     // int转字符串
String binary = Integer.toBinaryString(5); // 十进制转二进制
int result = Integer.compare(5, 10);    // 比较：-1, 0, 1
```

### 自动装箱/拆箱
```java
// 编译时自动转换
Integer autoBoxed = 100;           // 装箱：Integer.valueOf(100)
int autoUnboxed = autoBoxed + 1;   // 拆箱：autoBoxed.intValue() + 1
```

### 线程安全性
- **天然线程安全**：`value`是`private final int`，不可变
- 多线程访问时只能创建新对象，不会修改已有对象

---

## 五、核心接口详解

### 1. List接口（有序、可重复集合）

**核心方法：**
```java
E get(int index);                    // 获取元素
E set(int index, E element);         // 设置元素
void add(int index, E element);      // 插入元素
E remove(int index);                 // 删除元素
int indexOf(Object o);               // 查找索引
List<E> subList(int from, int to);   // 获取子列表
```

**实现类线程安全对比：**
| 实现类 | 线程安全 | 底层结构 | 适用场景 |
|--------|----------|----------|----------|
| `ArrayList` | ❌ 不安全 | 动态数组 | 随机访问频繁 |
| `LinkedList` | ❌ 不安全 | 双向链表 | 频繁插入删除 |
| `Vector` | ✅ 安全 | 动态数组 | 历史遗留，不推荐 |
| `CopyOnWriteArrayList` | ✅ 安全 | 写时复制数组 | 读多写少 |

---

### 2. Map接口（键值对映射）

**核心方法：**
```java
V put(K key, V value);                // 添加/修改
V get(Object key);                    // 获取值
V remove(Object key);                 // 删除
boolean containsKey(Object key);      // 判断键是否存在
Set<K> keySet();                      // 获取键集合
Collection<V> values();               // 获取值集合
Set<Map.Entry<K,V>> entrySet();       // 获取键值对集合
```

**实现类线程安全对比：**
| 实现类 | 线程安全 | 底层结构 | 特点 |
|--------|----------|----------|------|
| `HashMap` | ❌ 不安全 | 数组+链表/红黑树 | 最常用，JDK8优化 |
| `LinkedHashMap` | ❌ 不安全 | 哈希表+双向链表 | 保持插入顺序 |
| `TreeMap` | ❌ 不安全 | 红黑树 | 键有序 |
| `Hashtable` | ✅ 安全 | 数组+链表 | 历史遗留，全表锁 |
| `ConcurrentHashMap` | ✅ 安全 | 分段锁/CAS+synchronized | 推荐并发使用 |

---

### 3. Runnable接口（线程任务）

**定义：**
```java
@FunctionalInterface
public interface Runnable {
    void run();  // 唯一抽象方法
}
```

**使用方式：**
```java
// 传统方式
Runnable task = new Runnable() {
    @Override
    public void run() {
        System.out.println("Task running");
    }
};

// Lambda简化
Runnable lambdaTask = () -> System.out.println("Task running");

// 启动线程
new Thread(task).start();
```

**线程安全性：**
- 仅操作局部变量 → 天然线程安全
- 操作共享资源 → 需同步控制（synchronized/Lock）

---

### 4. Serializable接口（序列化标记）

**特性：**
- 标记接口，无任何方法
- 用于对象序列化（字节流）和反序列化

**使用要点：**
```java
public class User implements Serializable {
    // 显式声明serialVersionUID（推荐）
    private static final long serialVersionUID = 1L;
    
    // transient修饰不序列化的字段
    private transient String password;
    
    private String username;
    private int age;
}
```

**序列化场景：**
- 对象持久化（文件/数据库）
- 网络传输（RPC/Socket）
- 分布式缓存

**线程安全：**
- 多线程序列化同一对象 → 需加锁
- 反序列化生成新对象 → 无冲突

---

## 六、数组（Array）

### 核心特性
- **固定长度**：创建后长度不可变
- **同类型元素**：所有元素类型相同
- **随机访问**：通过索引O(1)访问
- **引用类型**：数组本身是引用类型

### 数组类型对比

| 类型 | 示例 | 特点 | 内存布局 |
|------|------|------|----------|
| **基本类型数组** | `int[] arr = {1, 2, 3}` | 直接存储值 | 栈：引用 → 堆：[值1, 值2, 值3] |
| **引用类型数组** | `String[] arr = {"a", "b"}` | 存储引用地址 | 栈：引用 → 堆：[地址1, 地址2] → 对象 |
| **多维数组** | `int[][] arr = {{1,2}, {3,4}}` | 数组的数组 | 栈：引用 → 堆：[行数组地址] → 每行数组 |

### 定义方式
```java
// 1. 静态初始化（推荐）
int[] arr1 = {1, 2, 3};
String[] arr2 = {"a", "b", "c"};

// 2. 动态初始化
int[] arr3 = new int[5];      // 默认值：0
boolean[] arr4 = new boolean[3]; // 默认值：false

// 3. 二维数组
int[][] matrix = {{1,2}, {3,4}};
int[][] dynamic = new int[2][3]; // 2行3列
```

### 关键属性
```java
int length = arr.length;  // 获取数组长度（属性，非方法）
```

### 异常
- `ArrayIndexOutOfBoundsException`：索引越界
- `NullPointerException`：数组引用为null

---

## 七、注解（Annotation）

### 概述
- **本质**：代码元数据，不直接影响逻辑
- **应用**：框架配置、编译检查、代码生成

### 内置注解
```java
@Override      // 检查方法重写
@Deprecated    // 标记过时
@SuppressWarnings("unchecked") // 抑制警告
@FunctionalInterface // 函数式接口
```

### 自定义注解
```java
@Target(ElementType.METHOD)     // 适用目标
@Retention(RetentionPolicy.RUNTIME) // 保留策略
public @interface MyAnnotation {
    String value() default "";  // 属性
    int count() default 0;
}
```

### 保留策略
- `SOURCE`：仅源码，编译后丢弃
- `CLASS`：类文件，运行时不可见（默认）
- `RUNTIME`：运行时可通过反射获取

---

## 八、接口特性演进

### 接口版本特性
| JDK版本 | 新增特性 | 说明 |
|---------|----------|------|
| JDK7及以前 | 抽象方法、常量 | 仅定义规范 |
| JDK8 | default方法、static方法 | 接口可包含实现 |
| JDK9 | private方法 | 辅助default/static方法 |

### 示例
```java
public interface MyInterface {
    // 抽象方法（默认public abstract）
    void abstractMethod();
    
    // 默认方法（JDK8+）
    default void defaultMethod() {
        privateHelper();  // 调用私有方法
    }
    
    // 静态方法（JDK8+）
    static void staticMethod() {
        System.out.println("Static method");
    }
    
    // 私有方法（JDK9+）
    private void privateHelper() {
        System.out.println("Private helper");
    }
}
```

---

## 快速参考表

| 数据类型/接口 | 核心特点 | 线程安全 | 使用场景 |
|---------------|----------|----------|----------|
| **String** | 不可变、常量池 | ✅ 安全 | 不频繁修改的字符串 |
| **Integer** | 包装类、缓存池 | ✅ 安全 | int的对象表示 |
| **List** | 有序、可重复 | 实现类决定 | 动态数组需求 |
| **Map** | 键值对、键唯一 | 实现类决定 | 快速查找映射 |
| **Runnable** | 线程任务封装 | 任务决定 | 多线程任务定义 |
| **Serializable** | 序列化标记 | 过程相关 | 对象持久化/传输 |
| **数组** | 固定长度、同类型 | ✅ 安全 | 固定大小集合 |
| **注解** | 元数据标记 | - | 框架配置、检查 |

---

## 最佳实践

1. **字符串操作**
   - 不变字符串 → `String`
   - 频繁拼接 → `StringBuilder`（单线程）/ `StringBuffer`（多线程）

2. **数值包装**
   - 优先使用自动装箱/拆箱
   - 注意缓存池范围（-128~127）

3. **集合选择**
   - 随机访问多 → `ArrayList`
   - 频繁插入删除 → `LinkedList`
   - 并发环境 → `CopyOnWriteArrayList` / `ConcurrentHashMap`

4. **数组使用**
   - 长度固定 → 数组
   - 长度可变 → `ArrayList`
   - 多维数据 → 多维数组或集合嵌套

5. **接口设计**
   - 行为规范 → 接口
   - 版本兼容 → default方法
   - 代码复用 → private方法（JDK9+）
