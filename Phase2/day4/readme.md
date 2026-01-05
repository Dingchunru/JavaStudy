# MyBatis 深入解析

## 一、核心架构与源码解析

### 1.1 MyBatis 整体架构
```
┌─────────────────────────────────────────────────────────┐
│                   接口层 (API Layer)                     │
│  SqlSession、SqlSessionFactory、SqlSessionFactoryBuilder │
└─────────────────────────────────────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────┐
│                   核心处理层 (Core Layer)                │
│  配置解析、SQL解析、SQL执行、结果集映射、插件机制       │
└─────────────────────────────────────────────────────────┘
                             │
┌─────────────────────────────────────────────────────────┘
│                   基础支撑层 (Base Layer)                │
│  数据源、事务管理、日志、缓存、类型转换、资源加载       │
└─────────────────────────────────────────────────────────┘
```

### 1.2 核心源码类分析

#### SqlSessionFactoryBuilder
```java
public class SqlSessionFactoryBuilder {
    // 构建SqlSessionFactory的核心方法
    public SqlSessionFactory build(InputStream inputStream) {
        return build(inputStream, null, null);
    }
    
    public SqlSessionFactory build(InputStream inputStream, String environment, Properties properties) {
        try {
            // 1. 创建XMLConfigBuilder解析配置文件
            XMLConfigBuilder parser = new XMLConfigBuilder(
                inputStream, environment, properties);
            
            // 2. 解析配置并构建Configuration对象
            Configuration config = parser.parse();
            
            // 3. 创建DefaultSqlSessionFactory
            return build(config);
        } finally {
            ErrorContext.instance().reset();
        }
    }
}
```

#### Configuration - 配置信息中心
```java
public class Configuration {
    // 环境信息
    protected Environment environment;
    
    // 映射注册中心
    protected final MapperRegistry mapperRegistry = new MapperRegistry(this);
    
    // 缓存管理
    protected final Map<String, Cache> caches = new StrictMap<>("Caches collection");
    
    // 结果映射
    protected final Map<String, ResultMap> resultMaps = new StrictMap<>();
    
    // SQL语句存储
    protected final Map<String, MappedStatement> mappedStatements = new StrictMap<>();
    
    // 类型处理器
    protected final TypeHandlerRegistry typeHandlerRegistry = new TypeHandlerRegistry();
    
    // 插件链
    protected final InterceptorChain interceptorChain = new InterceptorChain();
    
    // 执行器类型
    protected ExecutorType defaultExecutorType = ExecutorType.SIMPLE;
}
```

#### SqlSession 执行流程
```java
public class DefaultSqlSession implements SqlSession {
    private final Configuration configuration;
    private final Executor executor;
    
    @Override
    public <T> T selectOne(String statement, Object parameter) {
        // 1. 获取MappedStatement
        MappedStatement ms = configuration.getMappedStatement(statement);
        
        // 2. 委托给Executor执行
        return executor.query(ms, wrapCollection(parameter), 
            RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER);
    }
}
```

#### Executor 执行器体系
```java
// 执行器接口
public interface Executor {
    // 查询方法
    <E> List<E> query(MappedStatement ms, Object parameter, 
                     RowBounds rowBounds, ResultHandler resultHandler);
    
    // 更新方法
    int update(MappedStatement ms, Object parameter);
}

// 执行器实现类
public abstract class BaseExecutor implements Executor {
    // 一级缓存
    protected PerpetualCache localCache;
    
    // 事务管理
    protected Transaction transaction;
}

// 简单执行器（默认）
public class SimpleExecutor extends BaseExecutor {
    @Override
    public <E> List<E> doQuery(MappedStatement ms, Object parameter,
                              RowBounds rowBounds, ResultHandler resultHandler) {
        Statement stmt = null;
        try {
            Configuration configuration = ms.getConfiguration();
            // 创建StatementHandler
            StatementHandler handler = configuration.newStatementHandler(
                this, ms, parameter, rowBounds, resultHandler);
            // 准备Statement
            stmt = prepareStatement(handler);
            // 执行查询
            return handler.query(stmt, resultHandler);
        } finally {
            closeStatement(stmt);
        }
    }
}
```

