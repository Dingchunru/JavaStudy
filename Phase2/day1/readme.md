# Spring核心：IoC、AOP、事务管理及设计模式应用

## 一、IoC（控制反转）容器

### 1.1 核心概念

**控制反转（Inversion of Control）** 是一种设计原则，它将对象创建和依赖绑定的控制权从应用程序代码转移到外部容器。

```java
// 传统方式：程序控制对象的创建
public class UserService {
    private UserRepository repository = new UserRepositoryImpl();
}

// IoC方式：容器控制对象的创建
public class UserService {
    @Autowired
    private UserRepository repository;
}
```

### 1.2 IoC容器实现

#### BeanFactory - 基础容器
```java
// 基本功能，延迟加载，适用于资源受限环境
BeanFactory factory = new XmlBeanFactory(
    new ClassPathResource("beans.xml")
);
MyBean bean = factory.getBean(MyBean.class);
```

#### ApplicationContext - 高级容器（推荐）
```java
// 提供企业级功能，包括：
// 1. 国际化支持
// 2. 事件发布机制
// 3. AOP集成
// 4. 资源访问
ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");
```

### 1.3 Bean生命周期

```
1. 实例化 Instantiation
2. 属性填充 Populate properties
3. 设置BeanName Aware接口
4. 设置BeanFactory Aware接口
5. 前置初始化 BeanPostProcessor.postProcessBeforeInitialization()
6. 初始化 InitializingBean.afterPropertiesSet()
7. 自定义初始化方法 init-method
8. 后置初始化 BeanPostProcessor.postProcessAfterInitialization()
9. 使用中 Ready for use
10. 销毁 DisposableBean.destroy()
11. 自定义销毁方法 destroy-method
```

### 1.4 依赖注入方式

#### 构造函数注入
```java
@Component
public class OrderService {
    private final PaymentService paymentService;
    
    @Autowired // Spring 4.3+ 可省略
    public OrderService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }
}
```

#### Setter注入
```java
@Component
public class UserService {
    private UserRepository repository;
    
    @Autowired
    public void setRepository(UserRepository repository) {
        this.repository = repository;
    }
}
```

#### 字段注入（不推荐生产环境使用）
```java
@Component
public class ProductService {
    @Autowired
    private InventoryService inventoryService;
}
```

### 1.5 高级配置特性

#### Profile环境隔离
```java
@Configuration
@Profile("dev")
public class DevConfig {
    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .build();
    }
}

@Configuration
@Profile("prod")
public class ProdConfig {
    @Bean
    public DataSource dataSource() {
        // 生产环境数据源配置
    }
}
```

#### Conditional条件化Bean
```java
@Configuration
public class DatabaseConfig {
    
    @Bean
    @ConditionalOnClass(name = "com.mysql.jdbc.Driver")
    @ConditionalOnProperty(name = "db.type", havingValue = "mysql")
    public DataSource mysqlDataSource() {
        // MySQL数据源
    }
}
```

## 二、AOP（面向切面编程）

### 2.1 AOP核心概念

```java
// 连接点 JoinPoint：程序执行过程中的某个点
// 切点 Pointcut：匹配连接点的表达式
// 通知 Advice：在特定连接点执行的动作
// 切面 Aspect：切点+通知
// 织入 Weaving：将切面应用到目标对象的过程
```

### 2.2 通知类型

```java
@Aspect
@Component
public class LoggingAspect {
    
    // 前置通知
    @Before("execution(* com.example.service.*.*(..))")
    public void logBefore(JoinPoint joinPoint) {
        System.out.println("Before method: " + joinPoint.getSignature().getName());
    }
    
    // 后置通知（无论如何都执行）
    @After("execution(* com.example.service.*.*(..))")
    public void logAfter(JoinPoint joinPoint) {
        System.out.println("After method: " + joinPoint.getSignature().getName());
    }
    
    // 返回通知
    @AfterReturning(
        pointcut = "execution(* com.example.service.*.*(..))",
        returning = "result"
    )
    public void logAfterReturning(JoinPoint joinPoint, Object result) {
        System.out.println("Method returned: " + result);
    }
    
    // 异常通知
    @AfterThrowing(
        pointcut = "execution(* com.example.service.*.*(..))",
        throwing = "error"
    )
    public void logAfterThrowing(JoinPoint joinPoint, Throwable error) {
        System.out.println("Exception in method: " + error);
    }
    
    // 环绕通知
    @Around("execution(* com.example.service.*.*(..))")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long elapsedTime = System.currentTimeMillis() - start;
            System.out.println("Method execution time: " + elapsedTime + "ms");
            return result;
        } catch (IllegalArgumentException e) {
            System.out.println("Illegal argument: " + Arrays.toString(joinPoint.getArgs()));
            throw e;
        }
    }
}
```

