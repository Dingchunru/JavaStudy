# 现代可观测性与容器化技术深度解析

## 一、链路追踪：SkyWalking

### 1.1 核心概念

**分布式链路追踪**是一种用于分析和监控分布式系统性能的关键技术。SkyWalking作为APM（应用性能管理）领域的佼佼者，提供了完整的解决方案。

### 1.2 架构设计

```
┌─────────────────────────────────────────────────┐
│                    Agent Layer                   │
├──────────────┬──────────────┬───────────────────┤
│ Java Agent   │ .NET Agent   │ 其他语言探针     │
└──────────────┴──────────────┴───────────────────┘
                         ↓
┌─────────────────────────────────────────────────┐
│                Collector Cluster                 │
├─────────────────┬───────────────────────────────┤
│ Trace Receiver  │ Metrics Processor             │
│ JVM Analyzer    │ Service Topology Builder      │
└─────────────────┴───────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────┐
│                  Storage Layer                   │
├─────────────────┬───────────────────────────────┤
│ Elasticsearch   │ H2 (测试环境)                 │
│ TiDB            │ MySQL                         │
└─────────────────┴───────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────┐
│                    UI Layer                      │
├─────────────────────────────────────────────────┤
│ Dashboard       │ 拓扑图        │ 告警管理      │
└─────────────────────────────────────────────────┘
```

### 1.3 关键特性

#### **自动埋点**
```yaml
# skywalking-agent.config
agent.service_name=${SW_AGENT_NAME:Your_ApplicationName}
collector.backend_service=${SW_AGENT_COLLECTOR_BACKEND_SERVICES:127.0.0.1:11800}
logging.level=${SW_LOGGING_LEVEL:INFO}
```

#### **多语言支持**
- Java：字节码增强技术
- .NET：CLR Profiler API
- Node.js：Wrap模块机制
- Go：编译器插桩

#### **采样策略**
```java
// 动态采样配置
public class DynamicSampling {
    // 慢请求100%采样
    @SamplingRule(operation="/api/*", minDuration=1000, samplingRate=1.0)
    // 普通请求10%采样  
    @SamplingRule(defaultRate=0.1)
    public void traceMethod() {
        // 业务逻辑
    }
}
```

### 1.4 实践案例

**Spring Cloud集成配置**
```yaml
# application.yml
spring:
  cloud:
    skywalking:
      enabled: true
      agent:
        service-name: order-service
        backend-service: skywalking-collector:11800
        sample-n-per-3-secs: -1  # 全量采样
      logging:
        level: DEBUG
        enable: true
      plugin:
        mysql: true
        redis: true
        rocketmq: true
```

## 二、日志收集：ELK Stack

### 2.1 技术栈组成

**ELK = Elasticsearch + Logstash + Kibana**
现代演进为 **Elastic Stack**，加入 Beats 轻量级数据采集器

### 2.2 架构详解

#### **数据流架构**
```
┌─────────┐    ┌──────────┐    ┌──────────────┐    ┌──────────────┐
│ 应用日志 │───▶│ Filebeat │───▶│  Logstash    │───▶│ Elasticsearch│
├─────────┤    ├──────────┤    ├──────────────┤    └──────────────┘
│ 系统指标 │───▶│ Metricbeat│   │ 数据处理管道│            │
├─────────┤    ├──────────┤    │• 解析过滤    │            │
│ 容器日志│───▶│ Docker   │    │• 字段丰富    │            │
│         │    │ 日志驱动 │    │• 数据转换    │            ▼
└─────────┘    └──────────┘    └──────────────┘    ┌──────────────┐
                                                    │   Kibana     │
                                                    │• 可视化      │
                                                    │• 仪表板      │
                                                    │• 机器学习    │
                                                    └──────────────┘
```

### 2.3 配置深度解析

