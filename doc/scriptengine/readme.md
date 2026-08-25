src/main/java/com/omni/
├── framework/
│   └── scriptexec/                    # 脚本引擎核心框架
│       ├── executor/
│       │   ├── ScriptExecutor.java
│       │   ├── BaseScriptExecutor.java
│       │   ├── exception/ScriptExecutionException.java
│       │   ├── model/ScriptSource.java
│       │   └── impl/
│       │       ├── PythonExecutor.java
│       │       ├── JavaExecutor.java
│       │       ├── CExecutor.java
│       │       ├── CppExecutor.java
│       │       └── RestExecutor.java
│       ├── engine/
│       │   ├── ScriptEngine.java
│       │   └── DefaultScriptEngine.java
│       └── config/
│           └── ScriptExecutorRegistry.java   # 执行器注册（替代Spring @Bean）
├── business/
│   └── assistance/
│       └── scriptengine/               # 业务模块
│           ├── controller/
│           │   └── ScriptEngineController.java
│           ├── service/
│           │   └── ScriptExecutionService.java
│           ├── dao/
│           │   └── ScriptDao.java      # 可选，用于数据库存储脚本
│           └── model/
│               └── Task.java           # 任务封装（从power-calc复制）
└── webapps/demo/
└── scriptengine/                  # 前端页面
├── index.html
├── style.css
└── script.js