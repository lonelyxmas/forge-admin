package com.mdframe.forge.plugin.job.config;

import com.mdframe.forge.plugin.job.support.JobApiTokenCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JobOpenApiSecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JobApiTokenCodec jobApiTokenCodec(JobProperties properties) {
        if (Boolean.TRUE.equals(properties.getOpenApi().getEnabled())) {
            properties.getOpenApi().validatedTokenPepper();
        }
        return new JobApiTokenCodec(properties);
    }
}