### 1.3 插件机制（Interceptor）
```java
// 插件接口定义
@Intercepts({
    @Signature(type = Executor.class, method = "query", 
              args = {MappedStatement.class, Object.class, 
                     RowBounds.class, ResultHandler.class})
})
public class ExamplePlugin implements Interceptor {
    
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 前置处理
        System.out.println("Before executing query");
        
        // 执行目标方法
        Object result = invocation.proceed();
        
        // 后置处理
        System.out.println("After executing query");
        
        return result;
    }
    
    @Override
    public Object plugin(Object target) {
        // 创建代理对象
        return Plugin.wrap(target, this);
    }
}
```

## 二、动态SQL深度解析

### 2.1 动态SQL实现原理

#### SqlNode 体系结构
```java
// SQL节点接口
public interface SqlNode {
    boolean apply(DynamicContext context);
}

// 混合SQL节点（包含多个子节点）
public class MixedSqlNode implements SqlNode {
    private final List<SqlNode> contents;
    
    @Override
    public boolean apply(DynamicContext context) {
        for (SqlNode sqlNode : contents) {
            sqlNode.apply(context);
        }
        return true;
    }
}

// If节点实现
public class IfSqlNode implements SqlNode {
    private final ExpressionEvaluator evaluator;
    private final String test;
    private final SqlNode contents;
    
    @Override
    public boolean apply(DynamicContext context) {
        if (evaluator.evaluateBoolean(test, context.getBindings())) {
            contents.apply(context);
            return true;
        }
        return false;
    }
}
```

#### DynamicContext 动态上下文
```java
public class DynamicContext {
    // 参数绑定上下文
    private final ContextMap bindings;
    
    // SQL构建器
    private final StringBuilder sqlBuilder = new StringBuilder();
    
    // 添加SQL片段
    public void appendSql(String sql) {
        sqlBuilder.append(sql);
        sqlBuilder.append(" ");
    }
    
    // 获取完整SQL
    public String getSql() {
        return sqlBuilder.toString().trim();
    }
}
```

### 2.2 OGNL表达式引擎
```java
// OGNL表达式求值器
public class ExpressionEvaluator {
    
    // 评估布尔表达式
    public boolean evaluateBoolean(String expression, Object parameterObject) {
        Object value = OgnlCache.getValue(expression, parameterObject);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return !new BigDecimal(String.valueOf(value))
                .equals(BigDecimal.ZERO);
        }
        return value != null;
    }
    
    // 评估迭代表达式
    public Iterable<?> evaluateIterable(String expression, 
                                       Object parameterObject) {
        Object value = OgnlCache.getValue(expression, parameterObject);
        if (value == null) {
            throw new BuilderException("...");
        }
        if (value instanceof Iterable) {
            return (Iterable<?>) value;
        }
        if (value.getClass().isArray()) {
            return Arrays.asList((Object[]) value);
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).entrySet();
        }
        throw new BuilderException("...");
    }
}
```

### 2.3 动态SQL标签解析

#### XMLScriptBuilder 解析过程
```java
public class XMLScriptBuilder extends BaseBuilder {
    
    // 解析动态SQL节点
    public SqlNode parseScriptNode() {
        List<SqlNode> contents = parseDynamicTags(context);
        MixedSqlNode rootSqlNode = new MixedSqlNode(contents);
        
        // 根据是否包含动态标签选择不同的SqlSource
        if (isDynamic) {
            return new DynamicSqlSource(configuration, rootSqlNode);
        } else {
            return new RawSqlSource(configuration, rootSqlNode, parameterType);
        }
    }
    
    // 解析动态标签
    List<SqlNode> parseDynamicTags(XNode node) {
        List<SqlNode> contents = new ArrayList<>();
        
        for (XNode child : node.getChildren()) {
            String nodeName = child.getNode().getNodeName();
            
            // 处理不同的动态SQL标签
            if ("if".equals(nodeName)) {
                processIfNode(child, contents);
            } else if ("choose".equals(nodeName)) {
                processChooseNode(child, contents);
            } else if ("foreach".equals(nodeName)) {
                processForEachNode(child, contents);
            } else if ("trim".equals(nodeName)) {
                processTrimNode(child, contents);
            } else {
                // 静态文本节点
                contents.add(new StaticTextSqlNode(child.getStringBody()));
            }
        }
        return contents;
    }
}
```

### 2.4 高级动态SQL示例

#### 动态UPDATE优化
```xml
<update id="updateSelective">
    UPDATE user
    <set>
        <if test="username != null and username != ''">
            username = #{username},
        </if>
        <if test="email != null">
            email = #{email},
        </if>
        <if test="status != null">
            status = #{status},
        </if>
        <if test="updateTime != null">
            update_time = #{updateTime}
        </if>
    </set>
    WHERE id = #{id}
</update>

<!-- set标签的底层实现原理 -->
<trim prefix="SET" suffixOverrides=",">
    <!-- if条件内容 -->
</trim>
```

