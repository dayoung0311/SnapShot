package com.example.snapshot.ui.notifications;

/**
 * 알림 데이터 모델 클래스
 */
public class NotificationItem {
    
    public static final int TYPE_LIKE = 1;
    public static final int TYPE_COMMENT = 2;
    public static final int TYPE_FOLLOW = 3;
    public static final int TYPE_TAG_LIKE = 4;
    
    private String notificationId; // 알림 ID
    private String userId;
    private String userName;
    private String userProfilePic;
    private String notificationText;
    private long timestamp;
    private int type;
    private String targetId; // 게시물 ID, 사용자 ID, 태그 ID 등
    private String contentImageUrl; // 알림 관련 콘텐츠 이미지 URL
    private boolean isRead; // 읽음 상태
    
    public NotificationItem(String userId, String userName, String userProfilePic, 
                           String notificationText, long timestamp, int type, 
                           String targetId, String contentImageUrl) {
        this.userId = userId;
        this.userName = userName;
        this.userProfilePic = userProfilePic;
        this.notificationText = notificationText;
        this.timestamp = timestamp;
        this.type = type;
        this.targetId = targetId;
        this.contentImageUrl = contentImageUrl;
        this.isRead = false;
    }
    
    // 알림 ID 추가를 위한 생성자
    public NotificationItem(String notificationId, String userId, String userName, String userProfilePic, 
                          String notificationText, long timestamp, int type, 
                          String targetId, String contentImageUrl, boolean isRead) {
        this.notificationId = notificationId;
        this.userId = userId;
        this.userName = userName;
        this.userProfilePic = userProfilePic;
        this.notificationText = notificationText;
        this.timestamp = timestamp;
        this.type = type;
        this.targetId = targetId;
        this.contentImageUrl = contentImageUrl;
        this.isRead = isRead;
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
    
    public String getUserName() {
        return userName;
    }
    
    public void setUserName(String userName) {
        this.userName = userName;
    }
    
    public String getUserProfilePic() {
        return userProfilePic;
    }
    
    public void setUserProfilePic(String userProfilePic) {
        this.userProfilePic = userProfilePic;
    }
    
    public String getNotificationText() {
        return notificationText;
    }
    
    public void setNotificationText(String notificationText) {
        this.notificationText = notificationText;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public int getType() {
        return type;
    }
    
    public void setType(int type) {
        this.type = type;
    }
    
    public String getTargetId() {
        return targetId;
    }
    
    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }
    
    public String getContentImageUrl() {
        return contentImageUrl;
    }
    
    public void setContentImageUrl(String contentImageUrl) {
        this.contentImageUrl = contentImageUrl;
    }
    
    public boolean isRead() {
        return isRead;
    }
    
    public void setRead(boolean read) {
        isRead = read;
    }
} 