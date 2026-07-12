# P1 契约

- `openapi/`：从 `/api/v1` 开始的 HTTP 契约。
- `asyncapi/`：跨服务异步事件契约。
- `json-schema/`：可复用的事件信封与消息负载 Schema。
- `contract-validator/`：无需启动基础设施的 YAML/JSON 语法和本地 `$ref` 校验测试。

执行校验：

```powershell
& 'D:\Java_Tools\Maven\apache-maven-3.9.6\bin\mvn.cmd' -s 'D:\Java_Tools\Maven\apache-maven-3.9.6\conf\settings.xml' test
```
