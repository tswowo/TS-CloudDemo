# tscloud — 云商城 Spring Cloud 微服务学习项目

一个完整的 Spring Cloud 微服务电商项目，从单体架构逐步演进到微服务体系，涵盖服务拆分、注册发现、远程调用、网关路由、认证鉴权、配置中心、限流熔断、分布式事务、消息队列、搜索引擎等核心知识点。

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 基础框架 | Spring Boot | 2.7.12 |
| 微服务 | Spring Cloud | 2021.0.3 |
| 微服务 | Spring Cloud Alibaba | 2021.0.4.0 |
| 注册/配置中心 | Nacos | 2.x |
| 远程调用 | OpenFeign + OkHttp | - |
| 网关 | Spring Cloud Gateway | - |
| ORM | MyBatis-Plus | 3.4.3 |
| 工具库 | Hutool | 5.8.11 |
| 认证 | JWT (jks) | - |
| 数据库 | MySQL | 8.x |
| 消息队列 | RabbitMQ | 3.x |
| 搜索引擎 | Elasticsearch | 7.12.1 |
| 限流熔断 | Sentinel | - |
| 分布式事务 | Seata | - |

## 模块结构

```
tscloud/
├── gateway-service/       # 网关服务 — 统一入口、路由转发、JWT 鉴权
├── api-service/           # API模块 — Feign Client 接口 + DTO + Fallback
├── common/        # 公共模块 — 异常处理、拦截器、工具类、MQ/RabbitMqHelper
├── item-service/     # 商品服务 — 商品 CRUD、库存扣减、AOP 索引同步
├── search-service/   # 搜索服务 — Elasticsearch 搜索、索引管理
├── cart-service/     # 购物车服务 — 购物车 CRUD、上限控制（热更新）
├── user-service/     # 用户服务 — 登录注册、JWT 签发、地址管理
├── trade-service/    # 交易服务 — 订单创建、支付状态监听、延迟校验取消
├── pay-service/      # 支付服务 — 支付单管理、MQ 异步通知
├── docs/             # 文档与重启指南
└── logs/             # 各服务日志目录
```

## 学习路线（按 Git 提交演进）

整个项目的 30+ 次提交形成了一条清晰的微服务学习路径。以下按阶段逐一介绍。

---

### 阶段一：单体架构 — 起点

**提交**: `6c11431` — init: 初始化仓库，导入学习项目

初始项目是一个标准的 Spring Boot 单体应用，包含完整的商城业务：用户、商品、购物车、订单、支付、地址、搜索。分层结构为 controller → service → mapper，使用 MyBatis-Plus 做 ORM，Spring Security + JWT 做认证。

知识点：
- Spring Boot 分层架构（Controller / Service / Mapper）
- MyBatis-Plus 的 `ServiceImpl` 基类和 `LambdaQueryWrapper`
- 统一响应体 `R<T>` 和 `@ControllerAdvice` 全局异常处理
- JWT 无状态认证的基本流程：登录签发 token → 请求携带 token → 拦截器校验

难点：
- JWT 签名文件 `tscloud.jks` 的生成和管理——需要用 JDK 自带的 `keytool` 命令生成密钥库
- Spring Security 配置的自定义 Filter 链注入

---

### 阶段二：微服务拆分 — 垂直拆分的实践

**提交**: `f2b8ff1` / `810a5f6` / `41db124` / `ac9257b` / `5901cbd`

逐步将单体中的商品、购物车、用户、支付、交易、地址模块拆分为独立服务。每个服务有自己的启动类、配置文件和数据库连接。

> **提交 `f2b8ff1`**: 拆分商品管理模块为微服务（item-service）

> **提交 `810a5f6`**: 拆分购物车模块，引入 Nacos 注册中心 + OpenFeign + OkHttp

这是最关键的一次架构升级：

- **Nacos 注册中心**：每个服务启动时向 Nacos 注册 `服务名 → IP:Port`，调用方通过服务名发现目标地址。对比 **Eureka**：Nacos 同时支持 AP/CP 模式切换，且自带配置中心功能；Eureka 仅做注册发现且已停止维护。
- **OpenFeign**：声明式 HTTP 客户端，只需定义接口 + 注解即可完成远程调用，Feign 在运行时生成 JDK 动态代理。对比 **RestTemplate**：RestTemplate 需要手动拼接 URL、序列化/反序列化，代码量大且容易出错；Feign 代码量少、类型安全。对比 **Dubbo**：Dubbo 基于 RPC 协议性能更高，但耦合度高、跨语言困难；Feign 基于 HTTP，通用性好。
- **OkHttp 连接池**：替换 Feign 默认的 `HttpURLConnection`。默认实现每次请求新建 TCP 连接（三次握手 + 四次挥手开销大），OkHttp 通过连接池复用连接，支持 HTTP/2 多路复用。微服务间调用频繁，连接池对性能至关重要。

```java
// Feign Client 示例 — 声明式远程调用
@FeignClient("item-service")  // 服务名即为 Nacos 中的注册名
public interface ItemClient {
    @GetMapping("/items")
    List<ItemDTO> queryItemByIds(@RequestParam("ids") Collection<Long> ids);
}
```

> **提交 `41db124`**: 修复 OkHttp 连接池依赖丢失

在抽取 api-service 时误删了 OkHttp 依赖，导致 Feign 静默降级为 `HttpURLConnection`。**重点**：连接池失效不会报错，只会让每次请求都新建连接，高并发下产生大量 `TIME_WAIT` 状态连接，性能急剧下降。排查时可通过日志查看 Feign 实际使用的 Client 类型。

> **提交 `ac9257b`**: 拆分支付模块（pay-service）和交易模块（trade-service）

订单与支付各自独立为服务——订单属于"交易域"，支付属于"支付域"。**服务边界判断**：支付涉及对接第三方支付平台，有独立的安全合规要求和可靠性策略，与订单的 CRUD 逻辑差异大，分开后可独立扩展、独立部署。

> **提交 `5901cbd`**: 拆分地址功能到用户模块

**重点——不是每个表都值得拆成独立微服务**。地址与用户是 1:N 强耦合关系，如果拆成独立的 address-service，查询用户地址列表就要跨服务调用，增加延迟和故障点。拆分的粒度应该是**业务聚合根**，而非数据库表。地址属于"用户域"，放在 user-service 是正确的选择。

