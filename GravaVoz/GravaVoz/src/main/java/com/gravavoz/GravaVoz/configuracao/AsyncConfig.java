package com.gravavoz.GravaVoz.configuracao;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.context.annotation.Bean;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {
 
 @Bean(name = "audioTaskExecutor")
 public Executor audioTaskExecutor() {
     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
     executor.setCorePoolSize(5);
     executor.setMaxPoolSize(10);
     executor.setQueueCapacity(100);
     executor.setThreadNamePrefix("AudioStream-");
     executor.initialize();
     return executor;
 }
}