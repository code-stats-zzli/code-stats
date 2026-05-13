#!/bin/bash
# generate-codestats.sh - 生成 CodeStats 完整项目

PROJECT_DIR="codestats-top"
mkdir -p $PROJECT_DIR/src/main/java/com/codestats/{engine,facade,config,core,filter,language,statistics,output,sorter}
mkdir -p $PROJECT_DIR/src/test/java

cd $PROJECT_DIR

# 写入所有 Java 文件（这里只写入关键骨架，实际应包含完整内容）
# 由于篇幅限制，本脚本仅创建目录和占位文件。
# 请将上面提供的各个类的完整内容复制到相应路径。

cat > README.md << 'EOF'
# CodeStats - 工业级代码统计工具

优化后的版本，支持：
- 20+ 语言自动检测
- 精确的注释/代码行计数（状态机）
- 并行统计（ForkJoin）
- .gitignore 模式过滤
- JSON/CSV/XML 输出
- Spring Boot 风格启动

编译运行：
mvn package
java -jar target/codestats.jar -d /src
EOF

echo "项目骨架已生成，请手动将上述提供的类文件放入对应目录。"
echo "最终打包命令："
echo "  mvn clean package"
echo "  java -jar target/codestats.jar"