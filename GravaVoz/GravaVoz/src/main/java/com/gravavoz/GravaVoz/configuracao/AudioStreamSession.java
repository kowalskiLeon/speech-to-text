package com.gravavoz.GravaVoz.configuracao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.cloud.speech.v1.*;
import com.google.cloud.speech.v1.StreamingRecognitionConfig.VoiceActivityTimeout;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.gravavoz.GravaVoz.service.SpeechToTextService;
import org.springframework.web.socket.WebSocketSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.socket.TextMessage;

public class AudioStreamSession {
    private final WebSocketSession webSocketSession;
    private final SpeechToTextService speechService;
    private SpeechClient speechClient;
    private com.google.api.gax.rpc.ClientStream<StreamingRecognizeRequest> clientStream;
    private final AtomicBoolean isStreaming = new AtomicBoolean(false);
    private final List<String> transcriptBuffer = new ArrayList<>();

    // Critical: Use a BlockingQueue to buffer audio chunks
    private final BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();
    private Thread streamingWorkerThread;

    public AudioStreamSession(WebSocketSession webSocketSession, SpeechToTextService speechService) {
        this.webSocketSession = webSocketSession;
        this.speechService = speechService;
    }

    public void startStreaming() throws IOException {
        // Use AtomicBoolean to prevent multiple streams
        if (!isStreaming.compareAndSet(false, true)) {
            return; // Already streaming
        }

        this.speechClient = SpeechClient.create();
        System.out.println("Iniciando streaming de áudio para sessão: " + webSocketSession.getId());
        Recognition r = new Recognition();
        // Configuração do streaming (mantida igual)
        StreamingRecognitionConfig streamingConfig = r.recoginitionFeatures();

        // Response observer (mantido igual)
        com.google.api.gax.rpc.ResponseObserver<StreamingRecognizeResponse> responseObserver = 
            new com.google.api.gax.rpc.ResponseObserver<StreamingRecognizeResponse>() {
                
                @Override
                public void onStart(com.google.api.gax.rpc.StreamController controller) {
                    System.out.println("Stream do Google Speech-to-Text iniciado");
                }

                @Override
                public void onResponse(StreamingRecognizeResponse response) {
                    System.out.println("Resposta bruta do Google: " + response.toString());
                    
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
                                    // Transcrição final - adiciona ao buffer e envia para o cliente
                                    transcriptBuffer.add(transcript);
                                    sendTranscriptionToClient(transcript, true, confidence);
                                    System.out.println("Transcrição FINAL: " + transcript);
                                } else {
                                    // Transcrição intermediária - envia para feedback em tempo real
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
                            System.out.println("Enviado para cliente: " + messageString);
                        }
                    } catch (Exception e) {
                        System.err.println("Erro ao enviar transcrição para cliente: " + e.getMessage());
                    }
                }
                
                
                
            };

        this.clientStream = speechClient.streamingRecognizeCallable().splitCall(responseObserver);

        // Envia a configuração inicial
        StreamingRecognizeRequest configRequest = StreamingRecognizeRequest.newBuilder()
                .setStreamingConfig(streamingConfig)
                .build();
        clientStream.send(configRequest);

        // Start the worker thread that consumes the queue
        startStreamingWorker();
    }

    /**
     * Starts a dedicated thread to send audio data from the queue to Google.
     */
    private void startStreamingWorker() {
        streamingWorkerThread = new Thread(() -> {
            try {
                while (isStreaming.get() || !audioQueue.isEmpty()) {
                    // This call blocks until an audio chunk is available or the timeout occurs
                    byte[] audioData = audioQueue.poll(400, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (audioData != null && isStreaming.get() && clientStream != null) {
                        StreamingRecognizeRequest audioRequest = StreamingRecognizeRequest.newBuilder()
                                .setAudioContent(ByteString.copyFrom(audioData))
                                .build();
                        clientStream.send(audioRequest);
                        //System.out.println("Chunk de áudio enviado (" + audioData.length + " bytes)");
                    }
                }
            } catch (InterruptedException e) {
                System.out.println("Worker thread de streaming interrompido.");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("Erro no worker thread ao enviar áudio: " + e.getMessage());
                isStreaming.set(false);
            }
        }, "AudioStreamWorker-" + webSocketSession.getId());

        streamingWorkerThread.start();
    }

    public void processAudioChunk(byte[] audioData) {
        // Simply add the chunk to the queue. The worker thread will handle it.
        if (isStreaming.get()) {
            try {
                audioQueue.put(audioData);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Erro ao adicionar chunk na fila: " + e.getMessage());
            }
        } else {
            System.err.println("Ignorando chunk de áudio: streaming não está ativo.");
        }
    }

    // The methods processSpeechResponse, sendTranscriptionToClient, sendErrorToClient,
    // stopStreaming, cleanup, isStreaming, and getFinalTranscript remain the SAME as in your original code.

    public void stopStreaming() {
        isStreaming.set(false);
        if (clientStream != null) {
            clientStream.closeSend();
            clientStream = null;
        }
        if (streamingWorkerThread != null) {
            streamingWorkerThread.interrupt();
            streamingWorkerThread = null;
        }
    }

    // ... (Keep your existing cleanup, isStreaming, and getFinalTranscript methods)
}