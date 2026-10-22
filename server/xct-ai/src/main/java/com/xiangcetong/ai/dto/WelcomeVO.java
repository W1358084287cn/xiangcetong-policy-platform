package com.xiangcetong.ai.dto;

/**
 * 欢迎页摘要 VO。
 */
public class WelcomeVO {

    /** 问候语：下午好，张三峰！ */
    private String greeting;
    /** 摘要数据 */
    private Summary summary;
    /** 快捷建议词 */
    private String[] quickSuggestions;
    /** 导航链接（含路径和标签） */
    private QuickLink[] quickLinks;

    public static WelcomeVO create(String greeting, Summary summary, String... suggestions) {
        WelcomeVO vo = new WelcomeVO();
        vo.greeting = greeting;
        vo.summary = summary;
        vo.quickSuggestions = suggestions;
        return vo;
    }

    public String getGreeting() { return greeting; }
    public void setGreeting(String greeting) { this.greeting = greeting; }
    public Summary getSummary() { return summary; }
    public void setSummary(Summary summary) { this.summary = summary; }
    public String[] getQuickSuggestions() { return quickSuggestions; }
    public void setQuickSuggestions(String[] quickSuggestions) { this.quickSuggestions = quickSuggestions; }
    public QuickLink[] getQuickLinks() { return quickLinks; }
    public void setQuickLinks(QuickLink[] quickLinks) { this.quickLinks = quickLinks; }

    /**
     * 快捷导航链接。
     */
    public static class QuickLink {
        private String label;
        private String path;
        private String icon;

        public QuickLink() {}
        public QuickLink(String label, String path, String icon) {
            this.label = label;
            this.path = path;
            this.icon = icon;
        }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
    }

    /**
     * 管家摘要数据。
     */
    public static class Summary {
        private int matchedPolicies;
        private int expiringPolicies;
        private int pendingApplies;
        private int profileCompleteness;

        public int getMatchedPolicies() { return matchedPolicies; }
        public void setMatchedPolicies(int matchedPolicies) { this.matchedPolicies = matchedPolicies; }
        public int getExpiringPolicies() { return expiringPolicies; }
        public void setExpiringPolicies(int expiringPolicies) { this.expiringPolicies = expiringPolicies; }
        public int getPendingApplies() { return pendingApplies; }
        public void setPendingApplies(int pendingApplies) { this.pendingApplies = pendingApplies; }
        public int getProfileCompleteness() { return profileCompleteness; }
        public void setProfileCompleteness(int profileCompleteness) { this.profileCompleteness = profileCompleteness; }
    }
}