package com.example.snapshot.model;

import com.google.firebase.Timestamp;

public class Report {
    public static final String TYPE_POST = "post";
    public static final String TYPE_USER = "user";
    
    private String reportId;
    private String reporterId; // 신고한 사용자 ID
    private String targetId;   // 신고된 대상 ID (포스트 ID 또는 사용자 ID)
    private String type;       // 신고 타입 (post 또는 user)
    private String reason;     // 신고 이유
    private Timestamp createdAt; // 신고 날짜
    
    // Firestore에 필요한 기본 생성자
    public Report() {
        this.createdAt = Timestamp.now();
    }
    
    public Report(String reporterId, String targetId, String type, String reason) {
        this.reporterId = reporterId;
        this.targetId = targetId;
        this.type = type;
        this.reason = reason;
        this.createdAt = Timestamp.now();
    }
    
    // Getter와 Setter 메서드
    public String getReportId() {
        return reportId;
    }
    
    public void setReportId(String reportId) {
        this.reportId = reportId;
    }
    
    public String getReporterId() {
        return reporterId;
    }
    
    public void setReporterId(String reporterId) {
        this.reporterId = reporterId;
    }
    
    public String getTargetId() {
        return targetId;
    }
    
    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getReason() {
        return reason;
    }
    
    public void setReason(String reason) {
        this.reason = reason;
    }
    
    public Timestamp getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
} 