#### **Logstash管道配置**
```ruby
input {
  beats {
    port => 5044
    ssl => false
  }
  
  tcp {
    port => 5000
    codec => json_lines
  }
}

filter {
  # Grok模式匹配
  grok {
    match => { "message" => "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} %{GREEDYDATA:message}" }
  }
  
  # 日期解析
  date {
    match => [ "timestamp", "ISO8601" ]
    target => "@timestamp"
  }
  
  # 用户代理解析
  useragent {
    source => "user_agent"
    target => "ua"
  }
  
  # 地理信息
  geoip {
    source => "clientip"
    target => "geoip"
  }
}

output {
  elasticsearch {
    hosts => ["http://elasticsearch:9200"]
    index => "logs-%{+YYYY.MM.dd}"
    template => "/usr/share/logstash/templates/logs-template.json"
    template_name => "logs"
  }
  
  # 异常日志特殊处理
  if [loglevel] == "ERROR" {
    elasticsearch {
      hosts => ["http://elasticsearch:9200"]
      index => "error-logs-%{+YYYY.MM.dd}"
    }
  }
}
```

#### **Elasticsearch索引策略**
```json
{
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "refresh_interval": "30s",
    "index.lifecycle.name": "logs_policy",
    "index.routing.allocation.require.box_type": "hot"
  },
  "mappings": {
    "dynamic_templates": [
      {
        "strings_as_keyword": {
          "match_mapping_type": "string",
          "mapping": {
            "type": "keyword",
            "ignore_above": 256
          }
        }
      }
    ],
    "properties": {
      "@timestamp": { "type": "date" },
      "message": { "type": "text", "analyzer": "standard" },
      "level": { "type": "keyword" },
      "trace_id": { "type": "keyword" },
      "span_id": { "type": "keyword" }
    }
  }
}
```

#### **ILM（索引生命周期管理）策略**
```json
{
  "policy": {
    "phases": {
      "hot": {
        "min_age": "0ms",
        "actions": {
          "rollover": {
            "max_size": "50gb",
            "max_age": "1d"
          }
        }
      },
      "warm": {
        "min_age": "1d",
        "actions": {
          "shrink": {
            "number_of_shards": 1
          },
          "forcemerge": {
            "max_num_segments": 1
          }
        }
      },
      "cold": {
        "min_age": "7d",
        "actions": {
          "freeze": {}
        }
      },
      "delete": {
        "min_age": "30d",
        "actions": {
          "delete": {}
        }
      }
    }
  }
}
```

### 2.4 Kibana高级可视化

#### **Canvas实时看板**
```json
{
  "workpad": {
    "pages": [
      {
        "elements": [
          {
            "id": "error_rate",
            "type": "metric",
            "expression": "filters 
              query=\"level:ERROR\" 
            | es index=logs-* 
            | math \"count()\" 
            | metric \"Error Count\""
          },
          {
            "id": "response_time",
            "type": "plot",
            "expression": "es index=logs-* 
            | math \"avg(response_time)\" 
            | plot defaultStyle={seriesStyle lines=3}"
          }
        ]
      }
    ]
  }
}
```

## 三、容器化：Docker深度解析

### 3.1 核心技术原理

#### **Namespace隔离机制**
```bash
# 查看当前进程的Namespace
ls -la /proc/$$/ns/

# 创建新的Namespace
unshare --fork --pid --mount-proc bash

# Docker使用的Namespace类型
# PID: 进程隔离
# NET: 网络隔离  
# MNT: 文件系统挂载点
# IPC: 进程间通信
# UTS: 主机名和域名
# USER: 用户和用户组
```

#### **Cgroups资源控制**
```bash
# 创建Cgroup
cgcreate -g cpu,memory:/mycontainer

# 限制CPU使用（CFS调度器）
echo 100000 > /sys/fs/cgroup/cpu/mycontainer/cpu.cfs_quota_us
echo 50000 > /sys/fs/cgroup/cpu/mycontainer/cpu.cfs_period_us

# 限制内存
echo 100M > /sys/fs/cgroup/memory/mycontainer/memory.limit_in_bytes

# Docker对应参数
docker run -d \
  --cpus="0.5" \
  --memory="100m" \
  --memory-swap="200m" \
  nginx:alpine
```

