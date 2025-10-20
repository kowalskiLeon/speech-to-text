package com.gravavoz.GravaVoz.dto;
//TranscriptionRequest.java

public class TranscriptionRequestDTO {
	private byte[] audioData;
    private String languageCode;
    private String encoding;
    private int sampleRateHertz; 
 // Getters and Setters
 public byte[] getAudioData() {
     return audioData;
 }
 public void setAudioData(byte[] audioData) {
     this.audioData = audioData;
 }
 public String getLanguageCode() {
     return languageCode;
 }
 public void setLanguageCode(String languageCode) {
     this.languageCode = languageCode;
 }
public String getEncoding() {
	return encoding;
}
public void setEncoding(String encoding) {
	this.encoding = encoding;
}
public int getSampleRateHertz() {
	return sampleRateHertz;
}
public void setSampleRateHertz(int sampleRateHertz) {
	this.sampleRateHertz = sampleRateHertz;
}
 
 
 
}