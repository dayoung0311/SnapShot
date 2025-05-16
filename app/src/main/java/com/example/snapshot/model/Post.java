package com.example.snapshot.model;

import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class Post {
    private String postId;
    private String userId;
    private String userName;
    private String userProfilePic;
    private String imageUrl;
    private String caption;
    private Timestamp creationDate;
    private int likeCount;
    private int commentCount;
    private List<String> userLikes; // 좋아요를 누른 사용자 ID 목록
    private List<Tag> tags; // 태그 목록
    private List<String> tagNames; // 태그 이름 목록 (검색용)
    
    // 빈 생성자 - Firestore에 필요
    public Post() {
        userLikes = new ArrayList<>();
        tags = new ArrayList<>();
        tagNames = new ArrayList<>(); // 초기화
    }
    
    public Post(String postId, String userId, String userName, String userProfilePic, 
                String imageUrl, String caption) {
        this.postId = postId;
        this.userId = userId;
        this.userName = userName;
        this.userProfilePic = userProfilePic;
        this.imageUrl = imageUrl;
        this.caption = caption;
        this.creationDate = Timestamp.now();
        this.likeCount = 0;
        this.commentCount = 0;
        this.userLikes = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.tagNames = new ArrayList<>(); // 초기화
    }
    
    // 모든 필드를 받는 생성자 추가
    public Post(String postId, String userId, String userName, String userProfilePic,
                String imageUrl, String caption, Timestamp creationDate, List<Tag> tags) {
        this.postId = postId;
        this.userId = userId;
        this.userName = userName;
        this.userProfilePic = userProfilePic;
        this.imageUrl = imageUrl;
        this.caption = caption;
        this.creationDate = creationDate != null ? creationDate : Timestamp.now();
        this.likeCount = 0; // 기본값
        this.commentCount = 0; // 기본값
        this.userLikes = new ArrayList<>(); // 기본값
        // tags 설정 시 tagNames도 함께 설정
        setTags(tags != null ? tags : new ArrayList<>()); 
    }
    
    // Getter 및 Setter
    public String getPostId() {
        return postId;
    }
    
    public void setPostId(String postId) {
        this.postId = postId;
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
    
    public String getImageUrl() {
        return imageUrl;
    }
    
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
    
    public String getCaption() {
        return caption;
    }
    
    public void setCaption(String caption) {
        this.caption = caption;
    }
    
    public Timestamp getCreationDate() {
        return creationDate;
    }
    
    public void setCreationDate(Timestamp creationDate) {
        this.creationDate = creationDate;
    }
    
    public int getLikeCount() {
        return likeCount;
    }
    
    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }
    
    public int getCommentCount() {
        return commentCount;
    }
    
    public void setCommentCount(int commentCount) {
        this.commentCount = commentCount;
    }
    
    public List<String> getUserLikes() {
        return userLikes;
    }
    
    public void setUserLikes(List<String> userLikes) {
        this.userLikes = userLikes;
    }
    
    public List<Tag> getTags() {
        return tags;
    }
    
    public void setTags(List<Tag> tags) {
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
        // tagNames 리스트 업데이트
        updateTagNames(); 
    }
    
    public List<String> getTagNames() { // Getter 추가
        return tagNames;
    }

    public void setTagNames(List<String> tagNames) { // Setter 추가
        this.tagNames = tagNames;
    }
    
    public void addTag(Tag tag) {
        if (this.tags == null) {
            this.tags = new ArrayList<>();
        }
        if (!this.tags.contains(tag)) { // 중복 방지 (선택적)
        this.tags.add(tag);
            // tagNames 리스트 업데이트
            updateTagNames();
        }
    }

    public void removeTag(Tag tag) { // 태그 제거 메소드 (필요시)
        if (this.tags != null) {
            this.tags.remove(tag);
             // tagNames 리스트 업데이트
            updateTagNames();
        }
    }
    
    // tags 리스트를 기반으로 tagNames 리스트를 업데이트하는 헬퍼 메소드
    private void updateTagNames() {
        if (this.tags == null) {
            this.tagNames = new ArrayList<>();
            return;
        }
        this.tagNames = new ArrayList<>();
        for (Tag t : this.tags) {
            if (t != null && t.getName() != null) {
                this.tagNames.add(t.getName());
            }
        }
    }
} 