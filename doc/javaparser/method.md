方法级调用链 + 时序图 · 零依赖完整实现
基于你现有的 SimpleJavaLexer 扩展，不引入任何第三方依赖。所有新增文件 + 修改文件全量给出。

目录结构总览
text
【新增】src/main/java/com/omni/framework/codestats/method/
├── model/
│   ├── MethodInfo.java
│   ├── MethodCallEdge.java
│   ├── MethodCallGraph.java
│   └── CallTreeNode.java
├── extractor/
│   ├── MethodExtractor.java            ← 核心：基于 SimpleJavaLexer 手写
│   └── MethodExtractorBatch.java
├── algorithm/
│   └── CallTreeBuilder.java
└── export/
└── PlantUmlExporter.java

【新增】src/main/java/com/omni/business/assistance/codestat/
├── controller/
│   └── MethodAnalysisController.java
└── service/
└── MethodAnalysisService.java

【新增】webapps/demo/three/methodchain/
├── methodchain.html
├── methodchain.css
└── methodchain.js

【修改】
├── webapps/demo/three/scene/3d-scene.html   （+1 行）
└── webapps/demo/three/scene/3d-scene.js     （+1 行）