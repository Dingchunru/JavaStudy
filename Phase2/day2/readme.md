# Spring MVC 深度解析

## 一、Spring MVC 请求处理流程

### 1.1 核心流程图
```
客户端请求 → DispatcherServlet → HandlerMapping → HandlerAdapter → Controller → 返回结果
       ↑                                                                   ↓
       ←------------- 视图渲染 ← ModelAndView ← 异常处理 ←------------------
```

### 1.2 详细处理步骤

#### 步骤1：请求到达 DispatcherServlet
```java
// DispatcherServlet 继承关系
HttpServlet ← FrameworkServlet ← DispatcherServlet
```

**DispatcherServlet 初始化过程：**
1. 加载 ApplicationContext
2. 初始化 HandlerMapping、HandlerAdapter 等九大组件
3. 扫描 @Controller、@RequestMapping 等注解

#### 步骤2：HandlerMapping 映射处理器
```java
// 常见的 HandlerMapping 实现
- RequestMappingHandlerMapping：处理 @RequestMapping 注解
- BeanNameUrlHandlerMapping：根据 Bean 名称映射
- SimpleUrlHandlerMapping：通过配置文件映射
```

**查找顺序：**
```java
// 1. 根据 URL 匹配 HandlerMethod
HandlerMethod handlerMethod = handlerMapping.getHandler(request);

// 2. 支持的内容：
//    - URL 路径匹配（/users/{id}）
//    - HTTP 方法匹配（GET、POST）
//    - 请求参数匹配（params="name=test"）
//    - 请求头匹配（headers="Content-Type=application/json"）
```

#### 步骤3：HandlerAdapter 执行处理器
```java
// HandlerAdapter 主要实现
1. RequestMappingHandlerAdapter：处理 @Controller
2. HttpRequestHandlerAdapter：处理 HttpRequestHandler
3. SimpleControllerHandlerAdapter：处理 Controller 接口
```

**适配器模式应用：**
```java
public ModelAndView handle(HttpServletRequest request, 
                          HttpServletResponse response, 
                          Object handler) throws Exception {
    
    // 参数解析
    Object[] args = getMethodArgumentValues(request, response, handler);
    
    // 执行控制器方法
    Object returnValue = invokeHandlerMethod(handler, args);
    
    // 返回值处理
    return handleReturnValue(returnValue, response);
}
```

#### 步骤4：拦截器执行链
```java
// 拦截器执行顺序
preHandle 1 → preHandle 2 → Controller → postHandle 2 → postHandle 1 → afterCompletion 2 → afterCompletion 1
```

#### 步骤5：参数解析与绑定
**支持的类型：**
```java
@RequestParam, @PathVariable, @RequestBody, @RequestHeader,
@CookieValue, @ModelAttribute, @SessionAttribute, @RequestPart
```

**自定义参数解析器：**
```java
@Component
public class UserArgumentResolver implements HandlerMethodArgumentResolver {
    
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(User.class);
    }
    
    @Override
    public Object resolveArgument(MethodParameter parameter,
                                 ModelAndViewContainer mavContainer,
                                 NativeWebRequest webRequest,
                                 WebDataBinderFactory binderFactory) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();
        String userId = request.getHeader("X-User-Id");
        return userService.findById(userId);
    }
}

// 注册配置
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new UserArgumentResolver());
    }
}
```

#### 步骤6：返回值处理
```java
// 常用返回值处理器
- ModelAndViewResolver：处理 ModelAndView
- @ResponseBody 处理器：处理 JSON/XML 响应
- ViewResolver：视图解析器
- HttpEntity 处理器
```

### 1.3 视图渲染流程
```java
// ViewResolver 链
1. BeanNameViewResolver
2. InternalResourceViewResolver（JSP）
3. FreeMarkerViewResolver
4. ThymeleafViewResolver

// 渲染过程
View view = viewResolver.resolveViewName(viewName, locale);
view.render(model, request, response);
```

