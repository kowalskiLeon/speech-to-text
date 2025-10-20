package com.gravavoz.GravaVoz.configuracao;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.protobuf.Duration;

public class Recognition {

    public StreamingRecognitionConfig recoginitionFeatures() {
        // 1. Build the VoiceActivityTimeout object
        StreamingRecognitionConfig.VoiceActivityTimeout voiceActivityTimeout = StreamingRecognitionConfig.VoiceActivityTimeout.newBuilder()
                .setSpeechEndTimeout(
                        Duration.newBuilder()
                                .setSeconds(5) // Set the timeout duration to 1 second
                                .setNanos(0)
                                .build()
                )
                .build();

        // 2. Build and return the full StreamingRecognitionConfig
        StreamingRecognitionConfig config = StreamingRecognitionConfig.newBuilder()
                .setConfig(
                        RecognitionConfig.newBuilder()
                                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                                .setSampleRateHertz(16000)
                                .setLanguageCode("pt-BR")
                                .setModel("command_and_search")
                                .setUseEnhanced(true)
                                .setProfanityFilter(false)
                                .build()
                )
                .setInterimResults(true)
                .setEnableVoiceActivityEvents(true) // Enable voice activity events
                .setSingleUtterance(true)
                .setVoiceActivityTimeout(voiceActivityTimeout) // Apply the timeout settings
                .build();

        return config;
    }
}