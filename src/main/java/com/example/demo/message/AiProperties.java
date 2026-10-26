package com.example.demo.message;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    private String baseUrl = "";
    private String apiKey = "";
    @NotBlank private String model = "gpt-5.6-luna";
    @Min(256) @Max(128000) private int maxOutputTokens = 8192;
    @Min(10) @Max(600) private int timeoutSeconds = 180;
}
