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

集群管理 · 功能实现 / 文件作用 / 实现讲解
下面按「分层架构 → 单文件职责 → 端到端流程 → 设计要点」四块讲。每个文件都会回答三个问题：它是干什么的、谁调它、它调谁。

一、分层总览
text
┌──────────────────────────────────────────────────────────────┐
│  前端 cluster.js / cluster.css / sys.html         [UI 层]    │
└──────────────────────────┬───────────────────────────────────┘
│ HTTP JSON
┌──────────────────────────▼───────────────────────────────────┐
│  ClusterController  (路由 + Token 校验 + 下载)     [接入层]    │
└──────────────────────────┬───────────────────────────────────┘
│
┌──────────────────────────▼───────────────────────────────────┐
│  ClusterService    (编排 + 节点注册表 + 部署包 + 心跳) [业务层]│
│         │                                                     │
│         ├── DeployChannel (接口)                              │
│         │     ├── LocalDeployChannel                          │
│         │     ├── SshDeployChannel                            │
│         │     └── ManualDeployChannel                         │
└─────────┴───────────────────────────────────────────────────┘
│ 模型
┌──────────────────────────▼───────────────────────────────────┐
│  ClusterNode / DeployRequest / JdkInfo            [模型层]   │
└──────────────────────────────────────────────────────────────┘
▲
│ HTTP 注册/心跳
┌──────────────────────────┴───────────────────────────────────┐
│  WorkerBootstrap  (独立于 Spring，JDK 原生 HTTP)  [自举层]    │
│      由 Bootstrap.main 在 isWorker() 时优先调用               │
└──────────────────────────────────────────────────────────────┘
一句话理解整条链：

浏览器 → ClusterController → ClusterService → 选一条 DeployChannel → 目标机起 Worker → Worker 用 WorkerBootstrap 回注册 → ClusterService 更新节点表 → 前端轮询看到 RUNNING

二、逐文件讲解
2.1 入口层
📄 src/main/java/com/omni/Bootstrap.java
项	内容
定位	应用唯一入口，Worker/主服务共用
职责	① 解压 jar 内资源 ② Worker 模式下先启动注册线程 ③ 再启动 Spring
改动点	相比原版只多了 4 行
为什么这么改	Worker 注册不能依赖 Spring 启动完成：如果 Worker 自己 Spring 启动失败，也必须向主服务报告"我挂了"。所以注册线程必须在 SpringApplication.run 之前启动
java
AppResourceExtractor.extractIfNeeded();
if (WorkerBootstrap.isWorker(args)) {   // ← 新增
WorkerBootstrap.start(args);        // ← 起守护线程注册+心跳
}
SpringApplication.run(Bootstrap.class, args);
2.2 模型层（三个纯 POJO）
📄 model/ClusterNode.java
项	内容
定位	节点在内存中的唯一表示，主节点和 Worker 都用它
关键字段	nodeId / role / channel / state / host / port / jarName / deployDir / javaPath / pid / lastHeartbeat
方法	只有 toView()：把内部字段转成前端友好的 Map（多了 uptimeMs 等派生态）
为什么独立 toView	内部字段（如 masterUrl / instanceId）没必要全暴露给前端，toView 是序列化边界
状态机（这个字段决定了 UI 显示什么颜色）：

