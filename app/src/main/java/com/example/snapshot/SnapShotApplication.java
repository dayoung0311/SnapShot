package com.example.snapshot;

import android.app.Application;
import android.os.Build;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;
import android.os.StrictMode.VmPolicy;
import android.util.Log;

import com.example.snapshot.utils.EnvConfig;
import com.google.firebase.FirebaseApp;

public class SnapShotApplication extends Application {
    
    private static final String TAG = "SnapShotApplication";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // StrictMode 설정
        setupStrictMode();
        
        // 환경 설정 초기화
        EnvConfig.init(this);
        
        // 초기화 확인 로그
        Log.d(TAG, "애플리케이션 초기화 완료");
        
        // API 키 로드 확인
        String geminiKey = EnvConfig.getGeminiApiKey();
        if (geminiKey != null && !geminiKey.isEmpty()) {
            Log.d(TAG, "Gemini API 키 로드 완료: " + geminiKey.substring(0, 5) + "...");
        } else {
            Log.w(TAG, "Gemini API 키가 설정되지 않았습니다");
        }
        
        // Firebase 초기화
        FirebaseApp.initializeApp(this);

        // FCM 토큰 등록
        registerFcmToken();
    }
    
    /**
     * Firebase Cloud Messaging 토큰 등록
     */
    private void registerFcmToken() {
        try {
            // 현재 사용자 확인
            com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
            if (auth.getCurrentUser() != null) {
                String userId = auth.getCurrentUser().getUid();
                
                // FCM 토큰 가져오기 및 등록
                com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                        .addOnCompleteListener(task -> {
                            if (!task.isSuccessful()) {
                                Log.w(TAG, "FCM 토큰 가져오기 실패", task.getException());
                                return;
                            }
                            
                            // 토큰 가져오기 성공
                            String token = task.getResult();
                            
                            // 토큰 저장
                            com.example.snapshot.repository.NotificationRepository.getInstance()
                                    .saveUserToken(userId, token)
                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "FCM 토큰 저장 완료"))
                                    .addOnFailureListener(e -> Log.e(TAG, "FCM 토큰 저장 실패", e));
                        });
            }
        } catch (Exception e) {
            Log.e(TAG, "FCM 토큰 등록 중 오류 발생", e);
        }
    }
    
    /**
     * StrictMode 설정 - 앱 성능 및 안정성 향상을 위한 설정
     */
    private void setupStrictMode() {
        // DEBUG 모드인지 체크 (직접 확인)
        boolean isDebug = false;
        
        try {
            // 패키지 정보에서 디버그 여부 확인
            isDebug = (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Exception e) {
            Log.e(TAG, "디버그 상태 확인 중 오류", e);
        }
        
        if (isDebug) {
            // 디버그 모드에서만 StrictMode 활성화
            
            // 스레드 정책 설정
            ThreadPolicy threadPolicy = new ThreadPolicy.Builder()
                    .detectAll()
                    .permitDiskReads()  // 디스크 읽기 허용
                    .permitDiskWrites() // 디스크 쓰기 허용
                    .permitNetwork()    // 네트워크 허용
                    .penaltyLog()       // 로그로 경고
                    .build();
            
            // VM 정책 설정
            VmPolicy vmPolicy = new VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build();
            
            // Android P 이상에서 Parcel 파싱 문제 해결
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                vmPolicy = new VmPolicy.Builder()
                        .detectLeakedSqlLiteObjects()
                        .detectLeakedClosableObjects()
                        .penaltyLog()
                        // .detectNonSdkApiUsage() // 비공개 API 사용 감지 비활성화 (AppCompat 호환성 이슈)
                        .build();
            }
            
            // 정책 적용
            StrictMode.setThreadPolicy(threadPolicy);
            StrictMode.setVmPolicy(vmPolicy);
            
            Log.d(TAG, "StrictMode 설정 완료");
        }
    }
} 