package com.xiangcetong.ai.dto;

import java.util.List;

/**
 * AI 对话响应（非流式，用于历史记录）。
 */
public class ChatResponse {

    private String answer;
    private List<SourceInfo> sources;
    private boolean fromKnowledge;

    public ChatResponse() {
    }

    public ChatResponse(String answer, List<SourceInfo> sources, boolean fromKnowledge) {
        this.answer = answer;
        this.sources = sources;
        this.fromKnowledge = fromKnowledge;
    }

    public static ChatResponse direct(String answer) {
        return new ChatResponse(answer, List.of(), false);
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<SourceInfo> getSources() {
        return sources;
    }

    public void setSources(List<SourceInfo> sources) {
        this.sources = sources;
    }

    public boolean isFromKnowledge() {
        return fromKnowledge;
    }

    public void setFromKnowledge(boolean fromKnowledge) {
        this.fromKnowledge = fromKnowledge;
    }

    /**
     * 来源引用信息。
     */
    public static class SourceInfo {
        private Long documentId;
        private String title;
        private String snippet;

        public SourceInfo() {
        }

        public SourceInfo(Long documentId, String title, String snippet) {
            this.documentId = documentId;
            this.title = title;
            this.snippet = snippet;
        }

        public Long getDocumentId() {
            return documentId;
        }

        public void setDocumentId(Long documentId) {
            this.documentId = documentId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSnippet() {
            return snippet;
        }

        public void setSnippet(String snippet) {
            this.snippet = snippet;
        }
    }
}