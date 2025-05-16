package com.example.snapshot.repository;

import com.example.snapshot.model.Comment;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

public class CommentRepository {
    private static final String COMMENTS_COLLECTION = "comments";
    private static final String POSTS_COLLECTION = "posts";
    private static final String TAG = "CommentRepository";
    
    private final FirebaseFirestore firestore;
    
    // 싱글톤 패턴
    private static CommentRepository instance;
    
    public static CommentRepository getInstance() {
        if (instance == null) {
            instance = new CommentRepository();
        }
        return instance;
    }
    
    private CommentRepository() {
        firestore = FirebaseFirestore.getInstance();
    }
    
    // 댓글 작성하기
    public Task<Void> addComment(Comment comment, String postId) {
        DocumentReference commentRef = firestore.collection(COMMENTS_COLLECTION).document();
        String commentId = commentRef.getId();
        
        comment.setCommentId(commentId);
        
        // 트랜잭션으로 댓글 추가 및 포스트 댓글 수 업데이트
        return firestore.runTransaction(transaction -> {
            // 포스트 문서 가져오기
            DocumentReference postRef = firestore.collection(POSTS_COLLECTION).document(postId);
            DocumentSnapshot postSnapshot = transaction.get(postRef);
            
            // 현재 댓글 수 가져오기
            long commentCount = postSnapshot.getLong("commentCount") != null ? 
                    postSnapshot.getLong("commentCount") : 0;
            
            // 댓글 수 1 증가
            transaction.update(postRef, "commentCount", commentCount + 1);
            
            // 새 댓글 저장
            transaction.set(commentRef, comment);
            
            return null;
        });
    }
    
    // 댓글 삭제하기
    public Task<Void> deleteComment(String commentId, String postId) {
        // 트랜잭션으로 댓글 삭제 및 포스트 댓글 수 업데이트
        return firestore.runTransaction(transaction -> {
            // 포스트 문서 가져오기
            DocumentReference postRef = firestore.collection(POSTS_COLLECTION).document(postId);
            DocumentSnapshot postSnapshot = transaction.get(postRef);
            
            // 현재 댓글 수 가져오기
            long commentCount = postSnapshot.getLong("commentCount") != null ? 
                    postSnapshot.getLong("commentCount") : 0;
            
            // 댓글 수 1 감소 (최소 0)
            transaction.update(postRef, "commentCount", Math.max(0, commentCount - 1));
            
            // 댓글 삭제
            DocumentReference commentRef = firestore.collection(COMMENTS_COLLECTION).document(commentId);
            transaction.delete(commentRef);
            
            return null;
        });
    }
    
    // 포스트에 달린 댓글 목록 가져오기 (타임스탬프 기준 오름차순 정렬 - 답글 순서 보장)
    public Query getCommentsForPost(String postId) {
        return firestore.collection(COMMENTS_COLLECTION)
                .whereEqualTo("postId", postId)
                .orderBy("timestamp", Query.Direction.ASCENDING);
    }
    
    // 사용자가 작성한 댓글 목록 가져오기 (타임스탬프 기준 내림차순 정렬)
    public Query getCommentsByUser(String userId) {
        return firestore.collection(COMMENTS_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING);
    }
    
    // 특정 댓글 가져오기
    public Task<DocumentSnapshot> getCommentById(String commentId) {
        return firestore.collection(COMMENTS_COLLECTION).document(commentId).get();
    }
    
    // 댓글 수정하기 (필드명 content -> text 로 변경)
    public Task<Void> updateComment(String commentId, String newText) {
        DocumentReference commentRef = firestore.collection(COMMENTS_COLLECTION).document(commentId);
        return commentRef.update("text", newText);
    }
} 