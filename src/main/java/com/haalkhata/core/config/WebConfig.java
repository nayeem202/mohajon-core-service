package com.haalkhata.core.config;

import java.nio.file.Paths;
import com.haalkhata.feature.subscription.application.SubscriptionEntitlementInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties props;
    private final SubscriptionEntitlementInterceptor subscriptionEntitlementInterceptor;

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(subscriptionEntitlementInterceptor).addPathPatterns("/api/v1/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(props.getStorageDir()).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/api/v1/files/**")
                .addResourceLocations(location);
    }
}
