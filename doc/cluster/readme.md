集群管理 · 两种部署方式完整实现
一、方案总览
两种方式都不依赖 Agent，核心区别是 Worker 进程由谁启动：

方式	谁启动 Worker	需要目标机提供什么	适用场景
方式 A：主服务推送	主服务器通过 SSH 连到目标机执行 scp + ssh	Linux/Windows 开 SSH（Win10+ 内置 OpenSSH Server），配密钥或密码	内网服务器集群
方式 B：手动部署	用户在目标机手动执行命令	目标机已有 JDK	员工 PC、跨 NAT、演示
共同点：

主服务提供 resources/deploy/*.jar 的下载地址（带 Token）

主服务维护节点注册中心 + Token

Worker 启动后 主动向主服务注册 + 心跳（这部分在 Bootstrap 里改造）

前端是同一个「集群管理」tab，部署时选方式

方式 A 内部又分 3 个子通道（统一 DeployChannel 接口）：

local — 目标就是主服务本机 → ProcessBuilder

ssh — 目标有 SSH → ProcessBuilder("ssh/scp")

manual — 只生成命令，不自动执行