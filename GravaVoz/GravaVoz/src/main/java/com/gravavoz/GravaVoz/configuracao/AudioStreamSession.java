package com.gravavoz.GravaVoz.configuracao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import com.gravavoz.GravaVoz.service.SpeechToTextService;
import org.springframework.web.socket.WebSocketSession;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PreDestroy;

import org.springframework.web.socket.TextMessage;

public class AudioStreamSession {
    private final WebSocketSession webSocketSession;
    private final SpeechToTextService speechService;
    private SpeechClient speechClient;
    private ClientStream<StreamingRecognizeRequest> clientStream;
    private final AtomicBoolean isStreaming = new AtomicBoolean(false);
    private final List<String> transcriptBuffer = new ArrayList<>();
    private ResponseObserver<StreamingRecognizeResponse> responseObserver;

    private final BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();
    private Thread streamingWorkerThread;

    public AudioStreamSession(WebSocketSession webSocketSession, SpeechToTextService speechService) {
        this.webSocketSession = webSocketSession;
        this.speechService = speechService;
        this.sendConnectionMessage(webSocketSession.getId());
    }

    public void startStreaming() throws IOException {
        // Use AtomicBoolean to prevent multiple streams
        if (!isStreaming.compareAndSet(false, true)) {
            return; // Already streaming
        }

        this.speechClient = SpeechClient.create();
        System.out.println("Iniciando streaming de áudio para sessão: " + webSocketSession.getId());
        sendConnectionMessage(webSocketSession.getId());
        Recognition r = new Recognition();
        StreamingRecognitionConfig streamingConfig = r.recoginitionFeatures();

        this.responseObserver = new ResponseObserver<StreamingRecognizeResponse>() {
               
                @Override
                public void onStart(com.google.api.gax.rpc.StreamController controller) {
                    System.out.println("Stream do Google Speech-to-Text iniciado");
                }

                @Override
                public void onResponse(StreamingRecognizeResponse response) {
                    System.out.println("Resposta bruta do Google: " + response.toString());
                    sendActivityMessage( webSocketSession.getId(), response.toString());
                    
                    // Processa a resposta para extrair as transcrições
                    try {
                        for (StreamingRecognitionResult result : response.getResultsList()) {
                            if (result.getAlternativesList().size() > 0) {
                                String transcript = result.getAlternativesList().get(0).getTranscript();
                                double confidence = result.getAlternativesList().get(0).getConfidence();
                                
                                System.out.println("Transcript: " + transcript);
                                System.out.println("IsFinal: " + result.getIsFinal());
                                System.out.println("Confidence: " + confidence);
                                System.out.println("---");
                                
                                if (result.getIsFinal()) {
                                    transcriptBuffer.add(transcript);
                                    sendTranscriptionToClient(transcript, true, confidence);
                                    System.out.println("Transcrição FINAL: " + transcript);
                                } else {
                                    sendTranscriptionToClient(transcript, false, confidence);
                                    System.out.println("Transcrição INTERIM: " + transcript);
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Erro ao processar resposta do Speech-to-Text: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                @Override
                public void onError(Throwable t) {
                    System.err.println("Erro no Google Speech-to-Text: " + t.getMessage());
                    isStreaming.set(false);
                    
                }
                

                @Override
                public void onComplete() {
                    System.out.println("Stream do Google Speech-to-Text finalizado");
                    speechClient.shutdown();
                    speechClient.close();
                    stopStreaming();
                    isStreaming.set(false);
                    
                }
                
                private void sendTranscriptionToClient(String transcript, boolean isFinal, double confidence) {
                    try {
                        if (webSocketSession.isOpen()) {
                            ObjectMapper mapper = new ObjectMapper();
                            ObjectNode jsonMessage = mapper.createObjectNode();
                            jsonMessage.put("type", isFinal ? "FINAL_TRANSCRIPT" : "INTERIM_TRANSCRIPT");
                            jsonMessage.put("text", transcript);
                            jsonMessage.put("confidence", confidence);
                            jsonMessage.put("isFinal", isFinal);

                            String messageString = mapper.writeValueAsString(jsonMessage);
                            webSocketSession.sendMessage(new TextMessage(messageString));
                        }
                    } catch (Exception e) {
                        System.err.println("Erro ao enviar transcrição para cliente: " + e.getMessage());
                    }
                }
                
               
                   
                
                
                
            };

        this.clientStream = speechClient.streamingRecognizeCallable().splitCall(responseObserver);

        StreamingRecognizeRequest configRequest = StreamingRecognizeRequest.newBuilder()
                .setStreamingConfig(streamingConfig)
                .build();
        clientStream.send(configRequest);

        // Start the worker thread that consumes the queue
        startStreamingWorker();
    }

    public void sendConnectionMessage(String id) {
		
    	 try {
             if (webSocketSession.isOpen()) {
                 ObjectMapper mapper = new ObjectMapper();
                 ObjectNode jsonMessage = mapper.createObjectNode();
                 jsonMessage.put("type", "CONNECTION");
                 jsonMessage.put("id", id);

                 String messageString = mapper.writeValueAsString(jsonMessage);
                 webSocketSession.sendMessage(new TextMessage(messageString));
             }
         } catch (Exception e) {
             System.err.println("Erro ao enviar transcrição para cliente: " + e.getMessage());
         }
     
	}
    
    public void sendActivityMessage(String id, String activity) {
		
   	 try {
            if (webSocketSession.isOpen()) {
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode jsonMessage = mapper.createObjectNode();
                jsonMessage.put("type", activity);
                jsonMessage.put("id", id);

                String messageString = mapper.writeValueAsString(jsonMessage);
                webSocketSession.sendMessage(new TextMessage(messageString));
            }
        } catch (Exception e) {
            System.err.println("Erro ao enviar transcrição para cliente: " + e.getMessage());
        }
    
	}

    
    private void startStreamingWorker() {
        streamingWorkerThread = new Thread(() -> {
            final int TARGET_CHUNK_SIZE = 65536; 
            final int MAX_WAIT_MS = 1000;
            
            ByteArrayOutputStream bufferStream = new ByteArrayOutputStream(TARGET_CHUNK_SIZE);
            
            try {
                while (isStreaming.get()) {
                    byte[] audioData = audioQueue.poll(MAX_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (audioData != null && audioData.length > 0) {
                        bufferStream.write(audioData);
                    }
                    
                    if(bufferStream.size() >= TARGET_CHUNK_SIZE) {
                    	sendBufferedAudio(bufferStream.toByteArray());
                    	bufferStream.reset();
                    }
                }
                
                // Envia quaisquer dados remanescentes
                if (bufferStream.size() > 0) {
                    sendBufferedAudio(bufferStream.toByteArray());
                }
            } catch (InterruptedException e) {
                System.out.println("Worker thread de streaming interrompido.");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("Erro no worker thread ao enviar áudio: " + e.getMessage());
                isStreaming.set(false);
            } finally {
                try { bufferStream.close(); } catch (IOException ignored) {}
            }
        }, "AudioStreamWorker-" + webSocketSession.getId());

        streamingWorkerThread.start();
    }

    private void sendBufferedAudio(byte[] audioData) {
        if (!isStreaming.get() || clientStream == null) return;
        
        LocalTime time = LocalTime.now();
        System.out.println(time + ": Enviando chunk otimizado -> " + audioData.length);
        
        StreamingRecognizeRequest audioRequest = StreamingRecognizeRequest.newBuilder()
                .setAudioContent(ByteString.copyFrom(audioData))
                .build();
        clientStream.send(audioRequest);
    }

    public void processAudioChunk(byte[] audioData) {
        if (isStreaming.get()) {
            try {
                audioQueue.put(audioData);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Erro ao adicionar chunk na fila: " + e.getMessage());
            }
        }
    }

    
    public void pauseStreaming() {
        isStreaming.set(false);
        if (clientStream != null) {
            clientStream.closeSend();
            clientStream = null;
        }
    }
  
    public void stopStreaming() {
        isStreaming.set(false);
        
        if(responseObserver != null) {
        }
        
        if (clientStream != null) {
            clientStream.closeSend();
            clientStream = null;
        }
        if (streamingWorkerThread != null) {
            streamingWorkerThread.interrupt();
            streamingWorkerThread = null;
        }
    }
    
    @PreDestroy
    public void cleanup() {
        isStreaming.set(false);
        if (streamingWorkerThread != null) {
            streamingWorkerThread.interrupt();
        }
        audioQueue.clear();
        if (clientStream != null) {
            clientStream.closeSend();
        }
    }

    


}