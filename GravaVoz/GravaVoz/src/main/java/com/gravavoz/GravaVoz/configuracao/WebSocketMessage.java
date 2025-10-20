package com.gravavoz.GravaVoz.configuracao;


public class WebSocketMessage {
 private String type;
 private String data;
 private Double metering;
 
 // Getters e Setters
 public String getType() { return type; }
 public void setType(String type) { this.type = type; }
 
 public String getData() { return data; }
 public void setData(String data) { this.data = data; }
 
 public Double getMetering() { return metering; }
 public void setMetering(Double metering) { this.metering = metering; }
}