# 分布式微服务核心组件深度解析

## 一、服务调用：OpenFeign 深度解析

### 1.1 核心设计思想
OpenFeign 是基于 Java 的动态代理和注解处理机制实现的声明式 REST 客户端框架，将 HTTP 请求的过程抽象为简单的接口方法调用。

```java
@FeignClient(name = "user-service", configuration = FeignConfig.class)
public interface UserServiceClient {
    
    @GetMapping("/users/{id}")
    UserDTO getUserById(@PathVariable("id") Long id);
    
    @PostMapping("/users")
    ResponseEntity<UserDTO> createUser(@RequestBody UserCreateRequest request);
    
    @PutMapping("/users/{id}")
    UserDTO updateUser(@PathVariable("id") Long id, 
                      @RequestBody UserUpdateRequest request);
}
```

### 1.2 核心组件与工作原理

#### 1.2.1 动态代理机制
```java
// Feign 动态代理创建过程
public class ReflectiveFeign extends Feign {
    
    @Override
    public <T> T newInstance(Target<T> target) {
        // 创建方法处理器映射
        Map<String, MethodHandler> nameToHandler = targetToHandlersByName.apply(target);
        Map<Method, MethodHandler> methodToHandler = new LinkedHashMap<>();
        
        // 创建JDK动态代理
        InvocationHandler handler = factory.create(target, methodToHandler);
        T proxy = (T) Proxy.newProxyInstance(
            target.type().getClassLoader(),
            new Class<?>[]{target.type()},
            handler
        );
        
        return proxy;
    }
}
```

#### 1.2.2 请求模板构建
```java
public class SynchronousMethodHandler implements MethodHandler {
    
    @Override
    public Object invoke(Object[] argv) throws Throwable {
        // 构建请求模板
        RequestTemplate template = buildTemplateFromArgs.create(argv);
        Options options = findOptions(argv);
        
        // 执行请求
        return executeAndDecode(template, options);
    }
}
```

### 1.3 高级配置与优化

#### 1.3.1 自定义编码器与解码器
```java
@Configuration
public class FeignConfig {
    
    @Bean
    public Encoder feignEncoder() {
        return new SpringEncoder(messageConverters);
    }
    
    @Bean
    public Decoder feignDecoder() {
        return new ResponseEntityDecoder(new SpringDecoder(messageConverters));
    }
    
    @Bean
    public Contract feignContract() {
        return new SpringMvcContract();
    }
}
```

#### 1.3.2 请求拦截器
```java
@Component
public class AuthRequestInterceptor implements RequestInterceptor {
    
    @Override
    public void apply(RequestTemplate template) {
        // 添加认证头
        template.header("Authorization", "Bearer " + getToken());
        
        // 添加追踪ID
        template.header("X-Trace-Id", MDC.get("traceId"));
        
        // 记录请求日志
        log.debug("Feign request to: {}", template.url());
    }
}
```

#### 1.3.3 性能优化配置
```yaml
feign:
  client:
    config:
      default:
        connectTimeout: 5000
        readTimeout: 10000
        loggerLevel: basic
        retryer: com.example.CustomRetryer
        errorDecoder: com.example.CustomErrorDecoder
        requestInterceptors:
          - com.example.AuthRequestInterceptor
          - com.example.LoggingInterceptor
  compression:
    request:
      enabled: true
      mime-types: text/xml,application/xml,application/json
      min-request-size: 2048
    response:
      enabled: true
  okhttp:
    enabled: true  # 使用OKHttp替代默认的HTTP客户端
```

### 1.4 最佳实践

#### 1.4.1 服务降级与容错
```java
@FeignClient(name = "user-service", 
             fallback = UserServiceFallback.class,
             fallbackFactory = UserServiceFallbackFactory.class)
public interface UserServiceClient {
    
    @GetMapping("/users/{id}")
    @Fallback(fallbackMethod = "getDefaultUser")
    UserDTO getUserById(@PathVariable("id") Long id);
    
    default UserDTO getDefaultUser(Long id) {
        return UserDTO.builder()
            .id(id)
            .name("默认用户")
            .status(UserStatus.DISABLED)
            .build();
    }
}

@Component
public class UserServiceFallback implements UserServiceClient {
    
    @Override
    public UserDTO getUserById(Long id) {
        return UserDTO.builder()
            .id(id)
            .name("降级用户")
            .build();
    }
}
```

