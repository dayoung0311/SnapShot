package com.example.snapshot.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.snapshot.MainActivity;
import com.example.snapshot.R;
import com.example.snapshot.repository.NotificationRepository;
import com.example.snapshot.ui.post.PostDetailActivity;
import com.example.snapshot.ui.profile.ProfileActivity;
import com.example.snapshot.ui.tag.TagDetailActivity;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class SnapShotFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "snapshot_notification_channel";
    private static final String CHANNEL_NAME = "SnapShot Notifications";
    
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        
        // 토큰을 서버에 등록
        NotificationRepository notificationRepository = NotificationRepository.getInstance();
        String userId = getCurrentUserId();
        
        if (userId != null) {
            notificationRepository.saveUserToken(userId, token)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM 토큰 저장 완료"))
                    .addOnFailureListener(e -> Log.e(TAG, "FCM 토큰 저장 실패", e));
        }
    }
    
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        
        // 알림 페이로드 확인
        if (remoteMessage.getData().size() > 0) {
            handleNotification(remoteMessage.getData());
        }
        
        // 알림 메시지 확인
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            
            String title = remoteMessage.getNotification().getTitle();
            String body = remoteMessage.getNotification().getBody();
            
            // 알림 표시
            showNotification(title, body, remoteMessage.getData());
        }
    }
    
    private void handleNotification(Map<String, String> data) {
        String title = data.get("title");
        String body = data.get("body");
        String notificationType = data.get("notificationType");
        String targetId = data.get("targetId");
        
        // 알림 표시
        showNotification(title, body, data);
    }
    
    private void showNotification(String title, String body, Map<String, String> data) {
        // 기본값 설정
        if (title == null) title = "SnapShot";
        if (body == null) body = "새로운 알림이 있습니다.";
        
        // 알림 클릭 시 실행할 액티비티 결정
        Intent intent;
        String notificationType = data.get("notificationType");
        String targetId = data.get("targetId");
        
        if (notificationType != null && targetId != null) {
            switch (notificationType) {
                case "like":
                case "comment":
                    // 포스트 상세 화면으로 이동
                    intent = new Intent(this, PostDetailActivity.class);
                    intent.putExtra("postId", targetId);
                    break;
                case "follow":
                    // 프로필 화면으로 이동
                    intent = new Intent(this, ProfileActivity.class);
                    intent.putExtra("userId", targetId);
                    break;
                case "tag":
                    // 태그 상세 화면으로 이동
                    intent = new Intent(this, TagDetailActivity.class);
                    intent.putExtra("tagId", targetId);
                    break;
                default:
                    // 기본 메인 화면으로 이동
                    intent = new Intent(this, MainActivity.class);
                    break;
            }
        } else {
            // 기본 메인 화면으로 이동
            intent = new Intent(this, MainActivity.class);
        }
        
        // 기존 액티비티 스택 위에 새 액티비티 실행
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        // PendingIntent 생성
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        
        // 알림음 설정
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        
        // 알림 빌더 설정
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_notifications)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setAutoCancel(true)
                        .setSound(defaultSoundUri)
                        .setContentIntent(pendingIntent);
        
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Android Oreo 이상에서는 채널 생성 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT);
            
            notificationManager.createNotificationChannel(channel);
        }
        
        // 알림 표시
        notificationManager.notify(0, notificationBuilder.build());
    }
    
    // 현재 사용자 ID 가져오기
    private String getCurrentUserId() {
        // FirebaseAuth에서 현재 사용자 정보 가져오기
        if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
            return com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        return null;
    }
} 