#### 复杂条件查询
```xml
<select id="findByCondition" resultType="User">
    SELECT * FROM user
    <where>
        <!-- 使用choose处理互斥条件 -->
        <choose>
            <when test="type == 'admin'">
                AND role = 'ADMIN'
            </when>
            <when test="type == 'vip'">
                AND vip_level > 0
                AND status = 1
            </when>
            <otherwise>
                AND status = 1
            </otherwise>
        </choose>
        
        <!-- 时间范围查询 -->
        <if test="startTime != null and endTime != null">
            AND create_time BETWEEN #{startTime} AND #{endTime}
        </if>
        
        <!-- IN查询优化 -->
        <if test="ids != null and ids.size() > 0">
            AND id IN
            <foreach collection="ids" item="id" 
                     open="(" separator="," close=")">
                #{id}
            </foreach>
        </if>
        
        <!-- 动态排序 -->
        <if test="orderBy != null">
            ORDER BY ${orderBy}
            <if test="orderDirection != null">
                ${orderDirection}
            </if>
        </if>
    </where>
</select>
```

## 三、缓存机制深度解析

### 3.1 缓存体系架构

```java
// 缓存接口
public interface Cache {
    String getId();
    void putObject(Object key, Object value);
    Object getObject(Object key);
    Object removeObject(Object key);
    void clear();
    int getSize();
    ReadWriteLock getReadWriteLock();
}

// 缓存实现类继承体系
PerpetualCache    // 永久缓存（HashMap实现）
    ↓ 装饰器模式
LruCache          // LRU淘汰策略
FifoCache         // FIFO淘汰策略
SoftCache         // 软引用缓存
WeakCache         // 弱引用缓存
    ↓ 功能增强
ScheduledCache    // 调度缓存
SerializedCache   // 序列化缓存
LoggingCache      // 日志缓存
SynchronizedCache // 同步缓存
BlockingCache     // 阻塞缓存
TransactionalCache// 事务缓存
```

### 3.2 一级缓存（Local Cache）

#### 实现原理
```java
public abstract class BaseExecutor implements Executor {
    
    // 一级缓存（PerpetualCache实现）
    protected PerpetualCache localCache;
    
    // 本地输出参数缓存
    protected PerpetualCache localOutputParameterCache;
    
    // 查询操作（带一级缓存）
    @Override
    public <E> List<E> query(MappedStatement ms, Object parameter, 
                            RowBounds rowBounds, ResultHandler resultHandler) {
        // 1. 获取BoundSql
        BoundSql boundSql = ms.getBoundSql(parameter);
        
        // 2. 创建缓存Key
        CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);
        
        // 3. 执行查询（检查一级缓存）
        return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
    }
    
    // 实际查询方法
    public <E> List<E> query(MappedStatement ms, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler,
                            CacheKey key, BoundSql boundSql) {
        // 检查一级缓存
        List<E> list = (List<E>) localCache.getObject(key);
        if (list != null) {
            // 命中缓存，处理存储过程输出参数
            handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
        } else {
            // 未命中，从数据库查询
            list = queryFromDatabase(ms, parameter, rowBounds, 
                                    resultHandler, key, boundSql);
        }
        return list;
    }
}
```

#### 缓存Key的生成
```java
public class CacheKey implements Cloneable, Serializable {
    
    // 影响缓存Key的因素
    private static final int DEFAULT_MULTIPLIER = 37;
    private static final int DEFAULT_HASHCODE = 17;
    
    private final int multiplier;
    private int hashcode;
    private long checksum;
    private int count;
    
    // 更新缓存Key
    public void update(Object object) {
        int baseHashCode = object == null ? 1 : 
            ArrayUtil.hashCode(object);
        
        count++;
        checksum += baseHashCode;
        baseHashCode *= count;
        
        hashcode = multiplier * hashcode + baseHashCode;
        
        // 添加对象到列表
        updateList.add(object);
    }
    
    // CacheKey包含以下要素：
    // 1. MappedStatement的id
    // 2. 分页参数（RowBounds）
    // 3. 查询SQL（BoundSql）
    // 4. 参数值
    // 5. 环境id
}
```

### 3.3 二级缓存（Second Level Cache）