#### **UnionFS联合文件系统**
```
# Overlay2架构
lowerdir=/var/lib/docker/overlay2/l/XYZ123  # 只读层（镜像层）
upperdir=/var/lib/docker/overlay2/uuid/diff  # 可写层（容器层）
workdir=/var/lib/docker/overlay2/uuid/work   # 工作目录
merged=/var/lib/docker/overlay2/uuid/merged  # 合并视图
```

### 3.2 Dockerfile最佳实践

```dockerfile
# 多阶段构建示例
# 阶段1：构建阶段
FROM golang:1.19-alpine AS builder

WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o main .

# 阶段2：运行阶段  
FROM alpine:3.16

# 安全加固
RUN apk --no-cache add ca-certificates tzdata && \
    addgroup -S app && adduser -S app -G app && \
    mkdir -p /app && chown -R app:app /app

# 最小化用户权限
USER app
WORKDIR /app

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# 从构建阶段复制
COPY --from=builder --chown=app:app /app/main /app/main
COPY --from=builder --chown=app:app /app/config /app/config

# 非root端口
EXPOSE 8080

# 使用exec格式
ENTRYPOINT ["/app/main"]
CMD ["--config", "/app/config/prod.yaml"]
```

### 3.3 Docker Compose编排

```yaml
version: '3.8'

services:
  skywalking-oap:
    image: apache/skywalking-oap-server:9.4.0
    container_name: skywalking-oap
    restart: unless-stopped
    environment:
      SW_STORAGE: elasticsearch7
      SW_STORAGE_ES_CLUSTER_NODES: elasticsearch:9200
      JAVA_OPTS: >-
        -Xms2g
        -Xmx2g
        -XX:+UseG1GC
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:12800"]
      interval: 30s
      timeout: 10s
      retries: 3
    networks:
      - observability-net
    deploy:
      resources:
        limits:
          memory: 4G
        reservations:
          memory: 2G

  elasticsearch:
    image: elasticsearch:8.6.2
    container_name: elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms2g -Xmx2g"
    volumes:
      - es_data:/usr/share/elasticsearch/data
    ports:
      - "9200:9200"
    networks:
      - observability-net
    ulimits:
      memlock:
        soft: -1
        hard: -1

  logstash:
    image: logstash:8.6.2
    container_name: logstash
    volumes:
      - ./logstash/pipeline:/usr/share/logstash/pipeline
      - ./logstash/config:/usr/share/logstash/config
    ports:
      - "5044:5044"
      - "5000:5000"
    environment:
      LS_JAVA_OPTS: "-Xmx1g -Xms1g"
    networks:
      - observability-net
    depends_on:
      - elasticsearch

  kibana:
    image: kibana:8.6.2
    container_name: kibana
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    ports:
      - "5601:5601"
    networks:
      - observability-net
    depends_on:
      - elasticsearch

  application:
    build: .
    container_name: demo-app
    environment:
      - SW_AGENT_NAME=demo-service
      - SW_AGENT_COLLECTOR_BACKEND_SERVICES=skywalking-oap:11800
      - JAVA_OPTS=-javaagent:/skywalking/agent/skywalking-agent.jar
    volumes:
      - ./skywalking-agent:/skywalking/agent
    ports:
      - "8080:8080"
    networks:
      - observability-net
    depends_on:
      skywalking-oap:
        condition: service_healthy
      elasticsearch:
        condition: service_healthy
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"

networks:
  observability-net:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16

volumes:
  es_data:
    driver: local
```

### 3.4 高级网络模式

```yaml
# 自定义网络配置
networks:
  frontend:
    driver: bridge
    ipam:
      config:
        - subnet: 172.21.0.0/16
          gateway: 172.21.0.1
    driver_opts:
      com.docker.network.bridge.name: br-frontend
      com.docker.network.bridge.enable_icc: "true"
      com.docker.network.bridge.enable_ip_masquerade: "true"

  backend:
    driver: macvlan
    ipam:
      config:
        - subnet: 192.168.32.0/24
          gateway: 192.168.32.1
    driver_opts:
      parent: eth0
```