text
PENDING → STARTING → RUNNING → STOPPING → OFFLINE
↘ 心跳 45s 超时 → UNKNOWN
↘ 异常 → ERROR
📄 model/DeployRequest.java
项	内容
定位	前端一次性提交的部署意图
关键字段	channel（决定走哪条通道）、jarName、port、javaPath，SSH 专用 host/sshUser/sshKeyPath/sshPort
设计意图	用同一个 DTO 承载三种通道参数，ClusterService.deploy 内部按 channel 分流，避免为每个通道建一个 Controller 方法
📄 model/JdkInfo.java
项	内容
定位	JDK 探测结果
结构	currentVersion + candidates[]（每个候选含 path + version）
设计意图	不单返回一个版本，因为目标机往往装多个 JDK，用户需要自己选哪个 java 去启动 Worker，所以必须返回列表
2.3 通道层（策略模式核心）
📄 channel/DeployChannel.java（接口）
项	内容
定位	策略模式接口，把"在哪台机器执行命令"抽象为 4 个动作
4 个方法	checkJdk / probePort / deploy / stop
返回 DTO	DeployResult { success, manualOnly, pid, startCommand, downloadUrl, logs }
设计意图	manualOnly 字段让"手动模式"和"自动模式"共用同一个接口 —— 手动模式不执行动作，只生成命令，前端看到 manualOnly=true 就展示复制块
为什么这样抽象：

如果不用接口，ClusterService.deploy 里就会写 if (channel.equals("ssh")) {...} else if (channel.equals("local")) {...}，新增通道（比如未来加 WinRM）就要改 Service

现在新增通道 = 新增一个实现类 + 注册到 Map，Service 一行不改