#### 配置与启用
```xml
<!-- mybatis-config.xml -->
<settings>
    <!-- 启用二级缓存（默认true） -->
    <setting name="cacheEnabled" value="true"/>
</settings>

<!-- Mapper XML中配置 -->
<cache
  eviction="LRU"
  flushInterval="60000"
  size="512"
  readOnly="true"/>
```

#### 二级缓存实现
```java
// CachingExecutor 装饰器模式
public class CachingExecutor implements Executor {
    
    private final Executor delegate;
    private final TransactionalCacheManager tcm = 
        new TransactionalCacheManager();
    
    @Override
    public <E> List<E> query(MappedStatement ms, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler,
                            CacheKey key, BoundSql boundSql) {
        // 获取MappedStatement的缓存
        Cache cache = ms.getCache();
        
        if (cache != null) {
            // 如果需要刷新缓存
            flushCacheIfRequired(ms);
            
            if (ms.isUseCache() && resultHandler == null) {
                // 确保没有输出参数
                ensureNoOutParams(ms, boundSql);
                
                // 从二级缓存获取
                @SuppressWarnings("unchecked")
                List<E> list = (List<E>) tcm.getObject(cache, key);
                
                if (list == null) {
                    // 缓存未命中，委托给实际执行器查询
                    list = delegate.query(ms, parameter, rowBounds, 
                                        resultHandler, key, boundSql);
                    // 放入二级缓存
                    tcm.putObject(cache, key, list);
                }
                return list;
            }
        }
        // 无缓存配置，直接委托查询
        return delegate.query(ms, parameter, rowBounds, 
                            resultHandler, key, boundSql);
    }
}
```

#### 事务性缓存管理
```java
public class TransactionalCacheManager {
    
    // 事务缓存映射
    private final Map<Cache, TransactionalCache> 
        transactionalCaches = new HashMap<>();
    
    public void clear(Cache cache) {
        getTransactionalCache(cache).clear();
    }
    
    public Object getObject(Cache cache, CacheKey key) {
        return getTransactionalCache(cache).getObject(key);
    }
    
    public void putObject(Cache cache, CacheKey key, Object value) {
        getTransactionalCache(cache).putObject(key, value);
    }
    
    // 提交时将所有缓存写入实际缓存
    public void commit() {
        for (TransactionalCache txCache : transactionalCaches.values()) {
            txCache.commit();
        }
    }
    
    // 回滚时清除所有缓存
    public void rollback() {
        for (TransactionalCache txCache : transactionalCaches.values()) {
            txCache.rollback();
        }
    }
}
```

### 3.4 缓存同步策略

#### 脏读问题解决
```java
public class TransactionalCache implements Cache {
    
    // 实际缓存
    private final Cache delegate;
    
    // 提交时是否清空
    private boolean clearOnCommit;
    
    // 待提交的条目
    private final Map<Object, Object> entriesToAddOnCommit = new HashMap<>();
    
    @Override
    public void putObject(Object key, Object object) {
        // 暂存到entriesToAddOnCommit，事务提交后才真正写入缓存
        entriesToAddOnCommit.put(key, object);
    }
    
    @Override
    public Object getObject(Object key) {
        Object object = delegate.getObject(key);
        if (object == null) {
            // 从未提交的条目中查找
            object = entriesToAddOnCommit.get(key);
        }
        return object;
    }
    
    public void commit() {
        if (clearOnCommit) {
            delegate.clear();
        }
        // 将暂存条目写入实际缓存
        flushPendingEntries();
        reset();
    }
}
```

## 四、分页插件开发实战

### 4.1 分页插件设计原理

#### 插件拦截点选择
```java
// 最佳拦截点：StatementHandler.prepare
@Intercepts({
    @Signature(type = StatementHandler.class, 
               method = "prepare", 
               args = {Connection.class, Integer.class})
})
public class PageInterceptor implements Interceptor {
    
    private Dialect dialect;
    
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = (StatementHandler) 
            invocation.getTarget();
        
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
        
        // 获取原始的MappedStatement
        MappedStatement mappedStatement = (MappedStatement) 
            metaObject.getValue("delegate.mappedStatement");
        
        // 获取分页参数
        Object parameterObject = statementHandler
            .getBoundSql().getParameterObject();
        
        // 判断是否需要分页
        PageParam pageParam = getPageParam(parameterObject);
        if (pageParam != null) {
            // 进行分页处理
            return processPage(invocation, mappedStatement, 
                              parameterObject, pageParam);
        }
        
        // 不需要分页，直接执行
        return invocation.proceed();
    }
}
```

### 4.2 完整分页插件实现

