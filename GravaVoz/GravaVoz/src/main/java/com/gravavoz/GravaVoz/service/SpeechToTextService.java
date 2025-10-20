package com.gravavoz.GravaVoz.service;

import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class SpeechToTextService {

    public String transcribeAudio(byte[] audioData, String languageCode, String encoding, int sampleRateHertz) throws IOException {
        try (SpeechClient speechClient = SpeechClient.create()) {

            ByteString audioBytes = ByteString.copyFrom(audioData);
            RecognitionAudio audio = RecognitionAudio.newBuilder().setContent(audioBytes).build();

            // Converter a string encoding para o enum correspondente
            RecognitionConfig.AudioEncoding audioEncoding = RecognitionConfig.AudioEncoding.valueOf(encoding);

            RecognitionConfig config =
                RecognitionConfig.newBuilder()
                    .setEncoding(audioEncoding)
                    .setSampleRateHertz(sampleRateHertz)
                    .setLanguageCode(languageCode)
                    .setModel("command_and_search")
                    .build();

            RecognizeResponse response = speechClient.recognize(config, audio);
            java.util.List<SpeechRecognitionResult> results = response.getResultsList();

            StringBuilder transcribedText = new StringBuilder();
            for (SpeechRecognitionResult result : results) {
                SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                transcribedText.append(alternative.getTranscript());
            }
            System.out.println(transcribedText.toString());
            return transcribedText.toString();
        }
    }
}