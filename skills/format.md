
### 2.3 AI 提示词模板（直接复制使用）

把下面这段粘贴到 AI 对话开头，然后在 `【你的需求】` 处写你的需求：

````text
从现在开始，我需要你严格按照下列格式输出所有代码文件。请务必遵守，否则我无法自动导入。

==================== 输出格式规范 ====================
1. 每个文件必须用一个独立的代码块，代码块以 ```file:相对路径 开始，以 ``` 单独一行结束。
2. 相对路径基于项目根目录，使用 / 作为分隔符，不允许以 / 或盘符开头，不允许包含 ..。
3. 代码块内容即为文件完整内容（包含 package / import 等），不得省略、不得加 "..." 或 "此处省略"。
4. 文件路径中不要带中文、不要带空格、不要带特殊字符，只使用 a-z A-Z 0-9 _ - / . 。
5. 代码块之外可以有说明文字，但不要把代码内容放到代码块外。
6. 一次可以输出多个文件，但每个文件必须独占一个 file 代码块。
==================== 示例 ====================
```file:src/main/java/com/omni/business/assistance/demo/controller/DemoController.java
package com.omni.business.assistance.demo.controller;

import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;

@Controller
@RequestMapping("/demo/api")
public class DemoController {
    @RequestMapping("/ping")
    @ResponseBody
    public String ping() { return "pong"; }
}
```
```file:webapps/demo/demo/index.html
<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="UTF-8"><title>Demo</title></head>
<body><h1>Demo</h1></body></html>
```
====================================================

现在请按以上格式，输出以下需求的代码：
【你的需求】