#### 1.4.2 请求日志与监控
```java
@Configuration
public class FeignLogConfiguration {
    
    @Bean
    @ConditionalOnClass(Feign.class)
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }
    
    @Bean
    public Feign.Builder feignBuilder(Logger.Level loggerLevel) {
        return Feign.builder()
            .logLevel(loggerLevel)
            .logSlf4jLogger(UserServiceClient.class)
            .client(new OkHttpClient())
            .retryer(new Retryer.Default(100, 1000, 3));
    }
}
```

## 二、负载均衡：Ribbon与LoadBalancer深度解析

### 2.1 负载均衡算法演进

#### 2.1.1 传统Ribbon算法
```java
public interface IRule {
    Server choose(Object key);
    void setLoadBalancer(ILoadBalancer lb);
    ILoadBalancer getLoadBalancer();
}

// 轮询算法
public class RoundRobinRule extends AbstractLoadBalancerRule {
    
    private AtomicInteger nextServerCyclicCounter;
    
    @Override
    public Server choose(ILoadBalancer lb, Object key) {
        if (lb == null) {
            log.warn("no load balancer");
            return null;
        }
        
        Server server = null;
        int count = 0;
        while (server == null && count++ < 10) {
            List<Server> reachableServers = lb.getReachableServers();
            List<Server> allServers = lb.getAllServers();
            int upCount = reachableServers.size();
            int serverCount = allServers.size();
            
            if ((upCount == 0) || (serverCount == 0)) {
                log.warn("No up servers available from load balancer: " + lb);
                return null;
            }
            
            int nextServerIndex = incrementAndGetModulo(serverCount);
            server = allServers.get(nextServerIndex);
            
            if (server == null) {
                Thread.yield();
                continue;
            }
            
            if (server.isAlive() && (server.isReadyToServe())) {
                return (server);
            }
            
            server = null;
        }
        
        return server;
    }
}
```

#### 2.2.2 Spring Cloud LoadBalancer（新版本）
```java
@Configuration
@LoadBalancerClient(name = "user-service", 
                    configuration = LoadBalancerConfig.class)
public class LoadBalancerConfig {
    
    @Bean
    public ReactorLoadBalancer<ServiceInstance> randomLoadBalancer(
            Environment environment,
            LoadBalancerClientFactory loadBalancerClientFactory) {
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        return new RandomLoadBalancer(
            loadBalancerClientFactory.getLazyProvider(name, 
                ServiceInstanceListSupplier.class),
            name
        );
    }
    
    @Bean
    public ServiceInstanceListSupplier serviceInstanceListSupplier(
            ConfigurableApplicationContext context) {
        return ServiceInstanceListSupplier.builder()
            .withDiscoveryClient()
            .withHealthChecks()
            .build(context);
    }
}
```

### 2.2 高级负载均衡策略

#### 2.2.1 加权响应时间算法
```java
public class WeightedResponseTimeRule extends RoundRobinRule {
    
    // 服务器权重
    private volatile List<Double> accumulatedWeights = new ArrayList<>();
    
    @Override
    public Server choose(ILoadBalancer lb, Object key) {
        if (lb == null) {
            return null;
        }
        
        Server server = null;
        while (server == null) {
            List<Double> currentWeights = accumulatedWeights;
            if (Thread.interrupted()) {
                return null;
            }
            
            List<Server> allList = lb.getAllServers();
            int serverCount = allList.size();
            
            if (serverCount == 0) {
                return null;
            }
            
            double maxWeight = currentWeights.get(currentWeights.size() - 1);
            double randomWeight = random.nextDouble() * maxWeight;
            int n = 0;
            
            for (Double d : currentWeights) {
                if (d >= randomWeight) {
                    server = allList.get(n);
                    break;
                }
                n++;
            }
            
            if (server == null) {
                server = allList.get(random.nextInt(serverCount));
            }
        }
        
        return server;
    }
}
```

