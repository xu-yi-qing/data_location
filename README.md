# 中国行政区划查询系统

本项目基于 [mumuy/data_location](https://github.com/mumuy/data_location) (MIT 许可) 改造的后端项目，使用 Spring Boot 2.0 + Java 8 将其原始数据封装为 REST API 服务，遵循 GB/T2260 国家标准。

内置前端测试页面，开箱即用。

## 功能

- **省市区三级联动**：按层级获取行政区划列表
- **历史沿革查询**：查询指定区县的当前名称及所有历史前身（如崇文区→东城区）
- **街道/镇/乡查询**：获取区县下辖的所有街道级数据
- **行政区划模糊搜索**：支持去除行政后缀和民族名称的智能模糊匹配
- **身份证号码解析**：解析出生日期、性别、签发地，并追溯历史代码对应的现行归属地

## 技术栈

| 类型 | 选型 |
|------|------|
| 框架 | Spring Boot 2.0.0.RELEASE |
| 语言 | Java 8 |
| 构建 | Maven |
| 数据存储 | 内存（JSON 文件启动时加载） |
| 前端 | 原生 HTML + JS，无外部依赖 |

## 快速启动

**前置要求**：JDK 8+、Maven 3.x

```bash
cd /Users/xuyiqing/IdeaProjects/data_location
mvn spring-boot:run
```

启动完成后访问：http://localhost:8080

## 项目结构

```
src/main/
├── java/com/example/location/
│   ├── LocationApiApplication.java   # 启动入口
│   ├── config/
│   │   └── WebConfig.java            # CORS 配置
│   ├── model/                        # 数据模型
│   │   ├── ApiResponse.java          # 统一响应包装
│   │   ├── RegionNode.java           # 区划节点（code + name）
│   │   ├── SearchResult.java         # 搜索结果（含完整路径名）
│   │   ├── HistoryResult.java        # 历史沿革结果
│   │   └── IdCardResult.java         # 身份证解析结果
│   ├── data/
│   │   └── RegionDataStore.java      # 数据仓库（启动加载 + 索引构建）
│   ├── service/
│   │   ├── CascadeService.java       # 三级联动逻辑
│   │   ├── SearchService.java        # 模糊搜索逻辑
│   │   ├── HistoryService.java       # 历史沿革查询
│   │   └── IdCardService.java        # 身份证解析
│   └── controller/
│       └── LocationController.java   # REST 接口
└── resources/
    ├── application.yml
    ├── static/
    │   ├── index.html                # 前端页面
    │   └── app.js                    # 前端交互逻辑
    └── data/
        ├── list.json                 # 当前有效区划（3432条）
        ├── list2.json                # 含中间节点的扩展数据（3576条）
        ├── history.json              # 历史已废止区划（7228条）
        ├── diff.json                 # 旧代码→新代码变更映射
        └── code/                     # 街道级数据（3209个文件）
```

## API 文档

所有接口返回统一结构：
```json
{ "code": 0, "data": "...", "message": null }
```

### 行政区划

| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| GET | `/api/regions/provinces` | — | 获取所有省级列表 |
| GET | `/api/regions/cities` | `province=110000` | 获取省下市级列表（直辖市返回空数组） |
| GET | `/api/regions/districts` | `city=130100` | 获取市下区县列表 |
| GET | `/api/regions/streets` | `district=130102` | 获取区县下街道列表 |
| GET | `/api/regions/search` | `q=朝阳&limit=10` | 模糊搜索，limit 最大 20 |
| GET | `/api/regions/history/{code}` | — | 查询区划历史沿革 |

### 身份证

| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| GET | `/api/idcard/parse` | `id=xxxxxx` | 解析身份证号码 |
| POST | `/api/idcard/parse` | `{"id":"xxxxxx"}` | 解析身份证号码 |

### 示例

```bash
# 获取省列表
curl http://localhost:8080/api/regions/provinces

# 获取北京市下的区县（直辖市直接用省级代码）
curl "http://localhost:8080/api/regions/districts?city=110000"

# 查询东城区历史沿革
curl http://localhost:8080/api/regions/history/110101

# 模糊搜索"朝阳"
curl "http://localhost:8080/api/regions/search?q=朝阳"

# 解析身份证
curl "http://localhost:8080/api/idcard/parse?id=110101199003077512"
```

## 数据说明

数据文件来源于原始项目，遵循《中华人民共和国行政区划代码》（GB/T2260）标准。

**行政区划代码结构**（6位）：
```
[1-2位] 省    [3-4位] 市    [5-6位] 区县
```

街道级为 9 位：前 6 位为区县代码，后 3 位为街道序号。

数据最后更新：**2026年3月**

## 数据加载策略

| 数据 | 加载时机 | 说明 |
|------|---------|------|
| list / list2 / history / diff | 启动时 | 加载进内存，同时构建搜索索引和反向 diff 索引 |
| code/*.json（街道） | 首次请求时 | 按需加载并缓存，`ConcurrentHashMap` 保证线程安全 |

启动内存约 10MB，建议 JVM 参数：`-Xmx256m`