## 二、拦截器深入解析

### 2.1 拦截器接口定义
```java
public interface HandlerInterceptor {
    // 控制器执行前调用
    default boolean preHandle(HttpServletRequest request, 
                            HttpServletResponse response, 
                            Object handler) throws Exception {
        return true; // true=继续执行，false=中断
    }
    
    // 控制器执行后，视图渲染前调用
    default void postHandle(HttpServletRequest request, 
                          HttpServletResponse response, 
                          Object handler,
                          ModelAndView modelAndView) throws Exception {
    }
    
    // 请求完成后调用（包括异常情况）
    default void afterCompletion(HttpServletRequest request, 
                               HttpServletResponse response, 
                               Object handler, 
                               Exception ex) throws Exception {
    }
}
```

### 2.2 拦截器配置示例
```java
@Configuration
public class InterceptorConfig implements WebMvcConfigurer {
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 认证拦截器
        registry.addInterceptor(new AuthInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/login", "/api/register")
                .order(1);
        
        // 日志拦截器
        registry.addInterceptor(new LogInterceptor())
                .addPathPatterns("/**")
                .order(2);
        
        // 性能监控拦截器
        registry.addInterceptor(new PerformanceInterceptor())
                .addPathPatterns("/api/**")
                .order(3);
    }
}
```

### 2.3 拦截器实现案例

#### 认证拦截器
```java
@Component
public class AuthInterceptor implements HandlerInterceptor {
    
    @Autowired
    private JwtTokenProvider tokenProvider;
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) throws Exception {
        
        // 从请求头获取token
        String token = resolveToken(request);
        
        if (token == null || !tokenProvider.validateToken(token)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write("{\"error\": \"Unauthorized\"}");
            return false;
        }
        
        // 将用户信息存入请求属性
        String username = tokenProvider.getUsername(token);
        request.setAttribute("username", username);
        
        return true;
    }
    
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

#### 日志拦截器
```java
@Component
public class LogInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(LogInterceptor.class);
    private ThreadLocal<Long> startTime = new ThreadLocal<>();
    
    @Override
    public boolean preHandle(HttpServletRequest request, 
                           HttpServletResponse response, 
                           Object handler) {
        startTime.set(System.currentTimeMillis());
        
        logger.info("Request Started: {} {} from {}", 
                   request.getMethod(),
                   request.getRequestURI(),
                   request.getRemoteAddr());
        
        // 记录请求参数
        if (logger.isDebugEnabled()) {
            Enumeration<String> params = request.getParameterNames();
            while (params.hasMoreElements()) {
                String paramName = params.nextElement();
                logger.debug("Parameter: {} = {}", paramName, request.getParameter(paramName));
            }
        }
        
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request,
                              HttpServletResponse response,
                              Object handler,
                              Exception ex) {
        long duration = System.currentTimeMillis() - startTime.get();
        startTime.remove();
        
        logger.info("Request Completed: {} {} - Status: {} - Duration: {}ms",
                   request.getMethod(),
                   request.getRequestURI(),
                   response.getStatus(),
                   duration);
        
        if (ex != null) {
            logger.error("Request Error: {}", ex.getMessage(), ex);
        }
    }
}
```

### 2.4 拦截器与过滤器的区别

| 特性 | 拦截器 (Interceptor) | 过滤器 (Filter) |
|------|---------------------|----------------|
| 依赖框架 | Spring MVC | Servlet 规范 |
| 作用范围 | 控制器方法前后 | 请求进入Servlet前 |
| 访问对象 | HandlerMethod、ModelAndView | ServletRequest、ServletResponse |
| 异常处理 | 可访问异常对象 | 只能处理FilterChain异常 |
| 依赖注入 | 支持 | 需要特殊配置 |

## 三、全局异常处理

### 3.1 @ControllerAdvice 实现全局异常处理
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    
    // 记录异常日志
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        logger.warn("Business exception: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .path(getRequestPath())
                .build();
        
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 处理数据校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {
        
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());
        
        ErrorResponse error = ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Validation failed")
                .details(errors)
                .timestamp(LocalDateTime.now())
                .build();
        
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
    
    /**
     * 处理认证授权异常
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(Exception ex) {
        ErrorResponse error = ErrorResponse.builder()
                .code("ACCESS_DENIED")
                .message("Access denied")
                .timestamp(LocalDateTime.now())
                .build();
        
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }
    
    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex, 
                                                           HttpServletRequest request) {
        logger.error("Unhandled exception", ex);
        
        ErrorResponse error = ErrorResponse.builder()
                .code("INTERNAL_ERROR")
                .message("Internal server error")
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .build();
        
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    /**
     * 自定义错误响应类
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ErrorResponse {
        private String code;
        private String message;
        private List<String> details;
        private LocalDateTime timestamp;
        private String path;
    }
}
```