#### 2.2.2 区域感知负载均衡
```yaml
ribbon:
  NFLoadBalancerRuleClassName: com.netflix.loadbalancer.ZoneAvoidanceRule
  NIWSServerListFilterClassName: com.netflix.loadbalancer.ServerListSubsetFilter
  ServerListRefreshInterval: 2000
  
hystrix:
  command:
    default:
      execution:
        isolation:
          thread:
            timeoutInMilliseconds: 10000
```

### 2.3 自定义负载均衡器

```java
@Component
public class CustomLoadBalancer implements ReactorServiceInstanceLoadBalancer {
    
    private final ObjectProvider<ServiceInstanceListSupplier> supplierProvider;
    private final String serviceId;
    private final AtomicInteger position;
    
    public CustomLoadBalancer(
            ObjectProvider<ServiceInstanceListSupplier> supplierProvider,
            String serviceId) {
        this.supplierProvider = supplierProvider;
        this.serviceId = serviceId;
        this.position = new AtomicInteger(new Random().nextInt(1000));
    }
    
    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        ServiceInstanceListSupplier supplier = supplierProvider.getIfAvailable();
        return supplier.get(request).next()
            .map(instances -> processInstanceResponse(instances, request));
    }
    
    private Response<ServiceInstance> processInstanceResponse(
            List<ServiceInstance> instances, Request request) {
        if (instances.isEmpty()) {
            return new EmptyResponse();
        }
        
        // 自定义选择逻辑
        ServiceInstance instance = selectInstance(instances, request);
        return new DefaultResponse(instance);
    }
    
    private ServiceInstance selectInstance(
            List<ServiceInstance> instances, Request request) {
        // 1. 优先选择同区域实例
        List<ServiceInstance> sameZoneInstances = filterByZone(instances);
        if (!sameZoneInstances.isEmpty()) {
            return roundRobinSelect(sameZoneInstances);
        }
        
        // 2. 根据CPU负载选择
        return selectByCpuLoad(instances);
    }
    
    private ServiceInstance roundRobinSelect(List<ServiceInstance> instances) {
        int pos = Math.abs(this.position.incrementAndGet());
        return instances.get(pos % instances.size());
    }
}
```

## 三、熔断降级：Sentinel深度解析

### 3.1 Sentinel核心架构

#### 3.1.1 资源与规则定义
```java
// 注解方式定义资源
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @GetMapping("/{id}")
    @SentinelResource(
        value = "getUserById",
        blockHandler = "handleBlock",
        fallback = "getUserFallback",
        exceptionsToIgnore = {IllegalArgumentException.class}
    )
    public ResponseEntity<UserDTO> getUser(@PathVariable Long id) {
        // 业务逻辑
        return ResponseEntity.ok(userService.getUserById(id));
    }
    
    // 流控降级处理
    public ResponseEntity<UserDTO> handleBlock(Long id, BlockException ex) {
        log.warn("流量控制触发，用户ID: {}", id);
        return ResponseEntity.status(429)
            .body(UserDTO.builder()
                .id(id)
                .name("系统繁忙")
                .build());
    }
    
    // 熔断降级处理
    public ResponseEntity<UserDTO> getUserFallback(Long id, Throwable t) {
        log.error("服务熔断触发", t);
        return ResponseEntity.status(503)
            .body(UserDTO.builder()
                .id(id)
                .name("服务暂时不可用")
                .build());
    }
}
```

