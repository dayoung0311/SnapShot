package com.example.snapshot.model;

import com.google.firebase.Timestamp;

public class Notification {
    public static final String TYPE_LIKE = "like";
    public static final String TYPE_COMMENT = "comment";
    public static final String TYPE_FOLLOW = "follow";
    public static final String TYPE_TAG = "tag";
    
    private String notificationId;
    private String userId;           // 알림을 받는 사용자
    private String senderId;         // 알림을 발생시킨 사용자
    private String senderName;       // 발신자 이름
    private String senderProfilePic; // 발신자 프로필 사진
    private String notificationType; // 알림 유형 (좋아요, 댓글, 팔로우, 태그)
    private String targetId;         // 관련 객체 ID (포스트, 댓글, 태그 등)
    private String content;          // 알림 내용
    private boolean isRead;          // 읽음 여부
    private Timestamp creationDate;  // 생성 시간
    
    // 빈 생성자 - Firestore에 필요
    public Notification() {
    }
    
    public Notification(String notificationId, String userId, String senderId, 
                       String senderName, String senderProfilePic, 
                       String notificationType, String targetId, String content) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.senderId = senderId;
        this.senderName = senderName != null ? senderName : "";
        this.senderProfilePic = senderProfilePic != null ? senderProfilePic : "";
        this.notificationType = notificationType;
        this.targetId = targetId;
        this.content = content != null ? content : "";
        this.isRead = false;
        this.creationDate = Timestamp.now();
    }
    
    // Getter 및 Setter
    public String getNotificationId() {
        return notificationId;
    }
    
    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getSenderId() {
        return senderId;
    }
    
    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }
    
    public String getSenderName() {
        return senderName;
    }
    
    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }
    
    public String getSenderProfilePic() {
        return senderProfilePic;
    }
    
    public void setSenderProfilePic(String senderProfilePic) {
        this.senderProfilePic = senderProfilePic;
    }
    
    public String getNotificationType() {
        return notificationType;
    }
    
    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }
    
    public String getTargetId() {
        return targetId;
    }
    
    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public boolean isRead() {
        return isRead;
    }
    
    public void setRead(boolean read) {
        isRead = read;
    }
    
    public Timestamp getCreationDate() {
        return creationDate;
    }
    
    public void setCreationDate(Timestamp creationDate) {
        this.creationDate = creationDate;
    }
    
    // 포스트 좋아요 알림 생성 팩토리 메서드
    public static Notification createLikeNotification(String userId, String senderId, 
                                                    String senderName, String senderProfilePic,
                                                    String postId) {
        String content = senderName + "님이 회원님의 게시물을 좋아합니다.";
        return new Notification(null, userId, senderId, senderName, senderProfilePic, 
                               TYPE_LIKE, postId, content);
    }
    
    // 포스트 댓글 알림 생성 팩토리 메서드
    public static Notification createCommentNotification(String userId, String senderId, 
                                                      String senderName, String senderProfilePic,
                                                      String postId, String commentContent) {
        String content = senderName + "님이 회원님의 게시물에 댓글을 남겼습니다: " + 
                        (commentContent.length() > 30 ? commentContent.substring(0, 30) + "..." : commentContent);
        return new Notification(null, userId, senderId, senderName, senderProfilePic, 
                               TYPE_COMMENT, postId, content);
    }
    
    // 팔로우 알림 생성 팩토리 메서드
    public static Notification createFollowNotification(String userId, String senderId, 
                                                     String senderName, String senderProfilePic) {
        String content = senderName + "님이 회원님을 팔로우합니다.";
        return new Notification(null, userId, senderId, senderName, senderProfilePic, 
                               TYPE_FOLLOW, userId, content);
    }
    
    // 태그 알림 생성 팩토리 메서드
    public static Notification createTagNotification(String userId, String senderId, 
                                                  String senderName, String senderProfilePic,
                                                  String tagId, String tagName) {
        String content = senderName + "님이 '" + tagName + "' 태그에 새 게시물을 추가했습니다.";
        return new Notification(null, userId, senderId, senderName, senderProfilePic, 
                               TYPE_TAG, tagId, content);
    }
} 