### 2.3 切点表达式语法

```java
@Aspect
public class SystemArchitecture {
    
    // 匹配com.example.service包及其子包的所有方法
    @Pointcut("within(com.example.service..*)")
    public void inServiceLayer() {}
    
    // 匹配所有Service结尾的类的所有方法
    @Pointcut("execution(* *..*Service.*(..))")
    public void businessService() {}
    
    // 匹配带有@Transactional注解的方法
    @Pointcut("@annotation(org.springframework.transaction.annotation.Transactional)")
    public void transactionalMethod() {}
    
    // 组合切点
    @Pointcut("businessService() && transactionalMethod()")
    public void businessServiceWithTransaction() {}
    
    // 匹配带有特定注解的类
    @Pointcut("@within(org.springframework.stereotype.Repository)")
    public void repositoryClassMethods() {}
}
```

### 2.4 AOP实现原理

#### JDK动态代理（接口代理）
```java
public class JdkDynamicProxyDemo {
    public static void main(String[] args) {
        UserService target = new UserServiceImpl();
        
        UserService proxy = (UserService) Proxy.newProxyInstance(
            UserService.class.getClassLoader(),
            new Class[]{UserService.class},
            new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    System.out.println("Before method: " + method.getName());
                    Object result = method.invoke(target, args);
                    System.out.println("After method: " + method.getName());
                    return result;
                }
            }
        );
        
        proxy.saveUser();
    }
}
```

#### CGLIB代理（类代理）
```java
public class CglibProxyDemo {
    public static void main(String[] args) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(UserServiceImpl.class);
        enhancer.setCallback(new MethodInterceptor() {
            @Override
            public Object intercept(Object obj, Method method, Object[] args, 
                                  MethodProxy proxy) throws Throwable {
                System.out.println("Before method: " + method.getName());
                Object result = proxy.invokeSuper(obj, args);
                System.out.println("After method: " + method.getName());
                return result;
            }
        });
        
        UserService proxy = (UserService) enhancer.create();
        proxy.saveUser();
    }
}
```

## 三、事务管理

### 3.1 Spring事务核心接口

```java
// PlatformTransactionManager - 事务管理器接口
public interface PlatformTransactionManager {
    TransactionStatus getTransaction(@Nullable TransactionDefinition definition)
        throws TransactionException;
    
    void commit(TransactionStatus status) throws TransactionException;
    
    void rollback(TransactionStatus status) throws TransactionException;
}

// 常见实现类：
// 1. DataSourceTransactionManager - JDBC事务
// 2. JpaTransactionManager - JPA事务
// 3. HibernateTransactionManager - Hibernate事务
// 4. JtaTransactionManager - JTA分布式事务
```

### 3.2 声明式事务配置

#### XML配置方式
```xml
<!-- 配置事务管理器 -->
<bean id="transactionManager"
      class="org.springframework.jdbc.datasource.DataSourceTransactionManager">
    <property name="dataSource" ref="dataSource"/>
</bean>

<!-- 开启注解驱动的事务管理 -->
<tx:annotation-driven transaction-manager="transactionManager"/>

<!-- 或使用AOP配置事务 -->
<tx:advice id="txAdvice" transaction-manager="transactionManager">
    <tx:attributes>
        <tx:method name="get*" read-only="true"/>
        <tx:method name="find*" read-only="true"/>
        <tx:method name="*" propagation="REQUIRED"/>
    </tx:attributes>
</tx:advice>

<aop:config>
    <aop:pointcut id="serviceOperation"
                  expression="execution(* com.example.service.*.*(..))"/>
    <aop:advisor advice-ref="txAdvice" pointcut-ref="serviceOperation"/>
</aop:config>
```