#### 3.1.2 规则管理器
```java
@Configuration
public class SentinelRuleConfiguration {
    
    @PostConstruct
    public void initRules() {
        // 流量控制规则
        List<FlowRule> flowRules = new ArrayList<>();
        FlowRule flowRule = new FlowRule("getUserById")
            .setCount(100)                    // 阈值
            .setGrade(RuleConstant.FLOW_GRADE_QPS)  // QPS模式
            .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP) // 预热模式
            .setWarmUpPeriodSec(10)           // 预热时间
            .setMaxQueueingTimeMs(500)        // 最大排队时间
            .setClusterMode(false);           // 单机模式
        flowRules.add(flowRule);
        
        // 熔断降级规则
        List<DegradeRule> degradeRules = new ArrayList<>();
        DegradeRule degradeRule = new DegradeRule("getUserById")
            .setGrade(RuleConstant.DEGRADE_GRADE_RT)     // 响应时间模式
            .setCount(100)                               // 响应时间阈值(ms)
            .setTimeWindow(10)                           // 熔断时间窗口(s)
            .setRtSlowRequestAmount(5)                   // 最小请求数
            .setMinRequestAmount(5);                     // 触发熔断的最小请求数
        degradeRules.add(degradeRule);
        
        // 系统保护规则
        List<SystemRule> systemRules = new ArrayList<>();
        SystemRule systemRule = new SystemRule()
            .setHighestSystemLoad(3.0)          // 最高系统负载
            .setAvgRt(100)                      // 平均RT
            .setMaxThread(500)                  // 最大线程数
            .setQps(200);                       // 入口QPS
        systemRules.add(systemRule);
        
        // 加载规则
        FlowRuleManager.loadRules(flowRules);
        DegradeRuleManager.loadRules(degradeRules);
        SystemRuleManager.loadRules(systemRules);
    }
}
```

### 3.2 高级熔断策略

#### 3.2.1 自适应熔断
```java
@Component
public class AdaptiveCircuitBreaker {
    
    private final CircuitBreaker circuitBreaker;
    
    public AdaptiveCircuitBreaker() {
        this.circuitBreaker = CircuitBreaker.of("user-service",
            CircuitBreakerConfig.custom()
                .failureRateThreshold(50)                 // 失败率阈值
                .slowCallRateThreshold(100)               // 慢调用率阈值
                .slowCallDurationThreshold(Duration.ofSeconds(2)) // 慢调用定义
                .permittedNumberOfCallsInHalfOpenState(10) // 半开状态允许的调用数
                .maxWaitDurationInHalfOpenState(Duration.ofSeconds(30)) // 半开状态最大等待时间
                .slidingWindowType(SlidingWindowType.TIME_BASED) // 时间滑动窗口
                .slidingWindowSize(100)                   // 滑动窗口大小
                .minimumNumberOfCalls(10)                 // 最小调用数
                .waitDurationInOpenState(Duration.ofSeconds(60)) // 熔断持续时间
                .automaticTransitionFromOpenToHalfOpenEnabled(true) // 自动转换
                .recordExceptions(IOException.class, TimeoutException.class)
                .ignoreExceptions(BusinessException.class)
                .build()
        );
    }
    
    @CircuitBreaker(name = "user-service", fallbackMethod = "fallback")
    public Mono<UserDTO> getUserWithCircuitBreaker(Long userId) {
        return webClient.get()
            .uri("/users/{id}", userId)
            .retrieve()
            .bodyToMono(UserDTO.class);
    }
    
    public Mono<UserDTO> fallback(Long userId, Throwable throwable) {
        return Mono.just(UserDTO.builder()
            .id(userId)
            .name("服务降级")
            .build());
    }
}
```

#### 3.2.2 热点参数限流
```java
@Configuration
public class HotParamFlowControl {
    
    @PostConstruct
    public void initHotParamRules() {
        // 热点参数规则
        ParamFlowRule rule = new ParamFlowRule("getUserById")
            .setParamIdx(0)                       // 参数索引（第一个参数）
            .setCount(10)                         // 阈值
            .setGrade(RuleConstant.FLOW_GRADE_QPS)
            .setDurationInSec(1);                 // 统计窗口
        
        // 参数例外项配置
        ParamFlowItem item = new ParamFlowItem()
            .setObject(String.valueOf(1))         // 参数值
            .setClassType(Integer.class.getName()) // 参数类型
            .setCount(100);                       // 单独阈值
        
        rule.setParamFlowItemList(Collections.singletonList(item));
        ParamFlowRuleManager.loadRules(Collections.singletonList(rule));
    }
    
    // 热点参数统计
    @Aspect
    @Component
    public class HotParamAspect {
        
        @Around("@annotation(hotParam)")
        public Object around(ProceedingJoinPoint joinPoint, HotParam hotParam) {
            Object[] args = joinPoint.getArgs();
            if (args.length > hotParam.paramIndex()) {
                String paramValue = String.valueOf(args[hotParam.paramIndex()]);
                try {
                    Entry entry = SphU.entry(hotParam.resource(), 
                        EntryType.IN, 1, paramValue);
                    return joinPoint.proceed();
                } catch (BlockException e) {
                    throw new RateLimitException("热点参数限流触发");
                } catch (Throwable t) {
                    Tracer.trace(t);
                    throw new RuntimeException(t);
                }
            }
            return joinPoint.proceed();
        }
    }
}
```