#### 分页参数类
```java
// 分页参数基类
public class PageParam {
    private Integer pageNum = 1;     // 当前页码
    private Integer pageSize = 10;   // 每页大小
    private Boolean count = true;    // 是否进行count查询
    private String orderBy;          // 排序字段
    private Boolean reasonable = true; // 分页合理化
    
    // 计算offset
    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }
    
    // 校验参数
    public void validate() {
        if (pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize < 1) {
            pageSize = 10;
        }
        if (pageSize > 500) {
            pageSize = 500; // 防止过大的分页
        }
    }
}

// 分页结果类
public class PageInfo<T> implements Serializable {
    private List<T> list;           // 数据列表
    private long total;             // 总记录数
    private int pageNum;            // 当前页
    private int pageSize;           // 每页数量
    private int pages;              // 总页数
    private int prePage;            // 上一页
    private int nextPage;           // 下一页
    private boolean isFirstPage;    // 是否第一页
    private boolean isLastPage;     // 是否最后一页
    
    public PageInfo(List<T> list) {
        this.list = list;
    }
    
    // 计算分页信息
    public void calcByTotal(long total) {
        this.total = total;
        this.pages = (int) ((total + pageSize - 1) / pageSize);
        
        // 边界处理
        this.pageNum = Math.max(1, Math.min(pageNum, pages));
        this.prePage = Math.max(1, pageNum - 1);
        this.nextPage = Math.min(pages, pageNum + 1);
        
        this.isFirstPage = pageNum == 1;
        this.isLastPage = pageNum == pages;
    }
}
```

#### 数据库方言抽象
```java
// 方言接口
public interface Dialect {
    
    // 是否支持分页
    boolean supportsLimit();
    
    // 获取分页SQL
    String getLimitString(String sql, int offset, int limit);
    
    // 获取Count SQL
    String getCountString(String sql);
}

// MySQL方言实现
public class MySqlDialect implements Dialect {
    
    @Override
    public boolean supportsLimit() {
        return true;
    }
    
    @Override
    public String getLimitString(String sql, int offset, int limit) {
        StringBuilder builder = new StringBuilder(sql);
        builder.append(" LIMIT ");
        if (offset > 0) {
            builder.append(offset).append(",").append(limit);
        } else {
            builder.append(limit);
        }
        return builder.toString();
    }
    
    @Override
    public String getCountString(String sql) {
        // 移除ORDER BY子句
        String countSql = removeOrders(sql);
        return "SELECT COUNT(*) FROM (" + countSql + ") tmp_count";
    }
    
    private String removeOrders(String sql) {
        Pattern pattern = Pattern.compile(
            "order\\s+by[\\s\\S]*$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}

// Oracle方言实现
public class OracleDialect implements Dialect {
    
    @Override
    public String getLimitString(String sql, int offset, int limit) {
        sql = sql.trim();
        boolean hasOffset = offset > 0;
        
        StringBuilder builder = new StringBuilder(sql.length() + 100);
        if (hasOffset) {
            builder.append("SELECT * FROM ( SELECT row_.*, rownum rownum_ FROM ( ");
        } else {
            builder.append("SELECT * FROM ( ");
        }
        builder.append(sql);
        if (hasOffset) {
            builder.append(" ) row_ WHERE rownum <= ")
                   .append(offset + limit)
                   .append(") WHERE rownum_ > ")
                   .append(offset);
        } else {
            builder.append(" ) WHERE rownum <= ").append(limit);
        }
        return builder.toString();
    }
}
```

