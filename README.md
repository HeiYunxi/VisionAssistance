# Frontend
前端项目--基于 Vue 3、Vite 和 Element Plus 构建。
## 技术栈
- **框架**: Vue 3.5.22
- **构建工具**: Vite 7.1.11
- **UI 库**: Element Plus 2.11.5
- **路由**: Vue Router 4.6.3
- **样式**: Sass/SCSS
- **开发工具**: Vue Devtools
## 前置条件
在开始之前，请确保开发环境中已安装以下版本的 Node.js：
- **Node.js**: `^20.19.0 || >=22.12.0`
可以通过运行 `node -v` 来检查您的版本。
## 前端快速开始
按照以下步骤来设置并运行项目。
### 1. 克隆项目
```bash
git clone https://github.com/HeiYunxi/VisionAssistance.git
cd frontend
```
### 2. 安装依赖
使用 npm 安装项目所需的所有依赖包。
```bash
npm install
```
### 3. 启动开发服务器（通常为本地）
运行以下命令启动开发服务器，它通常会自动在浏览器中打开页面。
```bash
npm run dev
```
应用将在 `http://localhost:5173` (或其他可用端口) 上运行。

---
## 项目配置与约定
为了更快地熟悉项目，以下是一些重要的配置和约定：
### 路径别名 `@`
在项目中，`@` 被配置为 `src` 目录的别名。这使得在导入文件时更加方便。
**示例:**
```javascript
// 代替
import MyComponent from '../../../components/MyComponent.vue'
// 可以使用
import MyComponent from '@/components/MyComponent.vue'
```
此配置在 `vite.config.js` 和 `jsconfig.json` 中均已设置，以确保 Vite 构建和 IDE（如 VS Code）都能正确识别。
### API 请求配置
为了方便与后端 API 通信，项目在 `src/main.js` 中设置了一个全局的基础 URL。
```javascript
app.config.globalProperties.$baseUrl = 'http://127.0.0.1:8080/'
```
**请注意**: 如果后端服务地址不同，请**务必修改** `main.js` 中的 `$baseUrl` 值。在组件中，可以通过 `this.$baseUrl` (Options API) 或 `getCurrentInstance().appContext.config.globalProperties.$baseUrl` (Composition API) 来访问它。
### 全局工具函数
`src/assets/js/common.js` 文件中的工具函数已被挂载为全局属性 `$commonJs`，可以在任何组件中通过 `this.$commonJs` (Options API) 来调用这些函数。

---
# backEndTest
这是一个基于 Spring Boot 的后端项目，旨在演示基本的 Web 服务功能，包括文件处理。
## 技术栈
- **Java**: 17
- **Spring Boot**: 3.5.7
- **构建工具**: Maven
- **核心依赖**:
    - `Spring Web`: 用于构建 RESTful Web 应用。
    - `Lombok`: 通过注解简化 Java 代码。
    - `Spring Boot Test`: 用于单元测试和集成测试。
## 前置条件
在开始之前，请确保您的开发环境中已安装以下软件：
- **JDK 17 或更高版本**: 项目编译和运行所必需。
- **IntelliJ IDEA**: 推荐使用 Ultimate（旗舰版）或 Community（社区版）。

## 后端快速开始（在 IntelliJ IDEA 中构建和运行）
### 1. 克隆项目
首先，将项目代码克隆到本地：
```bash
git clone https://github.com/HeiYunxi/VisionAssistance.git
cd backEndTest
```
### 2. 导入项目
1. 打开 IntelliJ IDEA。
2. 选择 `File` -> `Open`。
3. 在文件浏览器中，导航到您刚刚克隆的项目根目录，并选择 `pom.xml` 文件。
4. 点击 `Open`。
IDEA 会自动识别这是一个 Maven 项目，并开始下载和索引所需的依赖。请耐心等待右下角的进度条完成。
### 3. 运行项目
项目导入成功后，运行程序：
1. 在项目结构中，找到主启动类。路径通常为 `src/main/java/com/example/backEndTest/BackEndTestApplication.java`。
2. 打开该文件，在 `main` 方法的左侧有一个绿色的 **"运行"** 箭头。
3. 点击该箭头，然后选择 `Run 'BackEndTestApplication.main()'`。
### 4. 访问应用
项目启动成功后，您将在控制台看到 Spring Boot 的启动日志，包括 Tomcat 服务器的端口信息。根据 `application.properties` 的配置，应用运行在 **8080** 端口。
打开您的浏览器或使用 API 测试工具（如 Postman）访问：
[http://localhost:8080](http://localhost:8080)

## 项目配置说明
项目的核心配置位于 `src/main/resources/application.properties` 文件中：
```properties
# 应用名称
spring.application.name=backEndTest
# WEB 服务端口
server.port=8080
# 文件上传配置
# 单个文件最大大小
spring.servlet.multipart.max-file-size=10MB
# 整个请求最大大小
spring.servlet.multipart.max-request-size=20MB
# (已注释) 自定义文件上传目录
# app.upload.dir=./uploads
```
