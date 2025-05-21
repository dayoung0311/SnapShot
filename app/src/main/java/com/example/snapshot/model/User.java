package com.example.snapshot.model;

import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.List;
import com.google.firebase.firestore.PropertyName;

public class User {
    private String userId;
    private String username;
    private String email;
    private String profilePicUrl;
    private String bio;
    private int followerCount;
    private int followingCount;
    private Timestamp creationDate;
    private List<String> followers;
    private List<String> following;
    private List<String> savedTags;
    private boolean restricted; // 신고로 인한 제한 상태
    private String restrictedReason; // 제한 이유
    
    // 빈 생성자 - Firestore에 필요
    public User() {
        followers = new ArrayList<>();
        following = new ArrayList<>();
        savedTags = new ArrayList<>();
        restricted = false; // 기본값은 제한 아님
    }
    
    public User(String userId, String username, String email, String profilePicUrl) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.profilePicUrl = profilePicUrl;
        this.bio = "";
        this.followerCount = 0;
        this.followingCount = 0;
        this.creationDate = Timestamp.now();
        this.followers = new ArrayList<>();
        this.following = new ArrayList<>();
        this.savedTags = new ArrayList<>();
        this.restricted = false; // 기본값은 제한 아님
        this.restrictedReason = "";
    }
    
    // Getter 및 Setter
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getProfilePicUrl() {
        return profilePicUrl;
    }
    
    public void setProfilePicUrl(String profilePicUrl) {
        this.profilePicUrl = profilePicUrl;
    }
    
    public String getBio() {
        return bio;
    }
    
    public void setBio(String bio) {
        this.bio = bio;
    }
    
    public int getFollowerCount() {
        return followerCount;
    }
    
    public void setFollowerCount(int followerCount) {
        this.followerCount = followerCount;
    }
    
    public int getFollowingCount() {
        return followingCount;
    }
    
    public void setFollowingCount(int followingCount) {
        this.followingCount = followingCount;
    }
    
    public Timestamp getCreationDate() {
        return creationDate;
    }
    
    public void setCreationDate(Timestamp creationDate) {
        this.creationDate = creationDate;
    }
    
    public List<String> getFollowers() {
        return followers;
    }
    
    public void setFollowers(List<String> followers) {
        this.followers = followers;
    }
    
    public List<String> getFollowing() {
        return following;
    }
    
    public void setFollowing(List<String> following) {
        this.following = following;
    }

    // savedTags Getter 및 Setter 추가
    public List<String> getSavedTags() {
        return savedTags;
    }

    public void setSavedTags(List<String> savedTags) {
        this.savedTags = savedTags;
    }

    public boolean isRestricted() {
        return restricted;
    }

    public void setRestricted(boolean restricted) {
        this.restricted = restricted;
    }

    public String getRestrictedReason() {
        return restrictedReason;
    }

    public void setRestrictedReason(String restrictedReason) {
        this.restrictedReason = restrictedReason;
    }

    /*
    // 사용자 이름 가져오기 (이메일에서 @ 앞부분 사용) -> username 필드를 직접 사용하도록 변경됨
    public String getUsernameFromEmail() { // 이름 변경 예시
        if (email != null && email.contains("@")) {
            return email.split("@")[0];
        }
        return username; // username 필드 반환
    }
    */
} 