知识点：
- 微服务垂直拆分：按业务域（限界上下文）划分，而非按数据表划分
- CAP 理论在注册中心选型中的应用
- Feign 的声明式代理原理与 RestTemplate、Dubbo 的对比
- 连接池对微服务间通信性能的影响

难点：
- **拆分粒度的判断**：拆太细导致服务爆炸和调用链过长（分布式事务问题），拆太粗又回到单体。经验法则：一个聚合根一个服务，先粗后细
- **DTO 冗余**：item-service 定义的 `ItemDTO`，cart-service 也要复制一份——这是下一阶段要解决的问题

---

### 阶段三：API 契约模块 — 解决 DTO 冗余

**提交**: `88771b4` — 封装 Client 和 DTO 为单独 API 模块

将 Feign Client 接口和 DTO 从 cart-service 抽取到独立的 `api-service` 模块，任何需要调用其他服务的模块只需依赖 api-service。

```
之前：cart-service 内部有一份 ItemClient.java + ItemDTO.java（副本）
      item-service 内部有原始定义
      → 两边各自维护，容易不一致

之后：api-service 是唯一的真相来源
      cart-service ──依赖──▶ api-service（含 ItemClient + ItemDTO）
      item-service ──依赖──▶ api-service（共享同一份 DTO 定义）
```

知识点：
- **API 模块模式**（也叫 contract-first / shared-schema）：把跨服务共享的接口契约集中管理
- 对比 **不抽取**：每个服务自己写 DTO → 字段不一致、重复劳动
- 对比 **BFF 模式**：对于学习项目规模，API 模块足够；大项目可能需要 GraphQL 或独立的 API Gateway 做 BFF 层

难点：
- api-service 需要保持**向后兼容**——改 DTO 字段会影响所有消费方
- 模块依赖方向：`api-service` 是最底层（不依赖任何业务模块），被所有需要远程调用的模块依赖

---

### 阶段四：云部署适配

**提交**: `d17f69e` / `2525dfc` — 部署数据库和 Nacos 到云服务器

配置外部化调整：数据库端口从硬编码 `3306` 改为 `${ts.db.port:3306}`（占位符 + 默认值），移除 Nacos 认证配置（云上内网部署未开启认证）。

知识点：
- **配置外部化**：不同环境（本地/测试/生产）的差异通过占位符 `${var:default}` 处理，启动时传入环境变量或 profiles 覆盖
- Spring Boot 配置加载优先级：命令行参数 > 环境变量 > application-{profile}.yaml > application.yaml

难点：
- 本地开发到云部署的配置差异分散在 6 个服务的 application.yaml 中，每次都要改多个文件——**共享配置**是下一阶段要解决的问题

> **提交 `1be10eb`**: 补充 search 接口到 item-service

搜索功能归属商品服务，遵循**数据就近原则**——搜索的是商品，结果返回商品数据，放在 item-service 避免额外跨服务调用。后续搜索量大可再拆出独立搜索引擎（如 Elasticsearch）。

---

### 阶段五：网关 — 统一入口与路由

**提交**: `a9b80cb` — 配置网关微服务，将 8080 端口请求转发到各微服务

网关接管了原单体服务的 8080 端口，成为系统的唯一入口：

```yaml
# 静态路由配置
spring.cloud.gateway.routes:
  - id: item-service
    uri: lb://item-service       # lb = 客户端负载均衡
    predicates:
      - Path=/items/**,/search/**
  - id: cart-service
    uri: lb://cart-service
    predicates:
      - Path=/carts/**
  # ... 其余服务同理
```

知识点：
- **Spring Cloud Gateway** 三大核心概念：Route（路由）、Predicate（断言）、Filter（过滤器）
- `lb://` 前缀触发客户端负载均衡——Gateway 从 Nacos 拉取服务实例列表，通过 `ReactorLoadBalancer` 选出一个实例转发。对比 **服务端负载均衡（Nginx）**：Gateway 是进程内的客户端负载，省去一跳网络延迟，但不适合异构语言环境
- Gateway 基于 **WebFlux + Netty**（响应式），与传统的 Spring MVC（Tomcat + Servlet）不同，是异步非阻塞模型

难点：
- Gateway 和普通微服务在底层技术栈上的差异：Gateway 用 WebFlux，普通服务用 Spring MVC，这意味着 `HttpServletRequest`、`HandlerInterceptor` 等在 Gateway 中不可用
- 路由此时是**静态**的，新增服务或改路由规则需重启网关

> **提交 `aa55ce5`**: 修复 Restful 请求方式错误

Feign Client 声明与实际服务端不匹配的经典 bug：
- `@GetMapping("/carts")` 执行删除操作 → 应为 `@DeleteMapping`
- 接口路径少写了 `/items` 前缀

**重点**：Feign 接口是纯声明式的，编译期不验证路径和方法的正确性——只要方法签名合法就编译通过，实际调用时才报 404/405。解决方案：运行时契约测试（如 Spring Cloud Contract）或集成测试覆盖。

> **提交 `c9fa2a1`**: 补充遗漏的 mapper.xml 文件

拆分服务时资源文件（`resources/mapper/*.xml`）容易漏迁移——Java 代码在 `src/main/java` 下，XML 在 `src/main/resources` 下，IDE 操作时很容易只复制了代码。

---

### 阶段六：网关过滤器 — 自定义过滤器体系

**提交**: `d5f7677` — 新增自定义 GlobalFilter 和 GatewayFilterFactory

Gateway 提供两种过滤器扩展点：

| | GlobalFilter | GatewayFilterFactory |
|---|---|---|
| **作用范围** | 所有路由自动生效 | 需在 YAML 中按路由配置 |
| **实现方式** | `implements GlobalFilter` | `extends AbstractGatewayFilterFactory<Config>` |
| **参数配置** | 不支持 | 支持（通过 `shortcutFieldOrder()` 映射 YAML 参数） |
| **典型场景** | 鉴权、全局日志、全局限流 | 路径改写、添加响应头、特定路由脱敏 |

