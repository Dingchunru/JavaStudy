# 微服务核心组件详解

## 一、服务注册与发现 - Nacos

### 1.1 核心概念

#### **服务注册中心的作用**
- **服务注册**：微服务启动时向注册中心注册自身信息（IP、端口、服务名等）
- **服务发现**：消费者从注册中心获取服务提供者信息
- **健康检查**：定期检测服务实例健康状态
- **负载均衡**：配合客户端/服务端负载均衡器实现流量分发

#### **Nacos 架构优势**
```
┌─────────────────────────────────────┐
│            Nacos Server             │
│  ┌─────────────┬───────────────┐  │
│  │ Naming Service │ Config Service │  │
│  └─────────────┴───────────────┘  │
└─────────────────────────────────────┘
         ▲              ▲
         │              │
    ┌────┴────┐   ┌─────┴─────┐
    │ 服务注册/发现  │   配置管理   │
    └────┬────┘   └─────┬─────┘
         │              │
    ┌────┴──────────────┴────┐
    │      微服务应用集群       │
    └─────────────────────────┘
```

### 1.2 详细实现

#### **服务注册实现**
```java
// Spring Cloud Alibaba 依赖
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>

// 配置示例
spring:
  application:
    name: user-service
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
        namespace: dev
        group: DEFAULT_GROUP
        ephemeral: true  # 临时实例（默认）
        # 持久化实例配置
        # ephemeral: false
        # weight: 1.0    # 权重
        # metadata: 
        #   version: v1.0
```

#### **健康检查机制**
1. **TCP心跳检查**：默认方式，通过TCP连接检测
2. **HTTP心跳检查**：发送HTTP请求检查
3. **MySQL健康检查**：检测数据库连接
4. **自定义健康检查**：实现HealthIndicator接口

```yaml
# 健康检查配置
spring:
  cloud:
    nacos:
      discovery:
        health-check-enabled: true
        health-check-interval: 10s
        health-check-timeout: 5s
        health-check-fail-threshold: 3
```

### 1.3 集群与高可用

#### **Nacos集群部署**
```bash
# 集群配置示例
# cluster.conf
192.168.1.101:8848
192.168.1.102:8848
192.168.1.103:8848

# 数据库配置（MySQL）
db.num=1
db.url.0=jdbc:mysql://127.0.0.1:3306/nacos?characterEncoding=utf8
db.user=nacos
db.password=nacos
```

#### **服务发现高级特性**
```java
@RestController
public class ServiceConsumer {
    
    @Autowired
    private LoadBalancerClient loadBalancer;
    
    @Autowired
    private DiscoveryClient discoveryClient;
    
    // 1. 获取所有实例
    public List<ServiceInstance> getInstances(String serviceId) {
        return discoveryClient.getInstances(serviceId);
    }
    
    // 2. 负载均衡调用
    public String loadBalanceCall() {
        ServiceInstance instance = loadBalancer.choose("user-service");
        String url = String.format("http://%s:%s/api/users", 
            instance.getHost(), instance.getPort());
        return restTemplate.getForObject(url, String.class);
    }
    
    // 3. 使用 @LoadBalanced
    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

## 二、配置中心 - Nacos Config

### 2.1 统一配置管理

#### **配置管理架构**
```
┌─────────────────────────────────┐
│      Nacos 配置中心              │
│                                 │
│  ├── 命名空间 (Namespace)       │
│  │   ├── 开发环境 (dev)         │
│  │   ├── 测试环境 (test)        │
│  │   └── 生产环境 (prod)        │
│  │                              │
│  ├── 配置分组 (Group)           │
│  │   ├── DEFAULT_GROUP          │
│  │   ├── DATABASE_GROUP         │
│  │   └── MQ_GROUP               │
│  │                              │
│  └── 配置Data ID                │
│      ├── user-service.yaml      │
│      ├── order-service.yaml     │
│      └── common-config.yaml     │
└─────────────────────────────────┘
```

#### **配置优先级规则**
```yaml
# 配置加载顺序（优先级从高到低）：
# 1. 命令行参数 (--server.port=8081)
# 2. Java系统属性 (-Dspring.profiles.active=dev)
# 3. 操作系统环境变量
# 4. 从Nacos加载的配置（根据spring.cloud.nacos.config.refresh-enabled动态刷新）
# 5. application-{profile}.yml
# 6. application.yml
# 7. @Configuration类上的@PropertySource
# 8. SpringApplication.setDefaultProperties
```

### 2.2 动态配置实现

#### **基础配置**
```yaml
# bootstrap.yml
spring:
  application:
    name: user-service
  profiles:
    active: dev
  cloud:
    nacos:
      config:
        server-addr: 127.0.0.1:8848
        namespace: ${spring.profiles.active}
        group: DEFAULT_GROUP
        file-extension: yaml
        # 共享配置
        shared-configs:
          - data-id: common-config.yaml
            group: COMMON_GROUP
            refresh: true
          - data-id: datasource-config.yaml
            group: DATABASE_GROUP
            refresh: true
        # 扩展配置
        extension-configs:
          - data-id: redis-config.yaml
            group: MIDDLEWARE_GROUP
            refresh: true