### 3.2 异常处理优化技巧

#### 自定义异常类
```java
// 基础异常类
public abstract class BaseException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;
    
    public BaseException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
    
    // Getters...
}

// 业务异常
public class BusinessException extends BaseException {
    public BusinessException(String errorCode, String message) {
        super(errorCode, message, HttpStatus.BAD_REQUEST);
    }
}

// 未找到资源异常
public class ResourceNotFoundException extends BaseException {
    public ResourceNotFoundException(String resource, String identifier) {
        super("RESOURCE_NOT_FOUND", 
              String.format("%s with id %s not found", resource, identifier),
              HttpStatus.NOT_FOUND);
    }
}
```

#### 异常处理工具类
```java
@Component
public class ExceptionHelper {
    
    /**
     * 构建标准化的错误响应
     */
    public static ErrorResponse buildErrorResponse(String code, 
                                                 String message, 
                                                 HttpServletRequest request) {
        return ErrorResponse.builder()
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .path(request.getRequestURI())
                .method(request.getMethod())
                .build();
    }
    
    /**
     * 记录异常日志
     */
    public static void logException(Exception ex, HttpServletRequest request) {
        String queryString = request.getQueryString();
        String path = request.getRequestURI() + (queryString != null ? "?" + queryString : "");
        
        logger.error("Exception occurred: {} {} - {}", 
                    request.getMethod(), 
                    path,
                    ex.getMessage(), 
                    ex);
    }
}
```

## 四、RESTful API 设计

### 4.1 RESTful 核心原则

#### 1. 资源导向
```
GET    /users           # 获取用户列表
GET    /users/{id}      # 获取特定用户
POST   /users           # 创建用户
PUT    /users/{id}      # 更新用户全部信息
PATCH  /users/{id}      # 部分更新用户
DELETE /users/{id}      # 删除用户
```

#### 2. 状态码规范
```java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    
    // 200 OK - 成功返回资源
    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        User user = userService.findById(id);
        return ResponseEntity.ok(user);
    }
    
    // 201 Created - 资源创建成功
    @PostMapping
    public ResponseEntity<User> createUser(@Valid @RequestBody User user) {
        User created = userService.create(user);
        return ResponseEntity
                .created(URI.create("/api/v1/users/" + created.getId()))
                .body(created);
    }
    
    // 204 No Content - 操作成功无返回
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
    
    // 400 Bad Request - 请求参数错误
    // 401 Unauthorized - 未认证
    // 403 Forbidden - 无权限
    // 404 Not Found - 资源不存在
    // 409 Conflict - 资源冲突
    // 500 Internal Server Error - 服务器错误
}
```

### 4.2 RESTful 最佳实践

#### 版本控制
```java
// 方式1：URL路径版本
@RestController
@RequestMapping("/api/v1/users")

// 方式2：请求头版本
@RequestMapping(value = "/api/users", headers = "API-Version=1")

// 方式3：内容协商版本
@RequestMapping(value = "/api/users", produces = "application/vnd.company.app-v1+json")
```