#### 完整分页拦截器
```java
@Intercepts({
    @Signature(type = Executor.class, method = "query", 
               args = {MappedStatement.class, Object.class, 
                      RowBounds.class, ResultHandler.class}),
    @Signature(type = Executor.class, method = "query", 
               args = {MappedStatement.class, Object.class, 
                      RowBounds.class, ResultHandler.class, 
                      CacheKey.class, BoundSql.class})
})
public class PageInterceptor implements Interceptor {
    
    private static final ThreadLocal<PageParam> PAGE_PARAM_THREAD_LOCAL = 
        new ThreadLocal<>();
    
    private final Map<String, Dialect> dialectMap = new HashMap<>();
    
    public PageInterceptor() {
        // 初始化方言
        dialectMap.put("mysql", new MySqlDialect());
        dialectMap.put("oracle", new OracleDialect());
        dialectMap.put("postgresql", new PostgreSQLDialect());
    }
    
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        try {
            Object[] args = invocation.getArgs();
            MappedStatement ms = (MappedStatement) args[0];
            Object parameter = args[1];
            
            // 获取分页参数
            PageParam pageParam = getPageParam(parameter);
            if (pageParam == null) {
                return invocation.proceed();
            }
            
            // 存储分页参数到ThreadLocal
            PAGE_PARAM_THREAD_LOCAL.set(pageParam);
            
            // 获取数据库方言
            Configuration configuration = ms.getConfiguration();
            String databaseId = configuration.getDatabaseId();
            Dialect dialect = getDialect(databaseId);
            
            // 执行分页查询
            return doPageQuery(invocation, ms, parameter, pageParam, dialect);
            
        } finally {
            // 清理ThreadLocal
            PAGE_PARAM_THREAD_LOCAL.remove();
        }
    }
    
    private Object doPageQuery(Invocation invocation, MappedStatement ms, 
                              Object parameter, PageParam pageParam, 
                              Dialect dialect) throws Throwable {
        
        // 1. 查询总数
        long total = 0;
        if (pageParam.getCount()) {
            total = queryTotal(invocation, ms, parameter, dialect);
            if (total == 0) {
                return new ArrayList<>();
            }
        }
        
        // 2. 执行分页查询
        Object result = invocation.proceed();
        
        // 3. 封装分页结果
        if (result instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) result;
            
            PageInfo<Object> pageInfo = new PageInfo<>(list);
            pageInfo.setPageNum(pageParam.getPageNum());
            pageInfo.setPageSize(pageParam.getPageSize());
            pageInfo.setTotal(total);
            
            return pageInfo;
        }
        
        return result;
    }
    
    private long queryTotal(Invocation invocation, MappedStatement ms,
                           Object parameter, Dialect dialect) throws Throwable {
        
        // 创建Count查询的MappedStatement
        MappedStatement countMs = createCountMappedStatement(ms, dialect);
        
        // 修改参数
        Object[] args = invocation.getArgs();
        Object[] newArgs = Arrays.copyOf(args, args.length);
        newArgs[0] = countMs; // 替换为count的MappedStatement
        
        // 执行Count查询
        Executor executor = (Executor) invocation.getTarget();
        List<Object> result = executor.query(
            countMs, parameter, RowBounds.DEFAULT, 
            Executor.NO_RESULT_HANDLER);
        
        if (result != null && !result.isEmpty()) {
            Object count = result.get(0);
            if (count instanceof Number) {
                return ((Number) count).longValue();
            }
        }
        return 0;
    }
    
    private MappedStatement createCountMappedStatement(
            MappedStatement ms, Dialect dialect) {
        
        String countId = ms.getId() + "_COUNT";
        if (ms.getConfiguration().hasStatement(countId)) {
            return ms.getConfiguration().getMappedStatement(countId);
        }
        
        // 创建新的MappedStatement
        BoundSql boundSql = ms.getBoundSql(null);
        String originalSql = boundSql.getSql();
        String countSql = dialect.getCountString(originalSql);
        
        // 构建新的BoundSql
        BoundSql countBoundSql = new BoundSql(
            ms.getConfiguration(), countSql, 
            boundSql.getParameterMappings(), 
            boundSql.getParameterObject());
        
        // 复制参数映射
        for (ParameterMapping mapping : boundSql.getParameterMappings()) {
            countBoundSql.setAdditionalParameter(
                mapping.getProperty(), 
                boundSql.getAdditionalParameter(mapping.getProperty()));
        }
        
        // 创建新的MappedStatement
        MappedStatement.Builder builder = new MappedStatement.Builder(
            ms.getConfiguration(), countId, 
            new SqlSource() {
                @Override
                public BoundSql getBoundSql(Object parameterObject) {
                    return countBoundSql;
                }
            }, ms.getSqlCommandType());
        
        builder.resource(ms.getResource())
               .fetchSize(ms.getFetchSize())
               .statementType(ms.getStatementType())
               .keyGenerator(ms.getKeyGenerator())
               .timeout(ms.getTimeout())
               .parameterMap(ms.getParameterMap())
               .resultMaps(ms.getResultMaps())
               .cache(ms.getCache())
               .useCache(ms.isUseCache());
        
        return builder.build();
    }
    
    // 获取分页参数
    private PageParam getPageParam(Object parameter) {
        if (parameter instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> paramMap = (Map<String, Object>) parameter;
            for (Object value : paramMap.values()) {
                if (value instanceof PageParam) {
                    return (PageParam) value;
                }
            }
        } else if (parameter instanceof PageParam) {
            return (PageParam) parameter;
        }
        
        return PAGE_PARAM_THREAD_LOCAL.get();
    }
    
    // 获取方言
    private Dialect getDialect(String databaseId) {
        if (databaseId != null && dialectMap.containsKey(databaseId)) {
            return dialectMap.get(databaseId);
        }
        // 默认返回MySQL方言
        return dialectMap.get("mysql");
    }
    
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
    
    @Override
    public void setProperties(Properties properties) {
        // 从配置中读取属性
    }
}
```

