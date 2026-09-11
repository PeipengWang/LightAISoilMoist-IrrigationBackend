package org.example.lightaisoilmoistirrigationbackend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LightAI Soil Moist Irrigation Backend")
                        .version("1.0.0")
                        .description("智能土壤湿度灌溉系统后端 API 文档")
                        .contact(new Contact()
                                .name("PeipengWang")));
    }
}