### 3.3 集群流控与降级

#### 3.3.1 集群流控配置
```java
@Configuration
public class ClusterFlowControlConfig {
    
    @Bean
    public ClusterStateManager clusterStateManager() {
        // 初始化集群流控状态管理器
        return new ClusterStateManager();
    }
    
    @Bean
    public TokenService tokenService() {
        // 基于Redis的令牌服务
        return new RedisTokenService(redisTemplate);
    }
    
    @PostConstruct
    public void initClusterFlowRules() {
        // 集群流控规则
        ClusterFlowRule clusterFlowRule = new ClusterFlowRule()
            .setFlowId(CLUSTER_FLOW_ID)
            .setThresholdType(ClusterRuleConstant.FLOW_THRESHOLD_GLOBAL)
            .setGrade(RuleConstant.FLOW_GRADE_QPS)
            .setCount(1000)                      // 集群总阈值
            .setStrategy(ClusterRuleConstant.FLOW_CLUSTER_STRATEGY_NORMAL)
            .setSampleCount(10)                  // 采样数
            .setWindowIntervalMs(1000);          // 窗口间隔
            
        // 集群降级规则
        ClusterDegradeRule clusterDegradeRule = new ClusterDegradeRule()
            .setFlowId(CLUSTER_DEGRADE_ID)
            .setGrade(RuleConstant.DEGRADE_GRADE_RT)
            .setCount(100)
            .setTimeWindow(10)
            .setMinRequestAmount(100);
    }
}
```

#### 3.3.2 实时监控与控制台
```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: localhost:8080      # 控制台地址
        port: 8719                     # 本地启动HTTP Server端口
      datasource:
        ds1:
          nacos:
            server-addr: localhost:8848
            dataId: sentinel-rules
            groupId: DEFAULT_GROUP
            rule-type: flow            # 规则类型
      filter:
        enabled: false                 # 关闭默认的CommonFilter
      metric:
        charset: UTF-8                 # 监控数据字符集
        file-single-size: 104857600    # 监控日志单个文件大小
        file-total-count: 6            # 监控日志文件总数
      log:
        dir: logs/sentinel            # 日志目录
        switch-pid: false             # 是否在日志中打印PID
```

## 四、集成实战：构建高可用微服务

### 4.1 完整配置示例

