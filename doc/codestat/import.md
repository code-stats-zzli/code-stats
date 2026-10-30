从现在起，请严格按下列格式输出代码，否则我无法自动导入。

【格式规范】
1. 若要交付一个模块：先用一个 ```omni-pack 块写元数据（JSON），再用多个 ```file:相对路径 块写文件内容。
2. 若只交付几个文件：直接用多个 ```file:相对路径 块。
3. 相对路径基于项目根，使用 / 分隔，禁止以 / 或盘符开头，禁止 ..。
4. 代码块内容即完整文件内容，不得省略、不得写 "..."。
5. 每个文件独占一个 file 代码块，块之间可写说明文字。

【示例】
```omni-pack
{"name":"user-points","version":"1.0","targetRoot":"src/main/java","conflict":"OVERWRITE"}
```

```file:com/omni/points/PointsController.java
package com.omni.points.controller;
...
```

```file:com/omni/points/PointsService.java
package com.omni.points.service;
...
```

现在请按以上格式输出：
【你的需求】


src/main/java/com/omni/business/assistance/importfile/
├── controller/ImportFileController.java
├── model/
│   ├── ImportFile.java
│   ├── ImportPack.java
│   ├── ParseResult.java
│   └── ImportResult.java
├── parser/
│   ├── ImportFormatParser.java            ← 接口（策略）
│   ├── FileBlockFormatParser.java         ← ```file:path ``` 格式
│   ├── OmniPackFormatParser.java          ← ```omni-pack ``` 扩展格式
│   ├── MergeFileFormatParser.java         ← // ====== path ======
│   └── ImportFormatRegistry.java          ← 注册 + 探测
└── service/ImportFileService.java