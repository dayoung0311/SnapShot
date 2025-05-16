package com.example.snapshot.model;

import com.google.firebase.Timestamp;

public class Comment {
    private String commentId;
    private String postId;
    private String userId;
    private String userName;
    private String userProfileImageUrl;
    private String text;
    private Timestamp timestamp;
    private String parentId; // ID of the parent comment, null for top-level comments
    private int depth;       // Depth level (0 for top-level, 1 for replies)
    
    // Default constructor required for calls to DataSnapshot.getValue(Comment.class)
    public Comment() {
    }
    
    public Comment(String commentId, String postId, String userId, String userName, String userProfileImageUrl, String text, Timestamp timestamp, String parentId, int depth) {
        this.commentId = commentId;
        this.postId = postId;
        this.userId = userId;
        this.userName = userName;
        this.userProfileImageUrl = userProfileImageUrl;
        this.text = text;
        this.timestamp = timestamp;
        this.parentId = parentId;
        this.depth = depth;
    }
    
    // Getters and setters for all fields
    
    public String getCommentId() {
        return commentId;
    }
    
    public void setCommentId(String commentId) {
        this.commentId = commentId;
    }
    
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
    
    public String getUserProfileImageUrl() {
        return userProfileImageUrl;
    }
    
    public void setUserProfileImageUrl(String userProfileImageUrl) {
        this.userProfileImageUrl = userProfileImageUrl;
    }
    
    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    public Timestamp getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getParentId() {
        return parentId;
    }
    
    public void setParentId(String parentId) {
        this.parentId = parentId;
    }
    
    public int getDepth() {
        return depth;
    }
    
    public void setDepth(int depth) {
        this.depth = depth;
    }
} 