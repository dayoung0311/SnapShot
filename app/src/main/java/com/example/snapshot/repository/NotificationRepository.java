package com.example.snapshot.repository;

import com.example.snapshot.model.Notification;
import com.example.snapshot.model.User;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class NotificationRepository {
    private static final String NOTIFICATIONS_COLLECTION = "notifications";
    private static final String TAG_SUBSCRIPTIONS_COLLECTION = "tag_subscriptions";
    private static final String USER_TOKENS_COLLECTION = "user_tokens";
    private static final String TAG = "NotificationRepository";
    
    private final FirebaseFirestore firestore;
    private final FirebaseMessaging firebaseMessaging;
    private final Executor executor;
    
    // 싱글톤 패턴
    private static NotificationRepository instance;
    
    public static NotificationRepository getInstance() {
        if (instance == null) {
            instance = new NotificationRepository();
        }
        return instance;
    }
    
    private NotificationRepository() {
        firestore = FirebaseFirestore.getInstance();
        firebaseMessaging = FirebaseMessaging.getInstance();
        executor = Executors.newSingleThreadExecutor();
    }
    
    // 알림 저장
    public Task<Void> saveNotification(Notification notification) {
        DocumentReference notificationRef = firestore.collection(NOTIFICATIONS_COLLECTION).document();
        notification.setNotificationId(notificationRef.getId());
        
        Log.d(TAG, "Attempting to save notification: ID=" + notification.getNotificationId() + ", Type=" + notification.getNotificationType() + ", UserID=" + notification.getUserId());
        
        return notificationRef.set(notification)
                .addOnSuccessListener(aVoid -> {
                    Log.i(TAG, "Notification saved successfully: ID=" + notification.getNotificationId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save notification: ID=" + notification.getNotificationId(), e);
                });
    }
    
    // 사용자가 받은 알림 조회
    public Query getNotificationsForUser(String userId) {
        return firestore.collection(NOTIFICATIONS_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("creationDate", Query.Direction.DESCENDING);
    }
    
    // 알림을 읽음으로 표시
    public Task<Void> markNotificationAsRead(String notificationId) {
        DocumentReference notificationRef = 
                firestore.collection(NOTIFICATIONS_COLLECTION).document(notificationId);
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("isRead", true);
        
        return notificationRef.update(updates);
    }
    
    // 사용자의 모든 알림을 읽음으로 표시
    public Task<Void> markAllNotificationsAsRead(String userId) {
        // 먼저 사용자의 읽지 않은 알림을 쿼리
        return firestore.collection(NOTIFICATIONS_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereEqualTo("isRead", false)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult().isEmpty()) {
                        return Tasks.forResult(null);
                    }
                    
                    QuerySnapshot querySnapshot = task.getResult();
                    List<DocumentSnapshot> documents = querySnapshot.getDocuments();
                    
                    // 최대 500개의 문서를 한번에 업데이트 (Firestore 제한)
                    WriteBatch batch = firestore.batch();
                    
                    for (DocumentSnapshot document : documents) {
                        DocumentReference docRef = document.getReference();
                        batch.update(docRef, "isRead", true);
                    }
                    
                    return batch.commit();
                });
    }
    
    // 알림 삭제
    public Task<Void> deleteNotification(String notificationId) {
        return firestore.collection(NOTIFICATIONS_COLLECTION)
                .document(notificationId)
                .delete();
    }
    
    // 사용자의 모든 알림 삭제
    public Task<Void> deleteAllNotificationsForUser(String userId) {
        return firestore.collection(NOTIFICATIONS_COLLECTION)
                .whereEqualTo("userId", userId)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult().isEmpty()) {
                        return Tasks.forResult(null);
                    }
                    
                    QuerySnapshot querySnapshot = task.getResult();
                    List<DocumentSnapshot> documents = querySnapshot.getDocuments();
                    
                    WriteBatch batch = firestore.batch();
                    
                    for (DocumentSnapshot document : documents) {
                        batch.delete(document.getReference());
                    }
                    
                    return batch.commit();
                });
    }
    
    // 태그 구독
    public Task<Void> subscribeToTag(String userId, String tagId, String tagName) {
        String subscriptionId = userId + "_" + tagId;
        DocumentReference subscriptionRef = 
                firestore.collection(TAG_SUBSCRIPTIONS_COLLECTION).document(subscriptionId);
        
        Map<String, Object> subscriptionData = new HashMap<>();
        subscriptionData.put("userId", userId);
        subscriptionData.put("tagId", tagId);
        subscriptionData.put("tagName", tagName);
        subscriptionData.put("subscribedAt", FieldValue.serverTimestamp());
        
        // FCM 토픽 구독 (태그 ID를 토픽으로 사용)
        firebaseMessaging.subscribeToTopic("tag_" + tagId);
        
        return subscriptionRef.set(subscriptionData);
    }
    
    // 태그 구독 취소
    public Task<Void> unsubscribeFromTag(String userId, String tagId) {
        String subscriptionId = userId + "_" + tagId;
        
        // FCM 토픽 구독 취소
        firebaseMessaging.unsubscribeFromTopic("tag_" + tagId);
        
        return firestore.collection(TAG_SUBSCRIPTIONS_COLLECTION)
                .document(subscriptionId)
                .delete();
    }
    
    // 사용자가 구독한 태그 목록 조회
    public Query getSubscribedTagsForUser(String userId) {
        return firestore.collection(TAG_SUBSCRIPTIONS_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("subscribedAt", Query.Direction.DESCENDING);
    }
    
    // 사용자가 특정 태그를 구독했는지 확인
    public Task<DocumentSnapshot> isTagSubscribedByUser(String userId, String tagId) {
        String subscriptionId = userId + "_" + tagId;
        return firestore.collection(TAG_SUBSCRIPTIONS_COLLECTION)
                .document(subscriptionId)
                .get();
    }
    
    // 사용자 FCM 토큰 저장
    public Task<Void> saveUserToken(String userId, String token) {
        DocumentReference tokenRef = firestore.collection(USER_TOKENS_COLLECTION).document(userId);
        
        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("token", token);
        tokenData.put("updatedAt", FieldValue.serverTimestamp());
        
        return tokenRef.set(tokenData);
    }
    
    // 다른 사용자에게 알림 전송
    public void sendNotificationToUser(String targetUserId, Notification notification) {
        // 알림 저장
        saveNotification(notification);
        
        // FCM을 통한 푸시 알림 전송
        executor.execute(() -> {
            try {
                // 사용자 토큰 조회
                firestore.collection(USER_TOKENS_COLLECTION)
                        .document(targetUserId)
                        .get()
                        .addOnSuccessListener(tokenDoc -> {
                            if (tokenDoc != null && tokenDoc.exists()) {
                                String token = tokenDoc.getString("token");
                                
                                if (token != null && !token.isEmpty()) {
                                    // 메시지 구성
                                    Map<String, String> data = new HashMap<>();
                                    data.put("title", "SnapShot");
                                    data.put("body", notification.getContent());
                                    data.put("notificationType", notification.getNotificationType());
                                    data.put("targetId", notification.getTargetId());
                                    
                                    // FCM으로 메시지 전송
                                    // Firebase 서버에서 실제 전송을 처리하므로 이 부분은 Firebase Cloud Functions에서 처리해야 함
                                    // 여기서는 알림을 데이터베이스에 저장하는 것으로 대체
                                }
                            }
                        })
                        .addOnFailureListener(e -> {
                            // 토큰 조회 실패해도 정상적으로 처리
                            e.printStackTrace();
                        });
            } catch (Exception e) {
                // 오류 처리
                e.printStackTrace();
            }
        });
    }
    
    // 태그를 구독한 모든 사용자에게 알림 전송
    public void sendNotificationToTagSubscribers(String tagId, String tagName, 
                                               String senderId, String senderName, 
                                               String senderProfilePic) {
        // 태그 구독자 조회
        firestore.collection(TAG_SUBSCRIPTIONS_COLLECTION)
                .whereEqualTo("tagId", tagId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> subscriberIds = new ArrayList<>();
                    
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String userId = doc.getString("userId");
                        
                        // 자신을 제외한 구독자만 알림 전송
                        if (userId != null && !userId.equals(senderId)) {
                            subscriberIds.add(userId);
                        }
                    }
                    
                    // 각 구독자에게 알림 전송
                    for (String userId : subscriberIds) {
                        Notification notification = Notification.createTagNotification(
                                userId, senderId, senderName, senderProfilePic, tagId, tagName);
                        
                        sendNotificationToUser(userId, notification);
                    }
                });
    }
} 