```

#### **动态刷新示例**
```java
@RestController
@RefreshScope  // 支持配置动态刷新
public class ConfigController {
    
    @Value("${server.port}")
    private String port;
    
    @Value("${config.info}")
    private String configInfo;
    
    @Autowired
    private Environment environment;
    
    // 监听配置变更
    @EventListener
    public void onRefresh(RefreshScopeRefreshedEvent event) {
        System.out.println("配置已刷新: " + new Date());
    }
    
    // 获取所有配置
    @GetMapping("/configs")
    public Map<String, Object> getConfigs() {
        Map<String, Object> configs = new HashMap<>();
        configs.put("port", port);
        configs.put("configInfo", configInfo);
        configs.put("database.url", 
            environment.getProperty("spring.datasource.url"));
        return configs;
    }
}
```

### 2.3 配置版本与回滚

#### **版本管理策略**
```sql
-- Nacos配置历史表结构
CREATE TABLE `his_config_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `nid` bigint(20) NOT NULL,
  `data_id` varchar(255) NOT NULL,
  `group_id` varchar(128) NOT NULL,
  `app_name` varchar(128) DEFAULT NULL,
  `content` longtext NOT NULL,
  `md5` varchar(32) DEFAULT NULL,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `src_user` text,
  `src_ip` varchar(20) DEFAULT NULL,
  `op_type` char(10) DEFAULT NULL,
  `tenant_id` varchar(128) DEFAULT ''
);
```

## 三、API网关 - Spring Cloud Gateway

### 3.1 网关核心功能

#### **网关架构设计**
```
┌─────────────────────────────────────────────────────┐
│                 API Gateway                          │
├─────────────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐  │
│  │ 路由转发  │  │ 权限认证  │  │  请求/响应转换   │  │
│  └──────────┘  └──────────┘  └──────────────────┘  │
├─────────────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐  │
│  │ 流量控制  │  │ 熔断降级  │  │  监控与日志      │  │
│  └──────────┘  └──────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────┘
                         │
         ┌───────────────┼───────────────┐
         │               │               │
    ┌────┴────┐    ┌────┴────┐    ┌────┴────┐
    │用户服务   │    │订单服务   │    │商品服务   │
    └─────────┘    └─────────┘    └─────────┘
```

### 3.2 路由配置详解

#### **基础路由配置**
```yaml
spring:
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true  # 开启从注册中心自动创建路由
          lower-case-service-id: true
      routes:
        - id: user-service
          uri: lb://user-service  # lb://表示负载均衡
          predicates:
            - Path=/user/**
          filters:
            - StripPrefix=1
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10   # 令牌填充速率
                redis-rate-limiter.burstCapacity: 20   # 令牌桶容量
                key-resolver: "#{@userKeyResolver}"
        
        - id: order-service
          uri: lb://order-service
          predicates:
            - Path=/order/**
            - Method=GET,POST
            - After=2023-01-20T17:42:47.789-07:00[Asia/Shanghai]
          filters:
            - StripPrefix=1
            - AddRequestHeader=X-Request-Red, Blue
        
        # 重定向路由
        - id: redirect-route
          uri: https://example.org
          predicates:
            - Path=/old-path/**
          filters:
            - RedirectTo=302, https://new-example.org/new-path
```

