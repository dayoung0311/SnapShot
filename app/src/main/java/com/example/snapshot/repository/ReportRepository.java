package com.example.snapshot.repository;

import com.example.snapshot.model.Report;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

/**
 * 신고 기능 관련 데이터 처리를 담당하는 Repository 클래스
 */
public class ReportRepository {
    private static final String REPORTS_COLLECTION = "reports";
    private static final String REPORTED_POSTS_COLLECTION = "reported_posts";
    private static final String REPORTED_USERS_COLLECTION = "reported_users";
    private static final String TAG = "ReportRepository";
    
    private final FirebaseFirestore firestore;
    
    // 싱글톤 패턴
    private static ReportRepository instance;
    
    public static ReportRepository getInstance() {
        if (instance == null) {
            instance = new ReportRepository();
        }
        return instance;
    }
    
    private ReportRepository() {
        firestore = FirebaseFirestore.getInstance();
    }
    
    /**
     * 포스트 신고
     * @param reporterId 신고자 ID
     * @param postId 신고할 포스트 ID
     * @param reason 신고 이유
     * @return Task<Void>
     */
    public Task<Void> reportPost(String reporterId, String postId, String reason) {
        // 이미 신고했는지 확인
        return hasUserReportedPost(reporterId, postId)
                .continueWithTask(hasReportedTask -> {
                    if (hasReportedTask.isSuccessful() && Boolean.TRUE.equals(hasReportedTask.getResult())) {
                        // 이미 신고한 경우 에러 반환
                        return Tasks.forException(new Exception("이미 이 포스트를 신고하셨습니다."));
                    } else {
                        // 신고 생성
                        Report report = new Report(reporterId, postId, Report.TYPE_POST, reason);
                        DocumentReference reportRef = firestore.collection(REPORTS_COLLECTION).document();
                        report.setReportId(reportRef.getId());
                        
                        // 신고된 포스트 카운트 업데이트
                        DocumentReference reportedPostRef = firestore.collection(REPORTED_POSTS_COLLECTION).document(postId);
                        
                        // 트랜잭션으로 신고 생성 및 카운트 업데이트
                        return firestore.runTransaction((Transaction.Function<Void>) transaction -> {
                            // 신고 문서 생성
                            transaction.set(reportRef, report);
                            
                            // 신고된 포스트 정보 업데이트
                            transaction.set(reportedPostRef, new HashMap<String, Object>() {{
                                put("postId", postId);
                                put("reportCount", FieldValue.increment(1));
                                put("lastReportedAt", FieldValue.serverTimestamp());
                                put("reporters", FieldValue.arrayUnion(reporterId));
                            }}, SetOptions.merge());
                            
                            return null;
                        }).continueWithTask(transactionTask -> {
                            if (transactionTask.isSuccessful()) {
                                // 포스트 신고 수 확인하고 필요하면 숨김 처리
                                return checkPostReportThreshold(postId);
                            } else {
                                return Tasks.forException(transactionTask.getException());
                            }
                        });
                    }
                });
    }
    
    /**
     * 사용자 신고
     * @param reporterId 신고자 ID
     * @param userId 신고할 사용자 ID
     * @param reason 신고 이유
     * @return Task<Void>
     */
    public Task<Void> reportUser(String reporterId, String userId, String reason) {
        // 이미 신고했는지 확인
        return hasUserReportedUser(reporterId, userId)
                .continueWithTask(hasReportedTask -> {
                    if (hasReportedTask.isSuccessful() && Boolean.TRUE.equals(hasReportedTask.getResult())) {
                        // 이미 신고한 경우 에러 반환
                        return Tasks.forException(new Exception("이미 이 사용자를 신고하셨습니다."));
                    } else {
                        // 신고 생성
                        Report report = new Report(reporterId, userId, Report.TYPE_USER, reason);
                        DocumentReference reportRef = firestore.collection(REPORTS_COLLECTION).document();
                        report.setReportId(reportRef.getId());
                        
                        // 신고된 사용자 카운트 업데이트
                        DocumentReference reportedUserRef = firestore.collection(REPORTED_USERS_COLLECTION).document(userId);
                        
                        // 트랜잭션으로 신고 생성 및 카운트 업데이트
                        return firestore.runTransaction((Transaction.Function<Void>) transaction -> {
                            // 신고 문서 생성
                            transaction.set(reportRef, report);
                            
                            // 신고된 사용자 정보 업데이트
                            transaction.set(reportedUserRef, new HashMap<String, Object>() {{
                                put("userId", userId);
                                put("reportCount", FieldValue.increment(1));
                                put("lastReportedAt", FieldValue.serverTimestamp());
                                put("reporters", FieldValue.arrayUnion(reporterId));
                            }}, SetOptions.merge());
                            
                            return null;
                        }).continueWithTask(transactionTask -> {
                            if (transactionTask.isSuccessful()) {
                                // 사용자 신고 수 확인하고 필요하면 숨김 처리
                                return checkUserReportThreshold(userId);
                            } else {
                                return Tasks.forException(transactionTask.getException());
                            }
                        });
                    }
                });
    }
    