```java
// GlobalFilter — 无需配置，全局生效
@Component
public class MyGlobalFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 对所有路由的请求做处理
        return chain.filter(exchange);
    }
    @Override
    public int getOrder() { return 0; }
}

// GatewayFilterFactory — 需要在 YAML 中配置 - My=a,b,c
@Component
public class MyGatewayFilterFactory
        extends AbstractGatewayFilterFactory<MyGatewayFilterFactory.Config> {
    @Override
    public List<String> shortcutFieldOrder() {
        return List.of("a", "b", "c"); // YAML 参数的顺序映射
    }
}
```

知识点：
- Spring Cloud Gateway 的过滤器链使用**响应式编程模型**（Project Reactor），返回 `Mono<Void>` 而非 `void`
- `Ordered` 接口控制过滤器执行顺序，数值越小越先执行
- 对比 **Zuul 1.x**（Servlet 阻塞模型）：Gateway 基于 WebFlux 非阻塞，吞吐量更高；对比 **Zuul 2.x**（Netty 非阻塞）：Gateway 与 Spring 生态集成更好

难点：
- Gateway 过滤器是基于 **WebFlux** 的，`ServerWebExchange` / `ServerHttpRequest` 不同于传统的 `HttpServletRequest`
- `shortcutFieldOrder()` 的参数映射机制：YAML 中的 `- My=val1,val2,val3` 按顺序注入到 Config 的 a、b、c 字段

---

### 阶段七：网关 JWT 鉴权 — 统一认证入口

**提交**: `ec34541` — 网关校验登录，JWT 解析 + 放行白名单 + 用户信息转发

将认证逻辑从各业务服务上提到网关层，实现"一处认证，全链路有效"：

```
客户端请求 → Gateway (AuthGlobalFilter)
              ├── 检查是否在 excludePaths 白名单中 → 是则放行
              ├── 从 Authorization 头提取 JWT
              ├── 解析 JWT 获取 userId
              ├── 将 userId 写入 user-info 请求头
              └── 转发给下游微服务
```

```java
@Component
@RequiredArgsConstructor
public class AuthGlobalFilter implements GlobalFilter, Ordered {
    private final AuthProperties authProperties;  // 白名单配置
    private final JwtTool jwtTool;
    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 1. 白名单放行
        if (isExcludePath(request.getPath().value())) {
            return chain.filter(exchange);
        }
        // 2. 解析 JWT
        Long userId = jwtTool.parseToken(token);
        // 3. 注入 user-info 请求头，传递给下游服务
        exchange.mutate().request(builder ->
            builder.header("user-info", userId.toString()));
        return chain.filter(exchange);
    }
}
```

同时，`common` 新增了 `UserInfoInterceptor`（Spring MVC HandlerInterceptor），用于在下游微服务中接收网关传递的 `user-info` 请求头，存入 `UserContext`（ThreadLocal）。

```java
// common 中的拦截器
public class UserInfoInterceptor implements HandlerInterceptor {
    public boolean preHandle(HttpServletRequest request, ...) {
        String userInfo = request.getHeader("user-info");
        if (StrUtil.isNotBlank(userInfo)) {
            UserContext.setUser(Long.parseLong(userInfo));
        }
        return true;
    }
    public void afterCompletion(...) {
        UserContext.removeUser();  // 防止内存泄漏
    }
}
```

**关键设计**：`MvcConfig` 使用 `@ConditionalOnClass(DispatcherServlet.class)`，确保拦截器**只在 Spring MVC 环境中加载**：

```java
@Configuration
@ConditionalOnClass(DispatcherServlet.class)  // Gateway 是 WebFlux，没有 DispatcherServlet
public class MvcConfig implements WebMvcConfigurer { ... }
```

知识点：
- **网关认证 vs 服务认证**：网关做**认证**（Authentication = 验证你是谁），微服务做**鉴权**（Authorization = 校验你能做什么）。网关只验证 JWT 有效性，不关心用户权限细节
- **AntPathMatcher**：Ant 风格路径匹配（`/search/**`、`/users/login`），比正则更直观
- `@ConditionalOnClass`：common 同时被 Gateway（WebFlux）和 MVC 服务引用时，避免 WebFlux 环境加载 Servlet API 报错
- 对比 **Spring Security OAuth2**：更重但功能完整（授权码模式、客户端模式）；手写 JWT 过滤器更轻量、灵活

难点：
- Gateway 用 `ServerWebExchange.mutate()` 修改请求（不可变模式），不能直接 `request.getHeaders().add()`
- ThreadLocal 的 `removeUser()` 在 `afterCompletion` 中执行——即使业务代码抛异常也会回调，保证不泄漏
- 密钥文件 `tscloud.jks` 需要在 gateway 和 user-service 各存一份（签发在 user-service，校验在 gateway）

---

### 阶段八：微服务间用户身份传递 — 全链路透明传输

**提交**: `4675138` — OpenFeign RequestInterceptor 传递用户信息

补齐用户身份传递链的最后一段——微服务 A 调用微服务 B 时，通过 Feign 拦截器自动携带当前用户标识：

```
[Gateway]                        [cart-service]                    [item-service]
   │                                 │                                 │
   │ JWT → user-info header         │                                 │
   ├────────────────────────────────►│                                 │
   │                                 │ UserInfoInterceptor             │
   │                                 │ → UserContext.setUser(123)      │
   │                                 │                                 │
   │                                 │ Feign RequestInterceptor        │
   │                                 │ → 从 UserContext 读取 userId    │
   │                                 │ → 写入 Feign 请求头 user-info   │
   │                                 ├─────────────────────────────────►│
   │                                 │                                 │ UserInfoInterceptor
   │                                 │                                 │ → UserContext.setUser(123)
```

完整的三段式传递：
1. **Gateway → 下游服务**：`AuthGlobalFilter` 将 JWT userId 注入 `user-info` Header
2. **请求进入服务**：`UserInfoInterceptor` 读 Header → 写 ThreadLocal
3. **服务间调用**：`Feign RequestInterceptor` 读 ThreadLocal → 写 Header

```java
// Feign 拦截器 — 每次远程调用前自动执行
@Bean
public RequestInterceptor userInfoInterceptor() {
    return requestTemplate -> {
        Long userId = UserContext.getUser();
        if (userId != null) {
            requestTemplate.header("user-info", userId.toString());
        }
    };
}
```