### 4.3 使用示例

#### Mapper接口
```java
public interface UserMapper {
    // 分页查询方法
    PageInfo<User> selectByPage(@Param("page") PageParam pageParam, 
                               @Param("condition") UserCondition condition);
    
    // 使用RowBounds方式
    List<User> selectByRowBounds(UserCondition condition, RowBounds rowBounds);
}
```

#### Service层调用
```java
@Service
public class UserService {
    
    @Autowired
    private UserMapper userMapper;
    
    public PageInfo<User> findUsers(int pageNum, int pageSize, 
                                   UserCondition condition) {
        // 创建分页参数
        PageParam pageParam = new PageParam();
        pageParam.setPageNum(pageNum);
        pageParam.setPageSize(pageSize);
        pageParam.setCount(true);
        pageParam.setReasonable(true);
        
        // 执行分页查询
        return userMapper.selectByPage(pageParam, condition);
    }
    
    // 使用RowBounds方式
    public List<User> findUsersByRowBounds(int offset, int limit, 
                                          UserCondition condition) {
        return userMapper.selectByRowBounds(condition, 
            new RowBounds(offset, limit));
    }
}
```

### 4.4 性能优化建议

#### 1. 大表分页优化
```sql
-- 传统分页（大数据量时性能差）
SELECT * FROM large_table ORDER BY id LIMIT 1000000, 20;

-- 优化方案1：使用覆盖索引
SELECT * FROM large_table 
WHERE id >= (SELECT id FROM large_table ORDER BY id LIMIT 1000000, 1)
ORDER BY id LIMIT 20;

-- 优化方案2：记录上次查询的最大ID
SELECT * FROM large_table 
WHERE id > #{lastMaxId}
ORDER BY id LIMIT 20;
```

#### 2. Count查询优化
```java
// 可选的总数查询策略
public enum CountStrategy {
    AUTO,           // 自动选择
    RAW_SQL,        // 使用COUNT(*)
    CACHE,          // 缓存总数
    APPROXIMATE,    // 估算（如EXPLAIN）
    NONE            // 不查询总数
}

// 在分页插件中实现缓存
public class CachedCountStrategy {
    private final Cache countCache;
    private final long cacheTimeout;
    
    public long getCachedCount(String cacheKey, Supplier<Long> countSupplier) {
        Object cached = countCache.getObject(cacheKey);
        if (cached != null) {
            return (Long) cached;
        }
        
        long count = countSupplier.get();
        countCache.putObject(cacheKey, count);
        
        // 定时清除缓存
        scheduleCacheCleanup(cacheKey);
        
        return count;
    }
}
```

## 五、高级特性与最佳实践

### 5.1 批量操作优化

```java
// BatchExecutor批量执行器
public class BatchExecutor extends BaseExecutor {
    
    private final List<Statement> statementList = new ArrayList<>();
    private final List<BatchResult> batchResultList = new ArrayList<>();
    private String currentSql;
    private MappedStatement currentStatement;
    
    @Override
    public int doUpdate(MappedStatement ms, Object parameterObject) {
        final Configuration configuration = ms.getConfiguration();
        final StatementHandler handler = configuration.newStatementHandler(
            this, ms, parameterObject, RowBounds.DEFAULT, null);
        
        final BoundSql boundSql = handler.getBoundSql();
        final String sql = boundSql.getSql();
        
        if (sql.equals(currentSql) && ms.equals(currentStatement)) {
            // 相同SQL，添加到批处理
            statementList.get(statementList.size() - 1)
                .addBatch(getParameterObject(parameterObject, boundSql));
        } else {
            // 不同SQL，创建新的Statement
            currentSql = sql;
            currentStatement = ms;
            
            Statement stmt = prepareStatement(handler);
            stmt.addBatch(getParameterObject(parameterObject, boundSql));
            statementList.add(stmt);
        }
        
        return BATCH_UPDATE_RETURN_VALUE;
    }
    
    @Override
    public List<BatchResult> doFlushStatements(boolean isRollback) {
        try {
            List<BatchResult> results = new ArrayList<>();
            
            for (int i = 0; i < statementList.size(); i++) {
                Statement stmt = statementList.get(i);
                int[] updateCounts = stmt.executeBatch();
                
                BatchResult batchResult = new BatchResult(
                    currentStatement, currentSql, updateCounts);
                results.add(batchResult);
            }
            
            return results;
        } finally {
            closeStatements();
        }
    }
}
```