## 四、整合部署方案

### 4.1 一体化监控架构

```yaml
# docker-compose.full-monitoring.yml
version: '3.8'

x-logging: &default-logging
  driver: "json-file"
  options:
    max-size: "10m"
    max-file: "3"
    labels: "production"
    env: "log_level,service_name"

x-monitoring: &default-monitoring
  restart: unless-stopped
  logging: *default-logging
  networks:
    - monitoring

services:
  # 数据收集层
  vector:
    image: timberio/vector:latest-alpine
    configs:
      - source: vector-config
        target: /etc/vector/vector.yaml
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    ports:
      - "8686:8686"  # 健康检查
    <<: *default-monitoring

  # 存储分析层
  tempo:
    image: grafana/tempo:latest
    command: ["-config.file=/etc/tempo.yaml"]
    volumes:
      - ./tempo.yaml:/etc/tempo.yaml
      - tempo-data:/tmp/tempo
    ports:
      - "3200:3200"  # Tempo
      - "9095:9095"  # 指标
    <<: *default-monitoring

  # 可视化层
  grafana:
    image: grafana/grafana:latest
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_PASSWORD}
      - GF_INSTALL_PLUGINS=grafana-piechart-panel
    volumes:
      - grafana-data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning
    ports:
      - "3000:3000"
    <<: *default-monitoring
    depends_on:
      - tempo
      - loki
      - prometheus

configs:
  vector-config:
    content: |
      sources:
        docker_logs:
          type: docker_logs
      
      transforms:
        parse_logs:
          type: remap
          inputs: [docker_logs]
          source: |
            . |= parse_json!(.message)
            .service = .container_name
            .timestamp = .timestamp
      
      sinks:
        to_loki:
          type: loki
          inputs: [parse_logs]
          endpoint: http://loki:3100
          labels:
            service: "{{ service }}"
            level: "{{ level }}"
        
        to_stdout:
          type: console
          inputs: [parse_logs]
          encoding: json

volumes:
  tempo-data:
  grafana-data:
  loki-data:

networks:
  monitoring:
    name: monitoring-network
    driver: bridge
```

### 4.2 生产环境优化配置

```bash
#!/bin/bash
# deploy-monitoring-stack.sh

# Docker守护进程配置
cat > /etc/docker/daemon.json << EOF
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3",
    "labels": "production",
    "env": "os,customer"
  },
  "storage-driver": "overlay2",
  "storage-opts": [
    "overlay2.override_kernel_check=true"
  ],
  "live-restore": true,
  "default-ulimits": {
    "nofile": {
      "Name": "nofile",
      "Hard": 65535,
      "Soft": 65535
    }
  },
  "metrics-addr": "0.0.0.0:9323",
  "experimental": true
}
EOF

# 内核参数优化
sysctl -w vm.max_map_count=262144
sysctl -w fs.file-max=65536
sysctl -w net.core.somaxconn=2048
sysctl -p

# 容器资源限制配置
mkdir -p /etc/containerd
cat > /etc/containerd/config.toml << EOF
[plugins]
  [plugins."io.containerd.grpc.v1.cri"]
    [plugins."io.containerd.grpc.v1.cri".containerd]
      default_runtime_name = "runc"
      [plugins."io.containerd.grpc.v1.cri".containerd.runtimes.runc]
        runtime_type = "io.containerd.runc.v2"
        [plugins."io.containerd.grpc.v1.cri".containerd.runtimes.runc.options]
          SystemdCgroup = true
    [plugins."io.containerd.grpc.v1.cri".registry]
      [plugins."io.containerd.grpc.v1.cri".registry.mirrors]
        [plugins."io.containerd.grpc.v1.cri".registry.mirrors."docker.io"]
          endpoint = ["https://docker.mirrors.ustc.edu.cn"]
EOF
```
