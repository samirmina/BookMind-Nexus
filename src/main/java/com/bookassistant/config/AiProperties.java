package com.bookassistant.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {
    @NotNull
    private String baseUrl = "http://localhost:11434/api/generate";

    @NotNull
    private String model = "phi3.5:latest";

    @NotNull
    private String embeddingModel = "nomic-embed-text";

    private String apiKey = "";

    @Min(1)
    private int timeoutSeconds = 120;

    @Min(1)
    private int numPredict = 1024;

    @Min(1)
    private int numCtx = 16384;

    @Min(-1)
    private int numGpu = 100;

    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax("2.0")
    private double temperature = 0.2;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double topP = 0.9;

    @Min(1)
    private int topK = 40;

    @DecimalMin("0.0")
    private double repeatPenalty = 1.1;

    @DecimalMin("0.0")
    private double presencePenalty = 0.0;

    @DecimalMin("0.0")
    private double frequencyPenalty = 0.0;

    @Min(1)
    private int maxPredictScale = 3;

    private boolean think = false;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getNumPredict() {
        return numPredict;
    }

    public void setNumPredict(int numPredict) {
        this.numPredict = numPredict;
    }

    public int getNumCtx() {
        return numCtx;
    }

    public void setNumCtx(int numCtx) {
        this.numCtx = numCtx;
    }

    public int getNumGpu() {
        return numGpu;
    }

    public void setNumGpu(int numGpu) {
        this.numGpu = numGpu;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getTopP() {
        return topP;
    }

    public void setTopP(double topP) {
        this.topP = topP;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getRepeatPenalty() {
        return repeatPenalty;
    }

    public void setRepeatPenalty(double repeatPenalty) {
        this.repeatPenalty = repeatPenalty;
    }

    public double getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    public int getMaxPredictScale() {
        return maxPredictScale;
    }

    public void setMaxPredictScale(int maxPredictScale) {
        this.maxPredictScale = maxPredictScale;
    }

    public boolean isThink() {
        return think;
    }

    public void setThink(boolean think) {
        this.think = think;
    }
}
