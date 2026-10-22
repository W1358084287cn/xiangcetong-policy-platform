package com.xiangcetong.ai.dto;

/**
 * AI 对话请求。
 */
public class ChatRequest {

    private String question;
    private String sessionId;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}