#### 注解配置方式
```java
@Configuration
@EnableTransactionManagement
public class TransactionConfig {
    
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}

@Service
@Transactional
public class OrderService {
    
    @Transactional(
        readOnly = false,
        propagation = Propagation.REQUIRED,
        isolation = Isolation.DEFAULT,
        timeout = 30,
        rollbackFor = {SQLException.class, DataAccessException.class},
        noRollbackFor = {IllegalArgumentException.class}
    )
    public Order createOrder(Order order) {
        // 业务逻辑
    }
}
```

### 3.3 事务传播行为

```java
public enum Propagation {
    REQUIRED,        // 支持当前事务，不存在则新建（默认）
    SUPPORTS,        // 支持当前事务，不存在则以非事务方式执行
    MANDATORY,       // 必须存在事务，否则抛出异常
    REQUIRES_NEW,    // 新建事务，挂起当前事务
    NOT_SUPPORTED,   // 非事务方式执行，挂起当前事务
    NEVER,           // 非事务方式执行，存在事务则抛出异常
    NESTED           // 嵌套事务
}

// 使用示例
@Service
public class OrderService {
    
    @Transactional(propagation = Propagation.REQUIRED)
    public void processOrder(Order order) {
        // 主事务
        saveOrder(order);
        
        try {
            // 独立事务执行
            inventoryService.updateInventory(order.getItems());
        } catch (Exception e) {
            // 库存更新失败不影响订单保存
            log.error("Inventory update failed", e);
        }
        
        // 继续在主事务中执行
        notifyCustomer(order);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateInventory(List<Item> items) {
        // 这个操作在独立事务中执行
    }
}
```

### 3.4 事务隔离级别

```java
public enum Isolation {
    DEFAULT(-1),          // 使用数据库默认隔离级别
    READ_UNCOMMITTED(1),  // 读未提交（可能脏读）
    READ_COMMITTED(2),    // 读已提交（Oracle默认）
    REPEATABLE_READ(4),   // 可重复读（MySQL默认）
    SERIALIZABLE(8)       // 串行化
}

// 使用示例
@Service
public class AccountService {
    
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void transferMoney(Long fromAccountId, Long toAccountId, BigDecimal amount) {
        // 避免脏读，确保读取已提交的数据
        Account fromAccount = accountRepository.findById(fromAccountId);
        Account toAccount = accountRepository.findById(toAccountId);
        
        // 转账逻辑
    }
}
```

### 3.5 编程式事务

```java
@Service
public class ManualTransactionService {
    
    private final PlatformTransactionManager transactionManager;
    private final TransactionTemplate transactionTemplate;
    
    public ManualTransactionService(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }
    
    // 使用TransactionTemplate
    public void executeWithTransactionTemplate() {
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                try {
                    // 业务逻辑
                    businessOperation1();
                    businessOperation2();
                } catch (Exception e) {
                    status.setRollbackOnly(); // 标记回滚
                }
            }
        });
    }
    
    // 使用PlatformTransactionManager
    public void executeWithTransactionManager() {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setPropagationBehavior(Propagation.REQUIRED.value());
        definition.setIsolationLevel(Isolation.READ_COMMITTED.value());
        definition.setTimeout(30);
        
        TransactionStatus status = transactionManager.getTransaction(definition);
        
        try {
            // 业务逻辑
            businessOperation1();
            businessOperation2();
            
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw e;
        }
    }
}
```

## 四、设计模式在Spring中的应用

### 4.1 工厂模式

#### BeanFactory - 工厂模式的典型实现
```java
// 1. 简单工厂模式
public class BeanFactory {
    public Object getBean(String name) {
        if ("userService".equals(name)) {
            return new UserServiceImpl();
        } else if ("orderService".equals(name)) {
            return new OrderServiceImpl();
        }
        throw new IllegalArgumentException("Unknown bean name: " + name);
    }
}

// 2. 工厂方法模式
public interface FactoryBean<T> {
    T getObject() throws Exception;
    Class<?> getObjectType();
    boolean isSingleton();
}

// 示例：MyBatis的SqlSessionFactoryBean
public class SqlSessionFactoryBean implements FactoryBean<SqlSessionFactory> {
    private DataSource dataSource;
    
    @Override
    public SqlSessionFactory getObject() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        return factoryBean.getObject();
    }
}
```

