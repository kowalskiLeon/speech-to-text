package com.gravavoz.GravaVoz.configuracao;

import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gravavoz.GravaVoz.service.SpeechToTextService;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AudioWebSocketHandler extends TextWebSocketHandler { // Correct base class

	private final Map<String, AudioStreamSession> sessions = new ConcurrentHashMap<>();
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final SpeechToTextService speechService;

	// Constructor that Spring will use for injection
	public AudioWebSocketHandler(SpeechToTextService speechService) {
		this.speechService = speechService;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		System.out.println("New connection: " + session.getId());
		// The SpeechToTextService is now properly available to pass to the session.
		sessions.put(session.getId(), new AudioStreamSession(session, speechService));
		// session.sendMessage(new TextMessage("New connection: " + session.getId()));
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
		String payload = message.getPayload();
		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode jsonNode = objectMapper.readTree(payload);
		
		if ("start_streaming".equals(jsonNode.get("type").asText())) {
			System.out.println(jsonNode.get("type").asText()+" "+jsonNode.get("data").asText());
			startStreaming(session);
		}
		if ("audio_chunk".equals(jsonNode.get("type").asText())) {
			this.processAudioChunk(session, message);
		}
		
		if ("stop_streaming".equals(jsonNode.get("type").asText())) {
			System.out.println(jsonNode.get("type").asText()+" "+jsonNode.get("data").asText());
			this.stopStreaming(session);
		}

	}
	
	private void startStreaming(WebSocketSession session) throws Exception {
		AudioStreamSession audioSession = getAudioSession(session);
        if(audioSession != null) {
        	audioSession.startStreaming();
        	sessions.put(session.getId(), audioSession);
        }
	}
	
	private void stopStreaming(WebSocketSession session) throws Exception {
		AudioStreamSession audioSession = sessions.remove(session.getId());
		if (audioSession != null) {
			audioSession.stopStreaming();
		}
	}
	

	private void processAudioChunk(WebSocketSession session, TextMessage message) throws Exception {
    try {
        String payload = message.getPayload();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(payload);

        if ("audio_chunk".equals(jsonNode.get("type").asText())) {
            String base64Data = jsonNode.get("data").asText();
            byte[] audioBytes = Base64.getDecoder().decode(base64Data);
            
            // Get or create audio session for this WebSocket session
            AudioStreamSession audioSession = getAudioSession(session);
            if (audioSession == null) {
                // Create new session and start streaming ONCE
                audioSession = new AudioStreamSession(session, speechService);
                audioSession.startStreaming(); // Start the stream and worker thread
                sessions.put(session.getId(), audioSession);
                System.out.println("New audio session created and streaming started for: " + session.getId());
            }
            
            // This now just adds the chunk to the queue
            audioSession.processAudioChunk(audioBytes);
        }
    } catch (Exception e) {
        System.err.println("Error processing audio message: " + e.getMessage());
        e.printStackTrace();
        String errorResponse = String.format("{\"error\": \"%s\"}", e.getMessage());
        session.sendMessage(new TextMessage(errorResponse));
    }
}
	
	// Helper method to manage audio sessions
	private AudioStreamSession getAudioSession(WebSocketSession session) {
	    return sessions.get(session.getId());
	}

	// Map to store audio sessions (you need to declare this in your class)
	// private final Map<String, AudioStreamSession> audioSessions = new ConcurrentHashMap<>();

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		AudioStreamSession audioSession = sessions.remove(session.getId());
		if (audioSession != null) {
			audioSession.stopStreaming();
		}
		System.out.println("Conexão de audio fechada.");
	}
}