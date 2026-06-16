package com.omni.business.assistance.screenshot.model;

import java.awt.image.BufferedImage;

public class ScreenshotResult {
    private BufferedImage image;
    private String recognizedText;
    private String matchedQuestion;
    private String matchedAnswer;
    private boolean found;

    public BufferedImage getImage() { return image; }
    public void setImage(BufferedImage image) { this.image = image; }
    public String getRecognizedText() { return recognizedText; }
    public void setRecognizedText(String recognizedText) { this.recognizedText = recognizedText; }
    public String getMatchedQuestion() { return matchedQuestion; }
    public void setMatchedQuestion(String matchedQuestion) { this.matchedQuestion = matchedQuestion; }
    public String getMatchedAnswer() { return matchedAnswer; }
    public void setMatchedAnswer(String matchedAnswer) { this.matchedAnswer = matchedAnswer; }
    public boolean isFound() { return found; }
    public void setFound(boolean found) { this.found = found; }
}