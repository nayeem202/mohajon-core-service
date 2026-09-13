package com.haalkhata.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String jwtSecret;
    private String trustedJwtSecret;
    private int accessTokenMinutes = 10080;
    private int refreshTokenDays = 30;
    private int verificationCodeTtlMinutes = 10;
    private int otpTtlMinutes = 5;
    private String storageDir = "./storage";
    private String corsAllowedOrigins = "*";
    private String mailProvider = "console";
    private String googleClientId = "";
    private boolean logRequestBody = true;
    private Bkash bkash = new Bkash();

    @Data
    public static class Bkash {
        private String baseUrl = "";
        private String appKey = "";
        private String appSecret = "";
        private String username = "";
        private String password = "";
        private String callbackUrl = "";
        private boolean sandbox = true;
    }
}