#### **动态路由配置**
```java
@Configuration
public class DynamicRouteConfig {
    
    @Autowired
    private RouteDefinitionWriter routeDefinitionWriter;
    
    @Autowired
    private ApplicationEventPublisher publisher;
    
    /**
     * 添加路由
     */
    public void addRoute(RouteDefinition definition) {
        routeDefinitionWriter.save(Mono.just(definition)).subscribe();
        publisher.publishEvent(new RefreshRoutesEvent(this));
    }
    
    /**
     * 删除路由
     */
    public void deleteRoute(String routeId) {
        routeDefinitionWriter.delete(Mono.just(routeId)).subscribe();
        publisher.publishEvent(new RefreshRoutesEvent(this));
    }
    
    /**
     * 从数据库加载路由
     */
    @PostConstruct
    public void loadRoutesFromDB() {
        List<RouteDefinition> routes = routeRepository.findAll();
        routes.forEach(route -> {
            routeDefinitionWriter.save(Mono.just(route)).subscribe();
        });
        publisher.publishEvent(new RefreshRoutesEvent(this));
    }
}
```

### 3.3 过滤器详解

#### **全局过滤器**
```java
@Component
@Order(-1)
public class GlobalAuthFilter implements GlobalFilter {
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, 
                             GatewayFilterChain chain) {
        
        // 1. 获取请求
        ServerHttpRequest request = exchange.getRequest();
        
        // 2. 鉴权逻辑
        String token = request.getHeaders().getFirst("Authorization");
        if (!validateToken(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        
        // 3. 记录日志
        String path = request.getURI().getPath();
        String method = request.getMethodValue();
        log.info("请求路径: {}, 方法: {}", path, method);
        
        // 4. 添加请求头
        ServerHttpRequest newRequest = request.mutate()
            .header("X-Request-Time", LocalDateTime.now().toString())
            .build();
        
        return chain.filter(exchange.mutate().request(newRequest).build());
    }
    
    private boolean validateToken(String token) {
        // 实现token验证逻辑
        return token != null && token.startsWith("Bearer ");
    }
}
```

#### **自定义网关过滤器**
```java
@Component
public class CustomFilter implements GatewayFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(CustomFilter.class);
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        
        // 前置处理
        log.info("Custom Filter Pre Process");
        
        // 获取请求参数
        MultiValueMap<String, String> queryParams = exchange.getRequest().getQueryParams();
        String userId = queryParams.getFirst("userId");
        
        if (StringUtils.isEmpty(userId)) {
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse()
                    .bufferFactory()
                    .wrap("缺少userId参数".getBytes()))
            );
        }
        
        // 修改请求体
        if (exchange.getRequest().getHeaders().getContentType() 
            == MediaType.APPLICATION_JSON) {
            
            return ServerWebExchangeUtils.cacheRequestBodyAndRequest(exchange, 
                (serverHttpRequest) -> {
                    return DataBufferUtils.join(serverHttpRequest.getBody())
                        .flatMap(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);
                            
                            try {
                                // 修改请求体
                                String body = new String(bytes, StandardCharsets.UTF_8);
                                JSONObject json = new JSONObject(body);
                                json.put("processed", true);
                                json.put("processTime", System.currentTimeMillis());
                                
                                byte[] newBytes = json.toString().getBytes();
                                exchange.getAttributes().put("cachedRequestBody", newBytes);
                                
                                ServerHttpRequest newRequest = serverHttpRequest.mutate()
                                    .body(Flux.just(exchange.getResponse()
                                        .bufferFactory()
                                        .wrap(newBytes)))
                                    .build();
                                
                                return chain.filter(exchange.mutate()
                                    .request(newRequest).build());
                                    
                            } catch (Exception e) {
                                return Mono.error(e);
                            }
                        });
                });
        }
        
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            // 后置处理
            log.info("Custom Filter Post Process");
            
            // 修改响应
            ServerHttpResponse response = exchange.getResponse();
            response.getHeaders().add("X-Custom-Header", "CustomValue");
        }));
    }
    
    @Override
    public int getOrder() {
        return 0;
    }
}
```

### 3.4 高级功能实现