知识点：
- `feign.RequestInterceptor` vs `HandlerInterceptor`：前者拦截**发出**的 Feign 请求，后者拦截**接收**的 MVC 请求，方向不同
- ThreadLocal 保证请求隔离：Tomcat 每个请求一个线程 → ThreadLocal 天然线程安全，不会串数据
- 对比 **分布式链路追踪（Sleuth/Zipkin）**：TraceId 也是类似的传播方式（请求头 + ThreadLocal + 跨服务传递），但 TraceId 是系统维度的，userId 是业务维度的
- 对比 **直接在 Controller 参数中声明 userId**：Header 传递方式对业务代码零侵入

难点：
- `afterCompletion` 中 `removeUser()` 是关键——漏掉会导致**内存泄漏**（线程回池后 ThreadLocal 残留）或**数据串扰**（复用线程拿到了上一个请求的 userId）
- Feign 拦截器的执行时机：在构建 HTTP 请求时（发请求前），不是在 Controller 层

---

### 阶段九：Nacos 配置中心 — 动态配置热更新

**提交**: `ef2170c` — cart-service 迁移到 Nacos 配置中心，购物车上限热更新

将 cart-service 的配置分为两层：

```
bootstrap.yaml（最早加载）            application.yaml（后加载）
├── Nacos 连接信息                     ├── server.port
├── file-extension: yaml               ├── Nacos discovery
└── shared-configs:                    └── ts.* 业务变量
    ├── shared-jdbc.yaml
    ├── shared-log.yaml
    └── shared-swagger.yaml

Nacos 中的 cart-service-dev.yaml：
    ts.cart.maxItems: 10  ← 购物车上限配置
```

热更新通过 `@ConfigurationProperties` 实现，**无需 `@RefreshScope`**：

```java
@Data
@ConfigurationProperties(prefix = "ts.cart")
@Component
public class CartProperties {
    private Integer maxItems;  // Nacos 中修改此值，Bean 自动 rebind
}
```

对比：
- `@ConfigurationProperties` + Nacos：**自动 rebind**，不需要额外注解
- `@Value` + Nacos：需要加 `@RefreshScope` 才能刷新（因为 `@Value` 是一次性注入）
- `@RefreshScope`：会重新创建 Bean 代理，有性能开销；`@ConfigurationProperties` 是原地 rebind

知识点：
- **bootstrap.yaml vs application.yaml**：bootstrap 由 Spring Cloud 父上下文加载（最早），用于连接配置中心；application 由 Spring Boot 应用上下文加载（稍晚），用于业务配置。如果连不上 Nacos，服务启动就会在 bootstrap 阶段失败
- **shared-configs**：JDBC、MyBatis、日志、Swagger 等通用配置在 Nacos 中配一次，所有服务通过 shared-configs 引用——解决了之前"改配置要改 6 个文件"的痛点
- 对比 **Apollo（携程）**：Apollo 有 UI 管理界面和灰度发布能力更强；Nacos 胜在注册中心 + 配置中心一体化，运维成本低
- 对比 **Spring Cloud Config**：需配合 Git + Bus 实现动态刷新，Nacos 自带推送机制

难点：
- 理解 Spring Cloud 父子容器启动顺序：bootstrap context 先启动 → 连 Nacos 拉配置 → application context 启动
- `@ConfigurationProperties` 的 rebind 机制是 Nacos SDK 在底层监听到配置变更后，通知 Spring 重新绑定对应 Bean——全自动，但需要 Bean 有 setter

---

### 阶段十：动态网关路由 — 路由表的热更新

**提交**: `938872a` — 网关路由从静态 YAML 配置迁移到 Nacos 动态加载

将路由表从 application.yaml 搬到 Nacos 的 `gateway-routes.json`，通过 `RouteDefinitionWriter` 实现不重启网关即可更新路由：

```java
@Component
public class DynamicRouteLoader {
    private final RouteDefinitionWriter writer;  // Gateway 路由写入器
    private final Set<String> routeIds = new HashSet<>();  // 追踪当前路由 ID

    @PostConstruct
    public void initRouteConfigListener() throws NacosException {
        // getConfigAndSignListener：拉取配置 + 注册监听器（一次调用完成）
        String configInfo = nacosConfigManager.getConfigService()
            .getConfigAndSignListener("gateway-routes.json", "DEFAULT_GROUP", 5000,
                new Listener() {
                    public void receiveConfigInfo(String configInfo) {
                        updateRouteConfig(configInfo);  // Nacos 配置变更 → 更新路由表
                    }
                });
        updateRouteConfig(configInfo);  // 启动时初始化
    }

    public void updateRouteConfig(String configInfo) {
        // 1. 删除所有旧路由
        routeIds.forEach(id -> writer.delete(Mono.just(id)).subscribe());
        routeIds.clear();
        // 2. 写入新路由
        List<RouteDefinition> routes = JSONUtil.toList(configInfo, RouteDefinition.class);
        routes.forEach(route -> {
            writer.save(Mono.just(route)).subscribe();
            routeIds.add(route.getId());
        });
    }
}
```

`gateway-routes.json` 示例（Nacos 中的 JSON 配置）：

```json
[
  {
    "id": "item-service",
    "uri": "lb://item-service",
    "predicates": [{"name": "Path", "args": {"pattern": "/items/**,/search/**"}}]
  },
  {
    "id": "cart-service",
    "uri": "lb://cart-service",
    "predicates": [{"name": "Path", "args": {"pattern": "/carts/**"}}]
  }
]
```

知识点：

| | 动态路由（本阶段） | 动态配置（阶段九） |
|---|---|---|
| **更新目标** | 路由表（基础设施） | 业务参数（ts.cart.maxItems） |
| **实现方式** | Nacos Listener + `RouteDefinitionWriter` | `@ConfigurationProperties` 自动 rebind |
| **代码量** | 需要手动管理删除/写入 | 只需声明 Bean |
| **粒度** | 整张路由表替换 | 单个属性更新 |
| **本质** | 资源变更（需要编程控制） | 值变更（框架自动处理） |

难点：
- `RouteDefinitionWriter.save/delete` 返回 `Mono`（Reactor 响应式类型），需要显式 `.subscribe()` 才会执行——忘记 subscribe 的话路由不会生效，且不会报错
- 当前实现是**整表替换**（全删 + 全写），生产环境建议改为**增量更新**（diff 后只增删差异路由），减少路由抖动
- `getConfigAndSignListener` 必须在**启动时**注册，不能在运行时动态添加新的 Listener

