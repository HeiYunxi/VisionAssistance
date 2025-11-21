package backEndTest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:D:\\Project\\VisionAssistance\\backEndTest\\Files\\processedDir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /files/** 的请求映射到本地的 uploads 目录
//        registry.addResourceHandler("/Files/**").addResourceLocations("file:Files/");
        registry.addResourceHandler("/uploads/**").addResourceLocations("file:uploads/");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**") // 1. 对所有 /api/ 路径下的请求应用CORS配置
                .allowedOrigins("http://localhost:5173") // 2. 允许来自这个源的请求
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 3. 允许的HTTP方法
                .allowedHeaders("*") // 4. 允许所有请求头
                .allowCredentials(true) // 5. 允许发送Cookie信息（如果需要）
                .maxAge(3600); // 6. 预检请求的有效期，单位为秒

        // *** 增加这一行，允许静态文件访问 ***
        registry.addMapping("/files/**")
                .allowedOrigins("http://localhost:5173") ;
    }
}