#### **熔断降级配置**
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: hystrix-route
          uri: lb://user-service
          predicates:
            - Path=/users/**
          filters:
            - name: Hystrix
              args:
                name: fallbackcmd
                fallbackUri: forward:/fallback/user
            - name: CircuitBreaker
              args:
                name: userServiceCB
                fallbackUri: forward:/fallback/user-service
                statusCodes:
                  - 500
                  - 502
                  - 503

# Resilience4j 配置
resilience4j.circuitbreaker:
  instances:
    userServiceCB:
      slidingWindowSize: 10
      failureRateThreshold: 50
      waitDurationInOpenState: 10000
      permittedNumberOfCallsInHalfOpenState: 3
```

#### **限流配置**
```java
@Configuration
public class RateLimitConfig {
    
    @Bean(name = "userKeyResolver")
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // 根据用户限流
            String userId = exchange.getRequest()
                .getQueryParams().getFirst("userId");
            return Mono.just(Optional.ofNullable(userId).orElse("anonymous"));
        };
    }
    
    @Bean(name = "ipKeyResolver")
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            // 根据IP限流
            String ip = exchange.getRequest()
                .getRemoteAddress().getAddress().getHostAddress();
            return Mono.just(Optional.ofNullable(ip).orElse("unknown"));
        };
    }
    
    @Bean(name = "apiKeyResolver")
    public KeyResolver apiKeyResolver() {
        return exchange -> {
            // 根据API路径限流
            String path = exchange.getRequest().getPath().value();
            return Mono.just(path);
        };
    }
}
```

#### **跨域配置**
```java
@Configuration
public class CorsConfig {
    
    @Bean
    public CorsWebFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许的域
        config.addAllowedOrigin("http://localhost:3000");
        config.addAllowedOrigin("https://production-domain.com");
        
        // 允许的方法
        config.addAllowedMethod("*");
        
        // 允许的头部
        config.addAllowedHeader("*");
        
        // 允许携带凭证
        config.setAllowCredentials(true);
        
        // 预检请求缓存时间
        config.setMaxAge(3600L);
        
        // 暴露的头部
        config.addExposedHeader("Authorization");
        config.addExposedHeader("X-Custom-Header");
        
        UrlBasedCorsConfigurationSource source = 
            new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsWebFilter(source);
    }
}
```

## 四、组件集成与最佳实践

### 4.1 完整的微服务配置示例

```yaml
# application.yml
spring:
  application:
    name: ${APP_NAME:user-service}
  
  profiles:
    active: ${PROFILE:dev}
  
  cloud:
    # Nacos 服务发现
    nacos:
      discovery:
        server-addr: ${NACOS_HOST:127.0.0.1}:${NACOS_PORT:8848}
        namespace: ${NAMESPACE:dev}
        group: ${GROUP:DEFAULT_GROUP}
        cluster-name: ${CLUSTER_NAME:DEFAULT}
        metadata:
          version: ${VERSION:1.0.0}
          region: ${REGION:cn-east-1}
        
        # 心跳配置
        heart-beat-interval: 5000
        heart-beat-timeout: 15000
        ip-delete-timeout: 30000
    
    # Nacos 配置中心
    config:
      server-addr: ${spring.cloud.nacos.discovery.server-addr}
      namespace: ${spring.cloud.nacos.discovery.namespace}
      group: ${spring.cloud.nacos.discovery.group}
      file-extension: yaml
      refresh-enabled: true
      shared-configs[0]:
        data-id: common-${spring.profiles.active}.yaml
        group: COMMON_GROUP
        refresh: true
      shared-configs[1]:
        data-id: datasource-${spring.profiles.active}.yaml
        group: DATABASE_GROUP
        refresh: true
    
    # Gateway 配置
    gateway:
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
      
      # 全局过滤器
      default-filters:
        - AddRequestHeader=X-Gateway-Request, true
        - AddResponseHeader=X-Gateway-Response, true
        - name: RequestRateLimiter
          args:
            key-resolver: "#{@ipKeyResolver}"
            redis-rate-limiter.replenishRate: 100
            redis-rate-limiter.burstCapacity: 200
      
      # 路由配置
      routes:
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/auth/**
          filters:
            - StripPrefix=1
            - name: CircuitBreaker
              args:
                name: authService
                fallbackUri: forward:/fallback/auth
        
        - id: api-route
          uri: lb://user-service
          predicates:
            - Path=/api/**
          filters:
            - name: JwtAuthFilter
              args:
                secret: ${JWT_SECRET}
                header: Authorization
                prefix: Bearer

# 监控配置
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,gateway
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
  tracing:
    sampling:
      probability: 1.0
```

### 4.2 监控与运维

#### **健康检查端点**
```yaml
# 健康检查配置
management:
  endpoint:
    health:
      probes:
        enabled: true
      show-details: always
      group:
        readiness:
          include: readinessState,nacosDiscovery,db,redis
        liveness:
          include: livenessState,diskSpace
        custom:
          include: customCheck

# 自定义健康检查
@Component
public class CustomHealthIndicator implements HealthIndicator {
    
    @Override
    public Health health() {
        boolean isHealthy = check();
        if (isHealthy) {
            return Health.up()
                .withDetail("status", "服务运行正常")
                .withDetail("timestamp", System.currentTimeMillis())
                .build();
        } else {
            return Health.down()
                .withDetail("status", "服务异常")
                .withDetail("error", "自定义检查失败")
                .build();
        }
    }
    
    private boolean check() {
        // 自定义健康检查逻辑
        return true;
    }
}
```

#### **指标监控**
```java
@Configuration
public class MetricsConfig {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
            .commonTags("application", "api-gateway")
            .commonTags("region", System.getenv("REGION"))
            .commonTags("zone", System.getenv("ZONE"));
    }
    
    // 自定义指标
    @Bean
    public Counter requestCounter(MeterRegistry registry) {
        return Counter.builder("gateway.requests.total")
            .description("网关总请求数")
            .tags("type", "total")
            .register(registry);
    }
    
    @Bean
    public Timer requestTimer(MeterRegistry registry) {
        return Timer.builder("gateway.request.duration")
            .description("请求处理时间")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }
}
```

### 4.3 安全最佳实践

#### **安全配置**
```java
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http) {
        
        return http
            .csrf().disable()
            .authorizeExchange()
                .pathMatchers("/auth/**", "/actuator/health").permitAll()
                .pathMatchers("/actuator/**").hasRole("ADMIN")
                .anyExchange().authenticated()
            .and()
            .oauth2ResourceServer()
                .jwt()
                .jwtAuthenticationConverter(jwtAuthenticationConverter())
            .and()
            .addFilterBefore(new JwtFilter(), 
                SecurityWebFiltersOrder.AUTHENTICATION)
            .build();
    }
    
    private Converter<Jwt, ? extends Mono<? extends AbstractAuthenticationToken>> 
        jwtAuthenticationConverter() {
        
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // 从JWT中提取权限
            List<String> roles = jwt.getClaimAsStringList("roles");
            return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
        });
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
```

## 五、故障排查与优化

### 5.1 常见问题解决方案

| 问题类型 | 可能原因 | 解决方案 |
|---------|---------|---------|
| 服务注册失败 | 网络不通，Nacos未启动 | 检查网络，确认Nacos服务状态 |
| 配置不生效 | 配置格式错误，未启用刷新 | 检查YAML格式，添加@RefreshScope |
| 网关路由404 | 路由配置错误，服务未注册 | 检查predicates配置，确认服务状态 |
| 限流失效 | Redis未配置，KeyResolver错误 | 检查Redis连接，验证KeyResolver |
| 性能瓶颈 | 网关单点，未启用缓存 | 部署网关集群，启用响应缓存 |

### 5.2 性能优化建议

1. **网关优化**：
   - 启用响应缓存
   - 配置合理的线程池
   - 启用HTTP/2
   - 使用WebFlux非阻塞模型

2. **Nacos优化**：
   - 集群部署保障高可用
   - 配置持久化到MySQL
   - 合理设置心跳间隔
   - 启用客户端缓存

3. **配置优化**：
   - 使用共享配置减少冗余
   - 合理规划命名空间和分组
   - 启用配置加密敏感信息
   - 建立配置变更审批流程

### 5.3 监控告警配置

```yaml
# Prometheus告警规则示例
groups:
  - name: microservices
    rules:
      - alert: ServiceDown
        expr: up{job="nacos-service"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "服务 {{ $labels.instance }} 宕机"
          
      - alert: HighRequestLatency
        expr: histogram_quantile(0.95, 
              rate(gateway_request_duration_seconds_bucket[5m])) > 1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "网关请求延迟过高"
```