📄 channel/LocalDeployChannel.java
项	内容
定位	主服务本机通道，用 ProcessBuilder
checkJdk	按 JAVA_HOME → PATH → 常见安装目录 → 当前 JVM 四级扫描，每找到一个就 java -version 解析
probePort	new ServerSocket().bind(p) 逐个试探，失败就 p++
deploy	① Files.copy 拷 jar ② 拼 java -jar ... --spring.profiles.active=worker ③ pb.directory(deployDir) + redirectOutput(appendTo(logFile)) ④ start() 拿 pid
stop	ProcessHandle.of(pid).destroy() + 遍历 descendants() 杀子进程
关键点	pb.directory() 必须设绝对路径，否则 Worker 的 user.dir 是主服务的，日志和临时文件会互相污染
📄 channel/SshDeployChannel.java
项	内容
定位	Linux 服务器通道，用系统 ssh / scp 命令
checkJdk	一条命令合并探测：java -version; which java; echo $JAVA_HOME，然后 ls /usr/lib/jvm/*/bin/java 补全候选
probePort	用 bash 的 /dev/tcp 语法：(echo > /dev/tcp/127.0.0.1/N) && echo OCCUPIED || echo FREE，比装 nc 更通用
deploy	① ssh host "mkdir -p 目录" ② scp jar host:/路径 ③ ssh host "cd 目录 && nohup java -jar ... &" → echo $! 拿 pid
stop	pkill -f 'cluster.node.id=xxx'
关键点	① -o StrictHostKeyChecking=no 首次免交互 ② ConnectTimeout=10 防连不上卡死 ③ runProcess 里读 stderr 合并进 stdout，否则 SSH 错误看不到
为什么用外部命令而不是 JSch：

JSch 需要额外依赖（约 300KB），而 ssh/scp 是 Linux/macOS 标配，Windows 10+ 也有

外部命令错误信息原汁原味，便于排查

缺点：不支持密码交互（见"问题"章节）

📄 channel/ManualDeployChannel.java
项	内容
定位	不执行任何远程操作，只返回两样东西
deploy 返回	① downloadUrl：带 Token 的一次性下载地址 ② startCommand：完整的启动命令（参数和 local/ssh 一模一样）
stop 返回	kill <pid> 或 taskkill /F /PID <pid>，让用户自己执行
设计意图	用户拿到命令后，在任何机器上执行都行（PC、云主机、容器），只要它能访问到主服务的 masterUrl。Worker 注册逻辑完全复用，UI 上它会自己出现在节点列表里
2.4 服务层（大脑）
📄 service/ClusterService.java
这是整个模块最核心的文件，承担 6 件事：

职责	具体实现	关键点
① 主节点身份	构造时从 .cluster/master.id 读，不存在就生成	必须持久化，否则重启后 masterId 变了，Worker 记得的旧 ID 全废
② Token 管理	从 .cluster/master.token 读/生成，verifyToken(t) 做比对	所有外部请求（注册/心跳/下载）都靠它鉴权
③ 通道注册表	CHANNELS = { local, ssh, manual }	新增通道只改这一行
④ 部署包管理	listPackages() 扫 resources/deploy/*.jar，resolvePackage() 做路径穿越防护	jarName.contains("..") 拒绝
⑤ 节点注册表	NODES = ConcurrentHashMap<nodeId, ClusterNode>，主节点构造时入表	主节点自己是 role=MASTER
⑥ 部署编排	deploy(req)：校验 → 建 node → 调 channel.deploy → 入表	统一入口，前端只调这一个
⑦ 心跳巡检	后台线程每 15s 扫一遍，now - lastHeartbeat > 45s 就标 UNKNOWN	用 daemon 线程，不阻塞 JVM 退出
部署核心编排（伪代码）：

java
public Map deploy(DeployRequest req) {
// 1) 校验：jarName、port、deployDir
Path jar = resolvePackage(req.getJarName());       // 穿越防护
if (req.getPort() <= 0) return err("请先探测端口");

    // 2) 造节点（此时还没启动）
    ClusterNode node = new ClusterNode();
    node.setNodeId(UUID.randomUUID()...);
    node.setState("STARTING");
    node.setPort(req.getPort());
    node.setJarMd5(md5(jar));    // 前端可见，便于排查

    // 3) 交给通道执行（3 种实现，Service 不关心细节）
    DeployChannel ch = channel(req.getChannel());
    DeployChannel.DeployResult r = ch.deploy(req, jar, node, masterUrl, masterToken);

    // 4) 结果分流
    if (r.manualOnly) {
        // 手动模式：返回命令 + 下载地址，不入表（等 Worker 回注册才入）
        NODES.put(node.getNodeId(), node);   // 先放 STARTING，让用户看到"等待注册"
        return { success, nodeId, downloadUrl, startCommand };
    } else {
        // 自动模式：直接入表
        node.setPid(r.pid);
        NODES.put(node.getNodeId(), node);
        return { success, nodeId, pid };
    }
}
为什么 Service 不直接拼命令：因为 local 和 ssh 的拼法不同（一个 ProcessBuilder(List)，一个 ssh host "字符串"），而且 Windows/Linux 转义规则不同。命令拼装必须收敛在通道内部。

2.5 接入层
📄 controller/ClusterController.java
接口	职责	关键点
GET /master	返回主节点信息	展示 tokenPreview（前 8 位），不暴露完整 Token
GET /packages	部署包列表	带 md5 便于前端校验
GET /packages/download	下载 jar	① token 从 URL 取（因为要浏览器直接下载）② 校验 service.verifyToken() ③ resolvePackage 防穿越 ④ sendStatic 流式返回
POST /check-jdk	探测 JDK	转发给 service.checkJdk → 通道
POST /probe-port	端口探测	同上
POST /deploy	部署	核心接口
POST /stop	停止	转发给通道的 stop
POST /node/register	Worker 注册	⚠️ X-Cluster-Token 从 Header 取（不是 URL），防止日志泄露
POST /node/heartbeat	心跳	同上
POST /node/offline	优雅下线	同上
GET /nodes	节点列表	前端 3s 轮询
两个鉴权方式的区别（这是易错点）：

下载接口：Token 在 URL 里（?token=xxx），因为浏览器 <a href> 或 window.open 没法加 Header

注册/心跳：Token 在 Header（X-Cluster-Token: xxx），因为调用方是我们自己的 Worker，可以自由加 Header，Header 更安全

2.6 Worker 自举层
📄 cluster/WorkerBootstrap.java
项	内容
定位	Worker 启动时注册 + 心跳的执行者
为什么独立于 Spring	① 不依赖 IoC ② 只用 HttpURLConnection，零第三方依赖 ③ 即使 Worker Spring 启动失败，它也能先注册
关键方法
isWorker(args)	遍历 args 找 --cluster.role=worker，由 Bootstrap 调用
start(args)	从 args 解析 masterUrl/token/nodeId/port，起守护线程做注册+心跳；注册 shutdown hook 做优雅下线
registerWithRetry	最多 30 次 × 2s，应对主服务还没启动 / 网络抖动
heartbeatLoop	每 5s 一次，一直跑
postJson	手写 HTTP POST，X-Cluster-Token 放 Header
为什么必须"注册成功才进入心跳"：

java
Thread t = new Thread(() -> {
registerWithRetry(...);     // 阻塞直到成功（或 60s 超时）
heartbeatLoop(...);         // 只有注册成功才进心跳
});
否则 Worker 在注册前就开始心跳，主服务收到心跳但不知道这个节点是谁 → 日志一堆 warn。

为什么用 shutdown hook 发 offline：Worker 被 kill 时，ProcessHandle.destroy() 会发 SIGTERM，JVM 会触发 shutdown hook，此时赶紧告诉主服务"我下线了"，主服务把状态从 RUNNING 改成 OFFLINE，不用等 45s 心跳超时。

2.7 前端层
📄 webapps/demo/sys/modules/cluster/cluster.js
项	内容
定位	严格遵守 SysCenter 模块契约，只调用 SysCenter.register
结构	① 顶部主节点卡片（一列 key-value）② 节点列表（表格 + 3s 轮询）③ 部署区（通道 Tab + 表单 + 结果展示）
关键交互
① 通道切换（Tab）

javascript
channelTab.click → 切换 currentChannel
├─ 'ssh'    → 显示 sshRow (host/user/key)，隐藏其他
├─ 'local'  → 显示 localRow (deployDir)
└─ 'manual' → 隐藏所有，显示提示文案
② 检测 JDK 后动态渲染候选列表

javascript
checkJdk() → POST /check-jdk
→ 返回 candidates[]
→ 渲染成一列 radio（默认选第一个）
→ 用户点哪个，selectedJavaPath 就变
③ 探测端口后回填

javascript
probePort() → POST /probe-port → { port: 28081 }
→ $('portInput').value = 28081
→ checkEnable() 启用部署按钮
④ 部署结果分两种展示

自动模式：显示"✅ 部署成功 nodeId=xxx pid=xxx"

手动模式：显示两块可复制的黑框（downloadUrl 和 startCommand），用户点「📋 复制」就行

⑤ 生命周期管理（关键）

javascript
pollTimer = setInterval(() => {
if (!document.body.contains(container)) {   // ← 切走 tab 时自动停
clearInterval(pollTimer);
return;
}
loadNodes();
}, 3000);

window.addEventListener('beforeunload', () => clearInterval(pollTimer));
这一段是 SysCenter 模块的标配：切走 tab → container 从 DOM 移除 → 下一轮轮询发现不在 DOM 里 → 自动清理定时器。否则用户切来切去会堆叠 N 个轮询。

📄 webapps/demo/sys/modules/cluster/cluster.css
只做三件事：

卡片、表单、表格的基础样式（对齐 global-tech.css 变量）

状态徽章 state-MASTER / state-RUNNING / state-STARTING / ... 不同颜色

manual-block 的黑框样式 + 右上角 copy-btn

📄 webapps/demo/sys/sys.html（修改）
只加两行：

html
<link rel="stylesheet" href="modules/cluster/cluster.css">
<script src="modules/cluster/cluster.js"></script>
这是 SysCenter 架构的价值：新增一个功能 = 新增一个目录 + 两行 HTML，sys.js 一行不改。

三、端到端流程（三种通道对比）
场景 1：SSH 推送
text
[用户点部署]
│
▼
cluster.js POST /cluster/api/deploy { channel:'ssh', host, sshUser, ... }
│
▼
ClusterController.deploy → ClusterService.deploy
│
├─ resolvePackage("xxx.jar")  → resources/deploy/xxx.jar  (防穿越)
├─ new ClusterNode { state:STARTING, port:28081 }
└─ SshDeployChannel.deploy
├─ ssh host "mkdir -p /opt/workers/xxx"
├─ scp jar host:/opt/workers/xxx/
├─ ssh host "cd /opt/workers/xxx && nohup java -jar ... & echo $!"
└─ 返回 { pid: 12345, logs:[...] }
│
▼
NODES.put(nodeId, node)  ← 节点进入内存，状态 STARTING
│
▼
前端轮询 /nodes → 看到 STARTING
│
▼ (几秒后，Worker 起来了)
WorkerBootstrap.registerWithRetry → POST /cluster/api/node/register { nodeId, port, ... }
│
▼
ClusterService.register → 状态变 RUNNING，lastHeartbeat=now
│
▼
前端轮询 → 显示绿色 RUNNING ✅
场景 2：手动下载
text
[用户点部署]
│
▼
cluster.js POST /cluster/api/deploy { channel:'manual', jarName, port }
│
▼
ManualDeployChannel.deploy
├─ 不执行任何动作
├─ 生成 downloadUrl = masterUrl + /packages/download?jar=xxx&token=xxx
└─ 生成 startCommand = "java -jar xxx.jar --cluster.role=worker ..."
│
▼
Service 返回 { success, manualOnly:true, downloadUrl, startCommand }
│
▼
前端渲染两个可复制黑框 → 用户复制
│
▼ (用户在另一台机器操作)
浏览器下载 jar → 目标机执行 startCommand
│
▼
Worker 起来 → 同 SSH 场景，自动注册回来
关键差异：手动模式的节点必须提前入表（state=STARTING），否则 Worker 上线后调用 register，Service 一看 Map 里没这个 nodeId 会怎么处理？—— 本实现是允许不存在的 nodeId 直接注册（register() 里 existing == null 分支直接 put），所以两种都行，但提前入表能让用户立刻看到"等待注册"的占位，体验更好。

场景 3：本机
和场景 1 完全一样，只是把 SshDeployChannel 换成 LocalDeployChannel（ProcessBuilder 直接启动）。Service 和 Controller 一行不改 —— 这就是策略模式的价值。

四、关键设计要点回顾
设计点	解决什么问题
DeployChannel 策略接口	3 种部署通道共享 Controller/Service，新增通道零侵入
WorkerBootstrap 独立于 Spring	Worker 即使 Spring 启动失败也能上报；不引入额外依赖
manualOnly 字段	"自动执行"和"只生成命令"共用一套接口，前端只判断一个标志位
Token 在 URL/Header 分场景	下载走 URL（浏览器限制），注册/心跳走 Header（更安全）
master.id + master.token 持久化	主服务重启后身份不变，Worker 能继续注册
心跳 45s 超时 + shutdown hook 主动下线	正常退出秒级感知，异常退出 45s 内感知
SysCenter.register 契约	新模块 = 新目录 + 两行 HTML，sys.js 不改
pb.directory() 绝对路径	Worker 的 user.dir 独立，日志/临时文件不污染主服务
--spring.profiles.active=worker	Worker 关闭 GUI / 精简连接池（必须同批上线，否则 Worker 启动弹窗）
五、上线前必须做的三件事
配 cluster.master.url：application.properties 里改成主服务的真实局域网 IP，否则 Worker 部署到别的机器后注册不回来（默认值是 127.0.0.1，只有本机能访问）。

Worker 端 profile 改造：ScreenshotRunner / DataSourceUtils 加 spring.profiles.active=worker 分支，关闭截图助手、把连接池缩到 maxActive=2。

准备 SSH 密钥（仅 SSH 通道）：主服务机 ssh-keygen + ssh-copy-id 到目标机，并在目标机放好 JDK。

这三件事做完，三种通道都能端到端跑通。