### 4.2 单例模式

```java
// Spring管理的Bean默认是单例的
@Component
@Scope("singleton") // 显式声明单例
public class SingletonService {
    // 单例Bean
}

// 实现原理：DefaultSingletonBeanRegistry
public class DefaultSingletonBeanRegistry {
    private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);
    
    public Object getSingleton(String beanName) {
        Object singletonObject = this.singletonObjects.get(beanName);
        if (singletonObject == null) {
            synchronized (this.singletonObjects) {
                singletonObject = this.singletonObjects.get(beanName);
                if (singletonObject == null) {
                    // 创建单例实例
                    singletonObject = createBean(beanName);
                    this.singletonObjects.put(beanName, singletonObject);
                }
            }
        }
        return singletonObject;
    }
}
```

### 4.3 代理模式

```java
// Spring AOP基于代理模式实现
public interface UserService {
    void saveUser(User user);
}

public class UserServiceImpl implements UserService {
    @Override
    public void saveUser(User user) {
        // 业务逻辑
    }
}

// 代理类
public class UserServiceProxy implements UserService {
    private UserService target;
    private TransactionManager transactionManager;
    
    public UserServiceProxy(UserService target) {
        this.target = target;
    }
    
    @Override
    public void saveUser(User user) {
        try {
            transactionManager.beginTransaction();
            target.saveUser(user); // 委托给真实对象
            transactionManager.commit();
        } catch (Exception e) {
            transactionManager.rollback();
            throw e;
        }
    }
}
```

### 4.4 模板方法模式

```java
// JdbcTemplate是模板方法模式的典型应用
public abstract class JdbcTemplate {
    
    public <T> T execute(ConnectionCallback<T> action) throws DataAccessException {
        Connection con = DataSourceUtils.getConnection(obtainDataSource());
        try {
            // 设置连接属性（模板方法的一部分）
            Connection conToUse = createConnectionProxy(con);
            // 执行用户代码（钩子方法）
            return action.doInConnection(conToUse);
        } catch (SQLException ex) {
            // 异常处理（模板方法的一部分）
            throw translateException("ConnectionCallback", ex);
        } finally {
            // 资源清理（模板方法的一部分）
            DataSourceUtils.releaseConnection(con, getDataSource());
        }
    }
    
    public <T> T query(String sql, ResultSetExtractor<T> rse) throws DataAccessException {
        return execute(new StatementCallback<T>() {
            @Override
            public T doInStatement(Statement stmt) throws SQLException {
                ResultSet rs = null;
                try {
                    rs = stmt.executeQuery(sql);
                    // 调用用户提供的结果集处理器
                    return rse.extractData(rs);
                } finally {
                    JdbcUtils.closeResultSet(rs);
                }
            }
        });
    }
}
```

### 4.5 观察者模式

```java
// Spring的事件机制
// 1. 定义事件
public class OrderCreatedEvent extends ApplicationEvent {
    private Order order;
    
    public OrderCreatedEvent(Object source, Order order) {
        super(source);
        this.order = order;
    }
    
    public Order getOrder() {
        return order;
    }
}

// 2. 发布事件
@Service
public class OrderService {
    private final ApplicationEventPublisher eventPublisher;
    
    @Transactional
    public Order createOrder(Order order) {
        // 保存订单
        order = orderRepository.save(order);
        
        // 发布事件
        eventPublisher.publishEvent(new OrderCreatedEvent(this, order));
        
        return order;
    }
}

// 3. 监听事件
@Component
public class EmailNotificationListener {
    
    @EventListener
    @Async // 异步处理
    public void handleOrderCreatedEvent(OrderCreatedEvent event) {
        // 发送邮件通知
        sendEmail(event.getOrder().getCustomerEmail(), 
                 "Your order has been created");
    }
    
    @EventListener
    @Order(1) // 指定监听顺序
    public void handleOrderCreatedEventFirst(OrderCreatedEvent event) {
        // 第一个处理
    }
    
    @EventListener
    @Order(2)
    public void handleOrderCreatedEventSecond(OrderCreatedEvent event) {
        // 第二个处理
    }
}
```

### 4.6 策略模式