---

### 阶段十一：Sentinel 限流熔断 — Feign 容错保护

**提交**: `78ec31d` / `aa0484d` / `35ee121` / `e9432eb`

引入 Sentinel 对微服务间的 Feign 调用做限流和熔断保护，并通过 `FallbackFactory` 实现服务降级。

**Sentinel 接入步骤**：

1. 父 POM 引入 `spring-cloud-starter-alibaba-sentinel`
2. cart-service 中开启 `feign.sentinel.enabled: true`
3. 为每个 Feign Client 编写 `FallbackFactory`，在远程调用失败时执行降级逻辑

```java
// FallbackFactory 示例 — ItemClient 的降级策略
@Slf4j
public class ItemClientFallback implements FallbackFactory<ItemClient> {
    @Override
    public ItemClient create(Throwable cause) {
        return new ItemClient() {
            @Override
            public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
                log.error("远程调用ItemClient#queryItemByIds出现异常，参数：{}", ids, cause);
                return CollUtils.emptyList();  // 查询降级 → 返回空列表
            }
            @Override
            public void deductStock(List<OrderDetailDTO> items) {
                throw new BizIllegalException(cause);  // 写操作降级 → 抛业务异常
            }
            // ... 其他方法同理
        };
    }
}
```

6 个 Feign Client 全部配备了 FallbackFactory：`ItemClientFallback`、`CartClientFallback`、`OrderClientFallback`、`PayClientFallback`、`SearchClientFallback`、`UserClientFallback`。

**降级策略设计原则**：

| 操作类型 | 降级策略 | 原因 |
|---|---|---|
| 查询（读） | 返回空列表/空对象 | 保证主流程不中断，牺牲部分数据 |
| 扣减/写入 | 抛 `BizIllegalException` | 数据一致性优先——不确定是否成功时不能静默吞掉 |

对比：
- **Sentinel 核心能力**：流量控制（QPS/线程数限流）、熔断降级（慢调用比例/异常比例）、系统自适应保护。Sentinel 的规则在控制台配置，实时生效，比 Hystrix 的代码侵入更低
- **Sentinel vs Hystrix**：Hystrix 已停止维护，Sentinel 是阿里开源的主力项目，控制台 UI 更友好
- **FallbackFactory vs Fallback**：`FallbackFactory` 可以获取异常原因（`Throwable cause`），`Fallback` 只能返回降级对象。前者可以做差异化降级，后者代码更简洁

知识点：
- Sentinel 的**控制台**（dashboard）与客户端是**拉模式**——客户端主动向控制台注册心跳，控制台推送规则到客户端
- `feign.sentinel.enabled`：开启后 Feign 生成的代理会被 Sentinel 包装，每次调用经过 Sentinel 的 Slot Chain
- `@SentinelResource`：可以用注解定义资源点和 fallback/handler，但 Feign 的降级直接用 `FallbackFactory` 更自然

难点：
- Sentinel 控制台默认**不持久化**规则到磁盘——重启后规则丢失。生产环境需要接入 Nacos/ZooKeeper 等外部数据源
- 当前 shared-sentinel.yaml 在 Nacos 中被注释掉了（临时关闭），启用时需要取消注释 bootstrap.yaml 中的引用

---

### 阶段十二：Seata 分布式事务 — 跨服务数据一致性

**提交**: `d7a4093` / `0c229b7` / `35ee121` / `80e0bce`

引入 Seata 管理跨微服务的事务——典型场景是下单流程涉及 trade-service（订单）、cart-service（购物车）、item-service（库存）三个服务。

```java
// trade-service: OrderServiceImpl.createOrder()
@Transactional  // 本地事务
// @GlobalTransactional(timeoutMills = 300000, name = "createOrder")  // 分布式事务（已注释）
public Long createOrder(OrderFormDTO orderFormDTO) {
    // 1. 创建订单（本地 DB）
    save(order);
    // 2. 清理购物车（远程 → cart-service）
    cartClient.deleteCartItemByIds(itemIdsList);
    // 3. 扣减库存（远程 → item-service）
    itemClient.deductStock(detailDTOS);
    // 4. 发送延迟消息
    mq.sendDelayMessage(...);
}
```

**Seata XA 模式**：

```yaml
seata:
  tx-service-group: tscloud
  service:
    vgroup-mapping:
      tscloud: "default"
    grouplist:
      default: localhost:8091  # Seata TC 地址
```

XA 模式下，Seata 将业务 SQL 与 XA 协议绑定，分两阶段提交：
1. **阶段一（Prepare）**：各服务执行 SQL，XA 资源管理器将事务置于"预提交"状态（数据已写入但未提交）
2. **阶段二（Commit/Rollback）**：TC 根据全局事务结果通知各 RM 统一提交或回滚

对比 Seata 的四种事务模式：

| 模式 | 核心机制 | 优点 | 缺点 |
|---|---|---|---|
| **AT** | 自动回滚日志（undo_log 表） | 无侵入，性能较好 | 需要 undo_log 表，依赖数据库 |
| **XA** | XA 协议（RM 配合） | 强一致性，无数据脏读 | 性能开销大，需要数据库支持 XA |
| **TCC** | Try-Confirm-Cancel 三阶段 | 无锁，性能最好 | 代码侵入大，需手写补偿逻辑 |
| **Saga** | 正向服务 + 补偿服务 | 长事务友好 | 补偿逻辑复杂 |

**TC 端的 Docker 网络问题（提交 `0c229b7`）**：Seata TC 部署在 Docker 中，Nacos 中注册的是 Docker 内网桥接 IP。本地开发时无法通过 Docker 网桥 IP 直连 TC，解决方案是将 `grouplist` 中的 TC 地址指向 `localhost`，通过 SSH 隧道转发到云服务器的 Seata 服务。

