package com.gravavoz.GravaVoz.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.gravavoz.GravaVoz.dto.TranscriptionRequestDTO;
import com.gravavoz.GravaVoz.service.SpeechToTextService;

import java.io.IOException;

@RestController
@RequestMapping("/api/speech")
@CrossOrigin(origins = "*") // Allows requests from your React Native app
public class SpeechController {

    @Autowired
    private SpeechToTextService speechToTextService;

    @PostMapping("/transcribe")
    public ResponseEntity<?> transcribeAudio(@RequestBody TranscriptionRequestDTO request) {
        try {
            String transcript = speechToTextService.transcribeAudio(
                request.getAudioData(),
                request.getLanguageCode(),
                request.getEncoding(),
                request.getSampleRateHertz() // Agora é Integer, pode ser nulo
            );
            return ResponseEntity.ok().body(java.util.Map.of("transcript", transcript));
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(
                java.util.Map.of("error", "Transcription failed: " + e.getMessage())
            );
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(
                java.util.Map.of("error", "Invalid request: " + e.getMessage())
            );
        }
    }
}