```java
// Spring Resource接口是策略模式的典型应用
public interface Resource extends InputStreamSource {
    boolean exists();
    boolean isReadable();
    boolean isOpen();
    URL getURL() throws IOException;
    File getFile() throws IOException;
    Resource createRelative(String relativePath) throws IOException;
    String getFilename();
    String getDescription();
}

// 不同策略实现
public class ClassPathResource implements Resource {
    // 从类路径加载资源
}

public class FileSystemResource implements Resource {
    // 从文件系统加载资源
}

public class UrlResource implements Resource {
    // 从URL加载资源
}

public class ServletContextResource implements Resource {
    // 从Servlet上下文加载资源
}

// 使用策略模式
public class ResourceLoader {
    public Resource getResource(String location) {
        if (location.startsWith("classpath:")) {
            return new ClassPathResource(location.substring("classpath:".length()));
        } else if (location.startsWith("file:")) {
            return new FileSystemResource(location.substring("file:".length()));
        } else if (location.startsWith("http:") || location.startsWith("https:")) {
            return new UrlResource(location);
        } else {
            // 根据上下文选择默认策略
            return new DefaultResourceLoader().getResource(location);
        }
    }
}
```

### 4.7 适配器模式

```java
// HandlerAdapter是适配器模式的典型应用
public interface HandlerAdapter {
    boolean supports(Object handler);
    ModelAndView handle(HttpServletRequest request, 
                       HttpServletResponse response, 
                       Object handler) throws Exception;
}

// 不同处理器的适配器
public class SimpleControllerHandlerAdapter implements HandlerAdapter {
    @Override
    public boolean supports(Object handler) {
        return (handler instanceof Controller);
    }
    
    @Override
    public ModelAndView handle(HttpServletRequest request, 
                              HttpServletResponse response, 
                              Object handler) throws Exception {
        return ((Controller) handler).handleRequest(request, response);
    }
}

public class HttpRequestHandlerAdapter implements HandlerAdapter {
    @Override
    public boolean supports(Object handler) {
        return (handler instanceof HttpRequestHandler);
    }
    
    @Override
    public ModelAndView handle(HttpServletRequest request, 
                              HttpServletResponse response, 
                              Object handler) throws Exception {
        ((HttpRequestHandler) handler).handleRequest(request, response);
        return null;
    }
}

public class RequestMappingHandlerAdapter implements HandlerAdapter {
    @Override
    public boolean supports(Object handler) {
        return (handler instanceof HandlerMethod);
    }
    
    @Override
    public ModelAndView handle(HttpServletRequest request, 
                              HttpServletResponse response, 
                              Object handler) throws Exception {
        // 处理@Controller注解的方法
        return invokeHandlerMethod(request, response, (HandlerMethod) handler);
    }
}
```

### 4.8 装饰器模式

```java
// Spring中的装饰器模式应用
// 1. HttpServletRequest装饰器
public class ServletRequestWrapper implements HttpServletRequest {
    private HttpServletRequest request;
    
    public ServletRequestWrapper(HttpServletRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        this.request = request;
    }
    
    @Override
    public Object getAttribute(String name) {
        return this.request.getAttribute(name);
    }
    
    @Override
    public void setAttribute(String name, Object o) {
        this.request.setAttribute(name, o);
    }
    
    // 其他方法委托给被装饰的request
}

// 2. 缓存装饰器
public class CachingDecorator implements DataService {
    private final DataService delegate;
    private final Cache cache;
    
    public CachingDecorator(DataService delegate) {
        this.delegate = delegate;
        this.cache = new ConcurrentHashMap<>();
    }
    
    @Override
    public Data getData(String key) {
        Data data = cache.get(key);
        if (data == null) {
            data = delegate.getData(key);
            cache.put(key, data);
        }
        return data;
    }
}

// 3. 事务装饰器
@Component
public class TransactionalDecorator implements OrderService {
    private final OrderService delegate;
    private final PlatformTransactionManager transactionManager;
    
    @Override
    public Order createOrder(Order order) {
        TransactionStatus status = transactionManager.getTransaction(
            new DefaultTransactionDefinition());
        try {
            Order result = delegate.createOrder(order);
            transactionManager.commit(status);
            return result;
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw e;
        }
    }
}
```

## 五、Spring核心设计原则