知识点：
- **`@GlobalTransactional` vs `@Transactional`**：前者管理分布式事务（Seata TC 协调），后者管理本地事务（数据库连接级别的 commit/rollback）。`@GlobalTransactional` 的 timeout 应长于所有子事务的 timeout 之和
- **事务传播的隐性依赖**：下单 → 清购物车 → 扣库存，中间任何一个失败都要回滚。不使用分布式事务时，可能出现"库存扣了但购物车没清"的不一致状态
- 对比 **消息队列最终一致性**（下个阶段）：Seata 追求**强一致性**（ACID），MQ 追求**最终一致性**（BASE）

难点：
- **XA 的性能**：两阶段提交期间资源被锁定，高并发下会成为瓶颈。实际生产通常用 AT 模式（自动回滚日志 + 全局锁）替代
- 当前 Seata 依赖和 `@GlobalTransactional` 注解均处于**注释状态**——因为后续引入了 RabbitMQ 的最终一致性方案，强一致分布式事务的需求降低

---

### 阶段十三：RabbitMQ 消息队列 — 异步通信与最终一致性

**提交**: `f862b38` / `2f0d11b` / `1bfa001` / `ba1397c` / `ef9fe77` / `3559890` / `502c875`

这是整个项目最核心的一次架构升级——用消息队列替代同步 Feign 调用，将支付-订单链路从**同步耦合**改造为**异步解耦**。

**MQ 消息拓扑总览**：

```
                    ┌──────────────────────────────┐
                    │         RabbitMQ              │
                    │                               │
                    │  ┌─────────────┐              │
  item-service ────┼─►│ item.direct  │──► search-service
  (@ItemSync)       │  │ key: save    │   (索引同步)
       │            │  │ key: delete  │              │
       │            │  └─────────────┘              │
       │            │                               │
       │            │  ┌─────────────┐              │
       │            │  │ pay.direct   │              │
  pay-service ──────┼─►│ key: success │──► trade-service
  (支付成功)         │  └─────────────┘   (标记订单已支付)
                    │                               │
                    │  ┌────────────────────┐       │
                    │  │ trade.delay.direct  │       │
  trade-service ────┼─►│ (delayed exchange)  │──► trade-service
  (下单后20s)        │  │ key: delay.query    │   (校验是否支付)
                    │  └────────────────────┘       │
                    │                               │
                    │  ┌─────────────┐              │
                    │  │ error.direct │              │
                    │  │ key: svc名   │──► error.queue
                    │  └─────────────┘   (消费失败→死信)
                    └──────────────────────────────┘
```

**1. 消息序列化配置**（`MqConfig`）：

```java
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class MqConfig {
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
```

对比默认的 `SimpleMessageConverter`：Java 内置序列化（`ObjectInputStream`/`ObjectOutputStream`）体积大、不可读、跨语言困难。Jackson JSON 序列化体积小、可读、跨语言友好。

**2. RabbitMqHelper 工具类**（封装 `RabbitTemplate`）：

```java
public class RabbitMqHelper {
    private final RabbitTemplate rabbitTemplate;

    // 普通消息发送
    public void sendMessage(String exchange, String routingKey, Object msg)

    // 延迟消息（依赖 rabbitmq_delayed_message_exchange 插件）
    public void sendDelayMessage(String exchange, String routingKey, Object msg, int delay)

    // 带 ConfirmCallback 的可靠发送（超时返回 false）
    public boolean sendMessageWithConfirm(String exchange, String routingKey, Object msg, int timeoutSeconds)
}
```

`sendMessageWithConfirm` 通过 `CorrelationData` + `CompletableFuture.get(timeout)` 实现**同步确认**：发送消息后阻塞等待 Broker 返回 ack/nack，超时或 nack 返回 false。这是**生产者端的可靠性保证**。

**3. 支付异步通知**（pay-service → trade-service）：

支付成功后不再通过 Feign 同步调用 `orderClient.markOrderPaySuccess()`，而是发送 MQ 消息：

```java
// PayOrderServiceImpl.tryPayOrderByBalance()
mq.sendMessage("pay.direct", "pay.success", po.getBizOrderNo());
```

trade-service 的 `PayStatusListener.listenPaySuccess` 消费此消息，标记订单已支付。

**为什么改成异步？**
- Feign 同步调用依赖 trade-service 的可用性——如果 trade-service 挂了，支付成功的状态无法传递，订单永远停在"未支付"
- MQ 消息有持久化和重试机制——即使消费方临时不可用，消息也不会丢失，恢复后继续消费
- 支付服务的 RT（响应时间）不再包含订单状态更新的耗时

**4. 延迟消息校验——订单超时取消**（trade-service ← → trade-service）：

订单创建后，trade-service 立即发送一条 **20 秒延迟消息**：

```java
// OrderServiceImpl.createOrder()
mq.sendDelayMessage("trade.delay.direct", "delay.order.query", order.getId(), 20000);
```

20 秒后消息到达 `PayStatusListener.listenOrderDelayMessage`，消费方校验订单支付状态：
- 已支付 → 标记支付成功
- 未支付 → 取消订单 + 恢复库存（`itemClient.restoreStock()`）

```
时间线：
t=0s    订单创建，发送 20s 延迟消息
t=15s   用户支付成功 → pay-service 发送 pay.success 消息
        → trade-service 标记支付成功
t=20s   延迟消息到达 → 检查订单状态 → 已支付，跳过取消
```

对比传统**定时任务轮询**：轮询间隔短→数据库压力大；间隔长→用户等太久。延迟消息精准、实时、省资源。

**5. 消费失败的重试与死信机制**（`MqConsumeErrorAutoConfig`）：

```java
@Configuration
@ConditionalOnProperty(name = "spring.rabbitmq.listener.simple.retry.enabled", havingValue = "true")
public class MqConsumeErrorAutoConfig {
    // 为每个微服务创建独立的 error queue: {serviceName}.error.queue
    // 绑定到 error.direct exchange，routing key = serviceName
    // 使用 RepublishMessageRecoverer：消费重试全部失败后 → 转发到 error.direct
}
```

**关键设计**：死信队列是**按服务名动态创建**的——每个服务启动时自动声明自己的 error queue 并绑定到 `error.direct`。这样运维人员只需监控 `error.direct` 交换机下的死信，定位到具体服务名即可。