### 5.2 延迟加载（Lazy Loading）

```java
// 延迟加载代理
public class LazyLoader implements Serializable {
    
    private final Configuration configuration;
    private final ResultLoaderMap loaderMap;
    
    // 创建代理对象
    public static Object createProxy(Object target, 
                                    Configuration configuration,
                                    ResultLoaderMap loaderMap) {
        List<Class<?>> interfaces = new ArrayList<>();
        interfaces.add(Serializable.class);
        
        if (target instanceof Collection) {
            interfaces.add(Collection.class);
        } else if (target instanceof List) {
            interfaces.add(List.class);
        } else if (target instanceof Set) {
            interfaces.add(Set.class);
        } else if (target instanceof Map) {
            interfaces.add(Map.class);
        }
        
        return Proxy.newProxyInstance(
            target.getClass().getClassLoader(),
            interfaces.toArray(new Class<?>[interfaces.size()]),
            new LazyLoaderInvocationHandler(target, configuration, loaderMap));
    }
}

// 延迟加载处理器
private static class LazyLoaderInvocationHandler 
    implements InvocationHandler {
    
    private Object target;
    private final Configuration configuration;
    private final ResultLoaderMap loaderMap;
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) 
        throws Throwable {
        
        final String methodName = method.getName();
        
        // 触发延迟加载
        if ("writeReplace".equals(methodName)) {
            return this;
        } else if (loaderMap.size() > 0 && !FINALIZE_METHOD.equals(methodName)) {
            if (aggressive || lazyLoadTriggerMethods.contains(methodName)) {
                // 触发所有延迟加载
                loadAll();
            } else if (PropertyNamer.isProperty(methodName)) {
                // 触发特定属性的延迟加载
                final String property = PropertyNamer.methodToProperty(methodName);
                if (loaderMap.hasLoader(property)) {
                    load(property);
                }
            }
        }
        
        return method.invoke(target, args);
    }
}
```

### 5.3 多数据源支持

```java
// 动态数据源路由
public class DynamicDataSource extends AbstractRoutingDataSource {
    
    private static final ThreadLocal<String> dataSourceKey = 
        new InheritableThreadLocal<>();
    
    @Override
    protected Object determineCurrentLookupKey() {
        return dataSourceKey.get();
    }
    
    public static void setDataSource(String dataSource) {
        dataSourceKey.set(dataSource);
    }
    
    public static void clearDataSource() {
        dataSourceKey.remove();
    }
}

// 使用AOP切换数据源
@Aspect
@Component
public class DataSourceAspect {
    
    @Before("@annotation(targetDataSource)")
    public void changeDataSource(JoinPoint point, 
                                TargetDataSource targetDataSource) {
        String dsName = targetDataSource.value();
        if (!DynamicDataSourceHolder.containsDataSource(dsName)) {
            throw new DataSourceNotFoundException("数据源不存在: " + dsName);
        }
        DynamicDataSourceHolder.setDataSource(dsName);
    }
    
    @After("@annotation(targetDataSource)")
    public void restoreDataSource(JoinPoint point, 
                                 TargetDataSource targetDataSource) {
        DynamicDataSourceHolder.clearDataSource();
    }
}
```

## 总结

MyBatis作为一个优秀的持久层框架，其核心优势在于：

1. **灵活的SQL控制**：动态SQL功能强大，可以精确控制SQL执行
2. **优秀的缓存机制**：多级缓存设计合理，平衡性能与数据一致性
3. **可扩展的插件体系**：通过拦截器可以轻松扩展功能
4. **良好的性能表现**：合理的默认配置和优化选项

在实际开发中，建议：
- 合理使用一级缓存，注意Session生命周期
- 谨慎使用二级缓存，注意数据一致性
- 开发插件时注意线程安全和性能影响
- 分页查询要考虑大数据量的优化方案
- 充分利用MyBatis的延迟加载提升性能

通过深入理解MyBatis的内部机制，我们可以更好地使用和扩展它，构建高性能的数据访问层。