### 5.1 依赖倒置原则（DIP）
```java
// 高层模块不应依赖低层模块，二者都应依赖抽象
public interface Repository<T> {
    T save(T entity);
    Optional<T> findById(Long id);
}

// 高层模块依赖抽象
@Service
public class UserService {
    private final Repository<User> userRepository;
    
    public UserService(Repository<User> userRepository) {
        this.userRepository = userRepository;
    }
}

// 低层模块实现抽象
@Repository
public class JpaUserRepository implements Repository<User> {
    // JPA实现
}

@Repository  
public class MyBatisUserRepository implements Repository<User> {
    // MyBatis实现
}
```

### 5.2 开放封闭原则（OCP）
```java
// 对扩展开放，对修改封闭
public interface PaymentStrategy {
    void pay(BigDecimal amount);
}

@Component
public class CreditCardPayment implements PaymentStrategy {
    @Override
    public void pay(BigDecimal amount) {
        // 信用卡支付逻辑
    }
}

@Component  
public class PayPalPayment implements PaymentStrategy {
    @Override
    public void pay(BigDecimal amount) {
        // PayPal支付逻辑
    }
}

@Service
public class PaymentService {
    private final Map<String, PaymentStrategy> strategies;
    
    public PaymentService(List<PaymentStrategy> strategyList) {
        strategies = strategyList.stream()
            .collect(Collectors.toMap(
                s -> s.getClass().getSimpleName(),
                Function.identity()
            ));
    }
    
    public void processPayment(String type, BigDecimal amount) {
        PaymentStrategy strategy = strategies.get(type);
        if (strategy != null) {
            strategy.pay(amount);
        }
    }
}
```

### 5.3 Spring核心设计理念总结

1. **轻量级与非侵入性**
   - POJO开发
   - 最小化API依赖

2. **依赖注入与松耦合**
   - 基于接口编程
   - 减少组件间的直接依赖

3. **声明式编程**
   - 注解配置
   - AOP面向切面

4. **一致的数据访问**
   - 统一的异常体系
   - 模板方法模式

5. **灵活的可扩展性**
   - 大量的扩展点
   - 良好的设计模式应用

## 六、最佳实践与常见问题

### 6.1 性能优化建议

```java
// 1. 合理使用Bean作用域
@Component
@Scope("prototype") // 需要时创建新实例
public class ExpensiveResource {
    // 资源密集型对象
}

// 2. 懒加载配置
@Configuration
public class AppConfig {
    
    @Bean
    @Lazy  // 延迟初始化
    public DataSource dataSource() {
        return new HikariDataSource();
    }
}

// 3. 避免循环依赖
// 不良设计：
@Service
public class ServiceA {
    @Autowired
    private ServiceB serviceB;
}

@Service  
public class ServiceB {
    @Autowired
    private ServiceA serviceA; // 循环依赖
}

// 解决方案：
// 1) 使用Setter注入
// 2) 使用@Lazy注解
// 3) 重构设计，提取公共逻辑
```

### 6.2 事务管理最佳实践

```java
// 1. 指定明确的回滚规则
@Transactional(rollbackFor = {BusinessException.class, SQLException.class})
public void businessMethod() {
    // 业务逻辑
}

// 2. 避免在事务方法中处理耗时操作
@Transactional
public void processOrder(Order order) {
    // 数据库操作
    orderRepository.save(order);
    
    // 将耗时操作移出事务
    CompletableFuture.runAsync(() -> {
        sendEmailNotification(order);
        generateReport(order);
    });
}

// 3. 合理使用只读事务
@Transactional(readOnly = true)
public List<Order> findOrdersByCustomer(Long customerId) {
    // 只读查询，优化数据库连接
    return orderRepository.findByCustomerId(customerId);
}
```

### 6.3 调试与监控

```java
// 1. 启用Spring Boot Actuator
@Configuration
@EnableAutoConfiguration
public class MonitoringConfig {
    // 通过/actuator端点监控应用状态
}

// 2. 自定义切面进行性能监控
@Aspect
@Component
@Slf4j
public class PerformanceMonitorAspect {
    
    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object monitorTransactionPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        try {
            return joinPoint.proceed();
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            if (executionTime > 1000) { // 超过1秒记录警告
                log.warn("Transaction method {} execution time: {}ms", 
                        joinPoint.getSignature(), executionTime);
            }
        }
    }
}
```