知识点：
- **Direct Exchange vs Delayed Exchange**：`pay.direct` 是普通直连交换机（立即投递），`trade.delay.direct` 使用了 `rabbitmq_delayed_message_exchange` 插件（按 delay 字段延迟投递）。延迟消息在 Broker 内存中暂存，到期后才路由到队列
- **消费确认机制**：默认自动 ack（`auto`），改为手动 ack（`manual`）可在消费成功后才确认，失败时 requeue 或不确认
- **幂等性**：MQ 可能重复投递（网络超时重试 + 没有 ack），消费方必须做幂等处理。`listenPaySuccess` 中检查 `status != 1` 就是幂等保护
- 对比 **Kafka**：RabbitMQ 更适合业务消息（灵活的路由、优先级队列、延迟消息），Kafka 更适合海量日志/流数据处理

难点：
- **RabbitMQ 插件的部署**：`rabbitmq_delayed_message_exchange` 不是内置的，需要在 RabbitMQ 服务器上手动安装 `.ez` 文件，版本必须与 RabbitMQ 版本严格对应
- **消息可靠性**：`sendMessage` 使用普通的 `convertAndSend`，没有 ConfirmCallback——如果 Broker 挂了消息直接丢失。关键业务（如支付成功通知）需要改成 `sendMessageWithConfirm`
- **延迟消息的时机权衡**：20 秒太短 → 用户在支付页面没操作完订单就被取消了；太长 → 库存被长时间占用不释放

---

### 阶段十四：Elasticsearch 搜索 — 从数据库查询到搜索引擎

**提交**: `7105776` / `d448f0b` / `73251ab` / `e447cdb` / `4ac59e1` / `54027b2` / `a49c47c`

将搜索功能从 item-service 的 MySQL 模糊查询迁移到独立的 search-service，底层使用 Elasticsearch 7.12.1，支持全文搜索、高亮、聚合过滤、广告加权和索引同步。

**为什么要迁移到 Elasticsearch？**

MySQL 的 `LIKE '%keyword%'` 无法使用索引（最左前缀失效），全表扫描在百万级商品数据下性能极差。Elasticsearch 基于**倒排索引**（Inverted Index），将文档中的词映射到文档 ID，搜索时直接定位，时间复杂度与文档总数无关。

```yaml
# search-service: application.yaml
ts:
  es:
    uri: http://localhost:19200
```

**1. RestHighLevelClient 配置**（`ElasticsearchConfig`）：

```java
@Bean
@ConfigurationProperties(prefix = "ts.es")
public EsProperties esProperties() { return new EsProperties(); }

@Bean(destroyMethod = "close")
public RestHighLevelClient restHighLevelClient(EsProperties properties) {
    return new RestHighLevelClient(RestClient.builder(
            HttpHost.create(properties.getUri())));
}
```

`destroyMethod = "close"` 确保应用关闭时释放 HTTP 连接。

**2. 搜索核心实现**（`SearchServiceImpl.searchFromEsByCondition`）：

```java
// BoolQuery：多条件组合
BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
boolQuery.must(QueryBuilders.matchQuery("name", query.getKey()));      // 全文匹配
boolQuery.filter(QueryBuilders.termQuery("category", query.getCategory())); // 精确过滤
boolQuery.filter(QueryBuilders.termQuery("brand", query.getBrand()));
boolQuery.filter(QueryBuilders.rangeQuery("price").gte(min).lte(max)); // 范围过滤

// FunctionScoreQuery：广告商品加权
FunctionScoreQueryBuilder functionScoreQuery = QueryBuilders.functionScoreQuery(
    boolQuery,
    new FunctionScoreQueryBuilder.FilterFunctionBuilder[]{
        new FunctionScoreQueryBuilder.FilterFunctionBuilder(
            QueryBuilders.termQuery("isAD", true),  // 广告商品
            ScoreFunctionBuilders.weightFactorFunction(100)  // 权重 × 100
        )
    }
);

// Highlight：高亮显示匹配的关键词
SearchSourceBuilder.highlight()
    .field("name").preTags("<em>").postTags("</em>");
```

搜索流程：关键词 matchQuery → boolQuery 过滤（品类/品牌/价格）→ function_score 加权（广告位提升）→ 分页排序 → 高亮结果解析。

**3. 聚合过滤**（`getFilters`）：

```java
// Terms Aggregation — 对 brand 和 category 字段做分组统计
request.source().aggregation(
    AggregationBuilders.terms("brand_agg").field("brand").size(10)
);
```

ES 的聚合查询比 MySQL 的 `SELECT DISTINCT` + `GROUP BY` 在去重计数上快得多——ES 在内存中使用**列式存储**结构做聚合，不需要回表。

**4. AOP + MQ 实现数据库与索引库同步**：

```
item-service (MySQL)                    search-service (ES)
    │                                        │
    │ @ItemSync(SAVE)                        │
    ├── ItemSyncAspect (AOP后置通知)         │
    │   ├── 查询保存后的完整商品对象           │
    │   ├── 转换为 ItemDoc                    │
    │   └── MQ.sendMsg("item.direct",        │
    │         "item.save", itemDoc) ────MQ───► ItemSyncListener
    │                                        │   ├── IndexRequest("items")
    │                                        │   └── client.index(request)
    │                                        │
    │ @ItemSync(DELETE)                      │
    └── MQ.sendMsg("item.direct",            │
              "item.delete", itemId) ──MQ───► ItemSyncListener
                                             │   ├── DeleteRequest("items")
                                             └─── client.delete(request)
```

```java
// 自定义注解
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ItemSync {
    Operation operation(); // SAVE, UPDATE, DELETE
}

// AOP 切面：@AfterReturning 后置通知
@Aspect @Component
public class ItemSyncAspect {
    @AfterReturning("@annotation(itemSync)")
    public void handleItemSync(JoinPoint joinPoint, ItemSync itemSync) {
        // DELETE → item.direct / item.delete → 发 id
        // SAVE/UPDATE → item.direct / item.save → 发 ItemDoc
    }
}
```

**为什么用 MQ 而非直接 Feign 调用 ES 写入？**
- **解耦**：item-service 不需要知道 search-service 的存在，不持有 ES Client 依赖
- **容错**：MQ 消息可持久化和重试，ES 暂时不可用时数据不丢失
- **削峰**：商品批量导入时大量写入请求排队，MQ 平滑处理

**5. `_id` 修复**（提交 `a49c47c`）：

