package com.gravavoz.GravaVoz.configuracao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.gravavoz.GravaVoz.service.SpeechToTextService;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private SpeechToTextService speechToTextService; // Inject the service

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Spring will now use the @Bean method below, correctly injecting the service.
        registry.addHandler(new AudioWebSocketHandler(speechToTextService), "/audio-stream")
                .setAllowedOrigins("*"); // Allow all origins for testing
    }
}