```yaml
# application.yml
spring:
  application:
    name: order-service
  
  cloud:
    # OpenFeign配置
    openfeign:
      client:
        config:
          default:
            connectTimeout: 5000
            readTimeout: 10000
            loggerLevel: full
            retryer: feign.Retryer.Default
            errorDecoder: com.example.CustomErrorDecoder
            decoder: feign.codec.Decoder.Default
            encoder: feign.codec.Encoder.Default
            contract: feign.Contract.Default
      compression:
        request:
          enabled: true
          mime-types: text/xml,application/xml,application/json
        response:
          enabled: true
      okhttp:
        enabled: true
      
    # 负载均衡配置
    loadbalancer:
      cache:
        enabled: true
        capacity: 256
        ttl: 30s
      health-check:
        interval: 30s
        initial-delay: 0s
      
    # Sentinel配置
    sentinel:
      enabled: true
      eager: true
      transport:
        dashboard: localhost:8080
        port: 8719
      datasource:
        flow:
          nacos:
            server-addr: ${spring.cloud.nacos.config.server-addr}
            dataId: ${spring.application.name}-flow-rules
            groupId: SENTINEL_GROUP
            rule-type: flow
        degrade:
          nacos:
            server-addr: ${spring.cloud.nacos.config.server-addr}
            dataId: ${spring.application.name}-degrade-rules
            groupId: SENTINEL_GROUP
            rule-type: degrade
        system:
          nacos:
            server-addr: ${spring.cloud.nacos.config.server-addr}
            dataId: ${spring.application.name}-system-rules
            groupId: SENTINEL_GROUP
            rule-type: system
        authority:
          nacos:
            server-addr: ${spring.cloud.nacos.config.server-addr}
            dataId: ${spring.application.name}-authority-rules
            groupId: SENTINEL_GROUP
            rule-type: authority
        param-flow:
          nacos:
            server-addr: ${spring.cloud.nacos.config.server-addr}
            dataId: ${spring.application.name}-param-flow-rules
            groupId: SENTINEL_GROUP
            rule-type: param-flow
      filter:
        enabled: true
        url-patterns: /**
      metric:
        file-single-size: 52428800
        file-total-count: 6
      log:
        dir: logs/csp/
        switch-pid: false

# 熔断器配置
resilience4j:
  circuitbreaker:
    instances:
      user-service:
        register-health-indicator: true
        sliding-window-size: 100
        minimum-number-of-calls: 10
        permitted-number-of-calls-in-half-open-state: 3
        automatic-transition-from-open-to-half-open-enabled: true
        wait-duration-in-open-state: 5s
        failure-rate-threshold: 50
        event-consumer-buffer-size: 10
        record-exceptions:
          - org.springframework.web.client.HttpServerErrorException
          - java.io.IOException
          - java.util.concurrent.TimeoutException
        ignore-exceptions:
          - com.example.BusinessException
  retry:
    instances:
      user-service:
        max-attempts: 3
        wait-duration: 100ms
        retry-exceptions:
          - org.springframework.web.client.HttpServerErrorException
  bulkhead:
    instances:
      user-service:
        max-concurrent-calls: 25
        max-wait-duration: 0
  timelimiter:
    instances:
      user-service:
        timeout-duration: 2s
        cancel-running-future: true

# 监控配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,sentinel
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http.server.requests: true
      sla:
        http.server.requests: 10ms, 50ms, 100ms, 200ms, 500ms, 1s, 2s
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
```

### 4.2 全局异常处理与降级

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    // Sentinel流控异常处理
    @ExceptionHandler(BlockException.class)
    public ResponseEntity<ErrorResponse> handleBlockException(
            BlockException ex, HttpServletRequest request) {
        log.warn("Sentinel流控触发: {}", ex.getRule(), ex);
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ErrorResponse.builder()
                .code("TOO_MANY_REQUESTS")
                .message("系统繁忙，请稍后重试")
                .path(request.getRequestURI())
                .timestamp(System.currentTimeMillis())
                .build());
    }
    
    // 熔断异常处理
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCircuitBreakerException(
            CallNotPermittedException ex, HttpServletRequest request) {
        log.error("熔断器打开: {}", ex.getMessage(), ex);
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse.builder()
                .code("SERVICE_UNAVAILABLE")
                .message("服务暂时不可用，请稍后重试")
                .path(request.getRequestURI())
                .timestamp(System.currentTimeMillis())
                .build());
    }
    
    // 超时异常处理
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeoutException(
            TimeoutException ex, HttpServletRequest request) {
        log.error("请求超时: {}", ex.getMessage(), ex);
        
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
            .body(ErrorResponse.builder()
                .code("GATEWAY_TIMEOUT")
                .message("请求超时")
                .path(request.getRequestURI())
                .timestamp(System.currentTimeMillis())
                .build());
    }
    
    // Feign客户端异常处理
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handleFeignException(
            FeignException ex, HttpServletRequest request) {
        log.error("Feign调用异常: {}", ex.getMessage(), ex);
        
        return ResponseEntity.status(ex.status())
            .body(ErrorResponse.builder()
                .code("REMOTE_SERVICE_ERROR")
                .message("远程服务调用失败")
                .path(request.getRequestURI())
                .timestamp(System.currentTimeMillis())
                .build());
    }
}
```

### 4.3 监控与告警集成

```java
@Configuration
@Slf4j
public class MonitoringConfiguration {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags(
            "application", "order-service",
            "environment", environment.getActiveProfiles()[0]
        );
    }
    
    @Bean
    public InMemorySentinelMetricsRepository sentinelMetricsRepository() {
        return new InMemorySentinelMetricsRepository();
    }
    
    @Bean
    public SentinelMetricsExporter sentinelMetricsExporter(
            InMemorySentinelMetricsRepository repository) {
        return new SentinelMetricsExporter(repository);
    }
    
    @EventListener
    public void onCircuitBreakerEvent(CircuitBreakerOnStateTransitionEvent event) {
        log.info("熔断器状态变更: {} -> {}", 
            event.getStateTransition().getFromState(), 
            event.getStateTransition().getToState());
        
        // 发送告警
        if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
            alertService.sendAlert(Alert.builder()
                .type(AlertType.CIRCUIT_BREAKER_OPEN)
                .service(event.getCircuitBreakerName())
                .message("熔断器已打开")
                .timestamp(System.currentTimeMillis())
                .build());
        }
    }
    
    @EventListener
    public void onSentinelBlockEvent(BlockExceptionEvent event) {
        log.warn("Sentinel流控事件: rule={}, resource={}", 
            event.getRule(), event.getResource());
        
        // 记录监控指标
        Metrics.counter("sentinel.block.count",
            "resource", event.getResource(),
            "rule", event.getRule().toString())
            .increment();
    }
}
```

## 五、性能优化与最佳实践

### 5.1 线程池优化

```java
@Configuration
public class ThreadPoolConfiguration {
    