#### HATEOAS 实现
```java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    
    @Autowired
    private EntityLinks entityLinks;
    
    @GetMapping("/{id}")
    public EntityModel<User> getUser(@PathVariable Long id) {
        User user = userService.findById(id);
        
        EntityModel<User> resource = EntityModel.of(user);
        
        // 添加相关链接
        resource.add(entityLinks.linkToItemResource(User.class, id).withSelfRel());
        resource.add(Link.of("/api/v1/users/" + id + "/orders", "orders"));
        resource.add(Link.of("/api/v1/users/" + id + "/addresses", "addresses"));
        
        return resource;
    }
    
    @GetMapping
    public CollectionModel<EntityModel<User>> getUsers(Pageable pageable) {
        Page<User> users = userService.findAll(pageable);
        
        List<EntityModel<User>> userResources = users.stream()
                .map(user -> EntityModel.of(user,
                        entityLinks.linkToItemResource(User.class, user.getId()).withSelfRel()))
                .collect(Collectors.toList());
        
        CollectionModel<EntityModel<User>> resources = CollectionModel.of(userResources);
        
        // 分页链接
        if (users.hasNext()) {
            resources.add(Link.of(buildPageUrl(pageable.getPageNumber() + 1), "next"));
        }
        if (users.hasPrevious()) {
            resources.add(Link.of(buildPageUrl(pageable.getPageNumber() - 1), "prev"));
        }
        
        return resources;
    }
}
```

#### API 文档生成（SpringDoc OpenAPI）
```java
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "用户管理", description = "用户相关的CRUD操作")
public class UserController {
    
    @Operation(summary = "获取用户列表", description = "分页查询用户列表")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误")
    })
    @GetMapping
    public ResponseEntity<Page<User>> getUsers(
            @Parameter(description = "分页参数") Pageable pageable,
            @Parameter(description = "搜索关键字") @RequestParam(required = false) String keyword) {
        // ...
    }
    
    @Operation(summary = "创建用户")
    @PostMapping
    public ResponseEntity<User> createUser(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "用户信息",
                required = true,
                content = @Content(schema = @Schema(implementation = User.class))
            )
            @Valid @RequestBody User user) {
        // ...
    }
}
```

### 4.3 RESTful 高级特性

#### 条件请求
```java
@GetMapping("/{id}")
public ResponseEntity<User> getUser(
        @PathVariable Long id,
        @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
    
    User user = userService.findById(id);
    String etag = "\"" + user.getVersion() + "\"";
    
    // 检查ETag
    if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
    }
    
    return ResponseEntity.ok()
            .eTag(etag)
            .lastModified(user.getUpdatedAt().toInstant())
            .body(user);
}
```

#### 异步处理
```java
@RestController
@RequestMapping("/api/v1/async")
public class AsyncController {
    
    @GetMapping("/long-operation")
    public Callable<ResponseEntity<String>> longOperation() {
        return () -> {
            // 模拟长时间操作
            Thread.sleep(5000);
            return ResponseEntity.ok("Operation completed");
        };
    }
    
    @GetMapping("/deferred-result")
    public DeferredResult<ResponseEntity<String>> deferredOperation() {
        DeferredResult<ResponseEntity<String>> deferredResult = new DeferredResult<>();
        
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(5000);
                deferredResult.setResult(ResponseEntity.ok("Deferred operation completed"));
            } catch (InterruptedException e) {
                deferredResult.setErrorResult(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error"));
            }
        });
        
        return deferredResult;
    }
}
```

## 五、完整示例：用户管理系统

### 5.1 项目结构
```
src/main/java/com/example/demo/
├── config/              # 配置类
├── controller/          # 控制器
├── dto/                # 数据传输对象
├── exception/          # 异常类
├── interceptor/        # 拦截器
├── service/            # 业务层
└── entity/             # 实体类
```

