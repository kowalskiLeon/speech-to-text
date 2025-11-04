package com.gravavoz.GravaVoz.configuracao;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.protobuf.Duration;

public class Recognition {

    public StreamingRecognitionConfig recoginitionFeatures() {
        StreamingRecognitionConfig.VoiceActivityTimeout voiceActivityTimeout = StreamingRecognitionConfig.VoiceActivityTimeout.newBuilder()
                .setSpeechEndTimeout(
                        Duration.newBuilder()
                                .setSeconds(5)
                                .setNanos(0)
                                .build()
                )
                .build();

        StreamingRecognitionConfig config = StreamingRecognitionConfig.newBuilder()
                .setConfig(
                        RecognitionConfig.newBuilder()
                                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                                .setSampleRateHertz(16000)
                                .setAudioChannelCount(1)
                                .setLanguageCode("pt-BR")
                                .setModel("latest_long")
                                .setUseEnhanced(true)
                                .setProfanityFilter(false)
                                .setEnableAutomaticPunctuation(true)
                                .build()
                )
                .setInterimResults(true)
                .setEnableVoiceActivityEvents(true)

                .build();

        return config;
    }
}