    /**
     * 사용자가 이미 해당 포스트를 신고했는지 확인
     * @param reporterId 신고자 ID
     * @param postId 포스트 ID
     * @return 신고 여부 (true/false)
     */
    public Task<Boolean> hasUserReportedPost(String reporterId, String postId) {
        return firestore.collection(REPORTS_COLLECTION)
                .whereEqualTo("reporterId", reporterId)
                .whereEqualTo("targetId", postId)
                .whereEqualTo("type", Report.TYPE_POST)
                .limit(1)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        return !task.getResult().isEmpty();
                    } else {
                        throw task.getException();
                    }
                });
    }
    
    /**
     * 사용자가 이미 해당 유저를 신고했는지 확인
     * @param reporterId 신고자 ID
     * @param userId 신고 대상 사용자 ID
     * @return 신고 여부 (true/false)
     */
    public Task<Boolean> hasUserReportedUser(String reporterId, String userId) {
        return firestore.collection(REPORTS_COLLECTION)
                .whereEqualTo("reporterId", reporterId)
                .whereEqualTo("targetId", userId)
                .whereEqualTo("type", Report.TYPE_USER)
                .limit(1)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful()) {
                        return !task.getResult().isEmpty();
                    } else {
                        throw task.getException();
                    }
                });
    }
    
    /**
     * 포스트 신고 수 임계값 확인 및 처리
     * 3회 이상 신고 시 포스트 숨김 처리
     * @param postId 포스트 ID
     * @return Task<Void>
     */
    private Task<Void> checkPostReportThreshold(String postId) {
        return firestore.collection(REPORTED_POSTS_COLLECTION)
                .document(postId)
                .get()
                .continueWithTask(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        Long reportCount = task.getResult().getLong("reportCount");
                        
                        // 신고 임계값 (3회) 이상인 경우
                        if (reportCount != null && reportCount >= 3) {
                            // Post 컬렉션에서 해당 포스트의 상태 업데이트
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("hidden", true);
                            updates.put("hiddenReason", "신고 누적으로 인한 자동 숨김 처리");
                            
                            return firestore.collection("posts")
                                    .document(postId)
                                    .update(updates);
                        }
                    }
                    return Tasks.forResult(null);
                });
    }
    
    /**
     * 사용자 신고 수 임계값 확인 및 처리
     * 5회 이상 신고 시 사용자 상태 변경
     * @param userId 사용자 ID
     * @return Task<Void>
     */
    private Task<Void> checkUserReportThreshold(String userId) {
        return firestore.collection(REPORTED_USERS_COLLECTION)
                .document(userId)
                .get()
                .continueWithTask(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        Long reportCount = task.getResult().getLong("reportCount");
                        
                        // 신고 임계값 (5회) 이상인 경우
                        if (reportCount != null && reportCount >= 5) {
                            // User 컬렉션에서 해당 사용자의 상태 업데이트
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("restricted", true);
                            updates.put("restrictedReason", "신고 누적으로 인한 제한 조치");
                            
                            return firestore.collection("users")
                                    .document(userId)
                                    .update(updates);
                        }
                    }
                    return Tasks.forResult(null);
                });
    }
    
    /**
     * 이 사용자가 신고로 인해 제한된 상태인지 확인
     * @param userId 사용자 ID
     * @return 제한 상태 (true/false)
     */
    public Task<Boolean> isUserRestricted(String userId) {
        return firestore.collection("users")
                .document(userId)
                .get()
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        Boolean restricted = task.getResult().getBoolean("restricted");
                        return Boolean.TRUE.equals(restricted);
                    }
                    return false;
                });
    }
} 