### 5.2 完整控制器示例
```java
@RestController
@RequestMapping("/api/v1/users")
@Validated
@Tag(name = "用户管理")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private ModelMapper modelMapper;
    
    // 查询用户列表
    @GetMapping
    @Operation(summary = "查询用户列表")
    public ResponseEntity<Page<UserDTO>> getUsers(
            @ParameterObject Pageable pageable,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email) {
        
        Page<User> users = userService.findUsers(pageable, name, email);
        Page<UserDTO> userDTOs = users.map(user -> modelMapper.map(user, UserDTO.class));
        
        return ResponseEntity.ok(userDTOs);
    }
    
    // 查询单个用户
    @GetMapping("/{id}")
    @Operation(summary = "查询用户详情")
    public ResponseEntity<UserDTO> getUser(@PathVariable Long id) {
        User user = userService.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id.toString()));
        
        UserDTO userDTO = modelMapper.map(user, UserDTO.class);
        return ResponseEntity.ok(userDTO);
    }
    
    // 创建用户
    @PostMapping
    @Operation(summary = "创建用户")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UserDTO> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        
        User user = modelMapper.map(request, User.class);
        User created = userService.create(user);
        
        UserDTO userDTO = modelMapper.map(created, UserDTO.class);
        return ResponseEntity
                .created(URI.create("/api/v1/users/" + created.getId()))
                .body(userDTO);
    }
    
    // 更新用户
    @PutMapping("/{id}")
    @Operation(summary = "更新用户")
    public ResponseEntity<UserDTO> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        
        User user = modelMapper.map(request, User.class);
        user.setId(id);
        
        User updated = userService.update(user);
        UserDTO userDTO = modelMapper.map(updated, UserDTO.class);
        
        return ResponseEntity.ok(userDTO);
    }
    
    // 删除用户
    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
    
    // 用户登录
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {
        
        String token = userService.login(request.getUsername(), request.getPassword());
        
        LoginResponse response = LoginResponse.builder()
                .token(token)
                .expiresIn(3600)
                .tokenType("Bearer")
                .build();
        
        return ResponseEntity.ok(response);
    }
}
```

### 5.3 配置类
```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LogInterceptor())
                .addPathPatterns("/api/**");
        
        registry.addInterceptor(new AuthInterceptor())
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/users/login", "/api/v1/users/register");
    }
    
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 配置JSON转换器
        converters.add(new MappingJackson2HttpMessageConverter());
    }
    
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOrigin("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        source.registerCorsConfiguration("/**", config);
        
        FilterRegistrationBean<CorsFilter> bean = 
            new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(0);
        return bean;
    }
}
```

## 六、性能优化建议

### 6.1 缓存策略
```java
@RestController
@RequestMapping("/api/v1/users")
@CacheConfig(cacheNames = "users")
public class UserController {
    
    @GetMapping("/{id}")
    @Cacheable(key = "#id", unless = "#result == null")
    public UserDTO getUser(@PathVariable Long id) {
        // ...
    }
    
    @PutMapping("/{id}")
    @CachePut(key = "#id")
    public UserDTO updateUser(@PathVariable Long id, @RequestBody User user) {
        // ...
    }
    
    @DeleteMapping("/{id}")
    @CacheEvict(key = "#id")
    public void deleteUser(@PathVariable Long id) {
        // ...
    }
}
```

### 6.2 异步处理优化
```java
@RestController
@RequestMapping("/api/v1/async")
public class AsyncController {
    
    @GetMapping("/users/report")
    public CompletableFuture<ResponseEntity<Resource>> generateReport() {
        return CompletableFuture.supplyAsync(() -> {
            // 生成报表
            byte[] data = reportService.generateUserReport();
            ByteArrayResource resource = new ByteArrayResource(data);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                           "attachment; filename=users_report.pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);
        });
    }
}
```