测试批量导入时，ItemDoc 中虽然设置了 `id` 字段，但 ES 通过 JSON body 不认业务 id，会自动生成随机 `_id`。修复方式是在 `IndexRequest` 上显式设置文档 ID：

```java
// 修复前
new IndexRequest("items").source(json, XContentType.JSON);  // ES 自动生成随机 _id

// 修复后
new IndexRequest("items").id(itemDoc.getId()).source(json, XContentType.JSON);  // 使用业务 id
```

**重点**：ES 的文档 `_id` 和 JSON body 中的 `id` 字段是两回事——`_id` 是 ES 元数据（主键），body 中的 `id` 只是索引字段。不加 `.id()` 的话 ES 给每个文档生成随机 UUID，导致同一商品可能被重复索引多次。

知识点：
- **倒排索引**：词 → 文档列表的映射。搜索"手机"→ 找到包含"手机"的所有文档 ID → 返回。比 MySQL 的 B+Tree 全表扫描快数个数量级
- **TF-IDF / BM25**：ES 的默认相关性评分算法。TF（词频）× IDF（逆文档频率）决定文档与查询的匹配程度
- **Function Score Query**：在 BM25 评分基础上叠加自定义权重（如广告加权），实现业务排序干预
- **ES 近实时（Near Real-Time）**：写入后默认 1 秒后可搜索到（refresh_interval），不是实时但对商品搜索场景足够
- 对比 **Canal**（阿里的 MySQL Binlog 同步工具）：Canal 监听 MySQL Binlog 自动写 ES，完全解耦业务代码；AOP + MQ 方式更轻量、不依赖 Canal Server，但需要在业务代码中加注解

难点：
- **索引库与数据库的最终一致性**：MQ 消息可能丢失或重复，消费端要做幂等（IndexRequest 的 id 天然幂等——同一 id 多次写入就是覆盖更新）。但**消息积压**时索引库会暂时落后于数据库
- **ES 的内存**：聚合查询在内存中构建全局桶，数据量大时可能 OOM。需要设置合理的 `size` 参数和关闭不需要的 `fielddata`
- **高亮结果解析**：highlight 返回的是分词的 fragment 数组，需要拼接后替换原文；如果分词导致高亮片段不完整，需要前后文补全逻辑

---

## 系统架构总览

```
                         ┌─────────────┐
                         │   客户端     │
                         └──────┬──────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │   Gateway (:8080)      │
                    │   - 路由转发            │
                    │   - JWT 鉴权            │
                    │   - 动态路由（Nacos）    │
                    └───────┬───────────────┘
                            │
                    ┌───────┴───────┐
                    │    Nacos       │
                    │  - 注册中心     │
                    │  - 配置中心     │
                    └───────┬───────┘
                            │
      ┌───────┬───────┬─────┼─────┬───────┬───────┐
      │       │       │     │     │       │       │
      ▼       ▼       ▼     ▼     ▼       ▼       ▼
 ┌────────┐┌──────┐┌────┐┌────┐┌────┐┌────────┐┌───────┐
 │  item  ││search││cart││user││trade││  pay   ││   MQ  │
 │service ││svc   ││svc ││svc ││svc  ││service ││(Rabbit)│
 └───┬────┘└──┬───┘└──┬─┘└──┬─┘└──┬─┘└───┬────┘└───┬───┘
     │        │       │     │     │       │        │
     │   ┌────┘       │     │     │       │        │
     │   │            │     │     │       │        │
     ▼   ▼            ▼     ▼     ▼       ▼        ▼
 ┌──────┐  ┌────────────────────────────┐  ┌──────────┐
 │  ES  │  │        MySQL 数据库          │  │ 延迟消息  │
 │"items"│  │ item-service/user-service/trade-service│  │ 死信队列  │
 └──────┘  │ cart-service/pay-service/tscloud        │  └──────────┘
           └────────────────────────────┘
```

**交互方式总结**：

| 方式 | 场景 | 示例 |
|---|---|---|
| Feign 同步调用 | 查询类、强依赖 | cart → item 查商品信息 |
| Feign 同步调用 | 事务性写操作 | trade → item 扣库存 |
| MQ 异步消息 | 状态通知 | pay → trade 支付成功通知 |
| MQ 延迟消息 | 超时校验 | trade → trade 20s 后校验支付状态 |
| MQ 异步消息 | 数据同步 | item → search ES 索引同步 |

## 启动方式

1. **启动 Nacos**（本地 18848 端口，或修改 `bootstrap.yaml` 中的地址）
2. **启动 MySQL**，执行各模块对应的数据库初始化脚本
3. **启动 Elasticsearch**（搜索功能依赖，默认端口 19200）
4. **启动 RabbitMQ**（消息队列功能依赖）
5. **按顺序启动服务**：
   ```
   item-service → search-service → cart-service → user-service → trade-service → pay-service → gateway
   ```
6. **访问** `http://localhost:8080`

所有服务的启动顺序和重启方法参见 `docs/本地开发环境重启指南.md`。

## 可选改进方向

- **Sentinel 规则持久化**：当前规则存储在内存中，重启丢失——可接入 Nacos 作为 Sentinel 的数据源，实现规则持久化和动态生效
- **Seata AT 模式替换 XA**：XA 模式的高并发性能瓶颈——可改用 AT 模式（undo_log 自动回滚），在一致性和性能间取得更好平衡
- **消息可靠发送**：`pay.success` 等关键消息改用 `sendMessageWithConfirm` + 数据库本地消息表，确保支付通知不丢失
- **链路追踪**：Sleuth + Zipkin 将 userId 和 TraceId 串联，方便排查跨服务问题（MQ 消息传递 TraceId 是额外挑战）
- **增量路由更新**：DynamicRouteLoader 目前是全量替换，改为 diff 后增量更新，减少路由抖动
- **契约测试**：api-service 的 Feign 接口与实际 Controller 之间加 Spring Cloud Contract 验证
- **ES 升级**：`RestHighLevelClient` 在 7.15.0 已标记废弃，后续可迁移到新的 `ElasticsearchClient`（Java API Client），支持 lambda 表达式构建查询
- **容器化部署**：扩展到全部服务 + Docker Compose 编排（MySQL + Nacos + RabbitMQ + ES + 全部微服务）