    @Bean("feignClientThreadPool")
    public ThreadPoolTaskExecutor feignClientThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("feign-client-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
    
    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}
```

### 5.2 缓存策略优化

```java
@Component
@Slf4j
public class CircuitBreakerCacheManager {
    
    private final Cache<String, Object> circuitBreakerCache;
    
    public CircuitBreakerCacheManager() {
        this.circuitBreakerCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .maximumSize(1000)
            .recordStats()
            .build();
    }
    
    @CircuitBreaker(name = "user-service", fallbackMethod = "getCachedUser")
    public Mono<UserDTO> getUserWithCache(Long userId) {
        String cacheKey = "user:" + userId;
        UserDTO cachedUser = (UserDTO) circuitBreakerCache.getIfPresent(cacheKey);
        
        if (cachedUser != null) {
            return Mono.just(cachedUser);
        }
        
        return userServiceClient.getUserById(userId)
            .doOnNext(user -> circuitBreakerCache.put(cacheKey, user));
    }
    
    public Mono<UserDTO> getCachedUser(Long userId, Throwable throwable) {
        String cacheKey = "user:" + userId;
        UserDTO cachedUser = (UserDTO) circuitBreakerCache.getIfPresent(cacheKey);
        
        if (cachedUser != null) {
            log.info("返回缓存数据: userId={}", userId);
            return Mono.just(cachedUser);
        }
        
        return Mono.just(UserDTO.builder()
            .id(userId)
            .name("降级用户")
            .build());
    }
}
```

### 5.3 配置动态更新

```java
@Component
@RefreshScope
@Slf4j
public class DynamicConfigurationManager {
    
    @Value("${feign.client.config.default.connectTimeout:5000}")
    private Integer connectTimeout;
    
    @Value("${feign.client.config.default.readTimeout:10000}")
    private Integer readTimeout;
    
    @Value("${sentinel.flow.qps.threshold:100}")
    private Integer qpsThreshold;
    
    @EventListener
    public void onRefreshEvent(EnvironmentChangeEvent event) {
        log.info("配置变更事件: {}", event.getKeys());
        
        event.getKeys().forEach(key -> {
            if (key.contains("feign")) {
                refreshFeignConfiguration();
            }
            if (key.contains("sentinel")) {
                refreshSentinelRules();
            }
        });
    }
    
    private void refreshFeignConfiguration() {
        // 动态更新Feign配置
        log.info("更新Feign配置: connectTimeout={}, readTimeout={}", 
            connectTimeout, readTimeout);
    }
    
    private void refreshSentinelRules() {
        // 动态更新Sentinel规则
        FlowRuleManager.loadRules(Collections.singletonList(
            new FlowRule("getUserById")
                .setCount(qpsThreshold)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
        ));
        log.info("更新Sentinel流控规则: qpsThreshold={}", qpsThreshold);
    }
}
```