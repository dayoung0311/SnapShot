package com.example.snapshot.utils;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 환경 설정 파일에서 API 키 등의 민감한 정보를 로드하는 유틸리티 클래스
 */
public class EnvConfig {
    private static final String TAG = "EnvConfig";
    private static final String ENV_FILE = "env.properties";
    
    private static Properties properties;
    
    /**
     * 환경 설정 파일을 로드합니다.
     * 앱 초기화 시 한 번만 호출해야 합니다.
     * @param context 애플리케이션 컨텍스트
     */
    public static void init(Context context) {
        properties = new Properties();
        try {
            InputStream inputStream = context.getAssets().open(ENV_FILE);
            properties.load(inputStream);
            Log.d(TAG, "환경 설정 파일 로드 완료");
        } catch (IOException e) {
            Log.e(TAG, "환경 설정 파일 로드 실패: " + e.getMessage());
        }
    }
    
    /**
     * 저장된 속성 값을 가져옵니다.
     * @param key 속성 키
     * @return 속성 값 또는 키가 없는 경우 null
     */
    public static String get(String key) {
        if (properties == null) {
            Log.e(TAG, "환경 설정이 초기화되지 않았습니다. init() 메서드를 먼저 호출하세요.");
            return null;
        }
        return properties.getProperty(key);
    }
    
    /**
     * Firebase API 키를 가져옵니다.
     * @return Firebase API 키
     */
    public static String getFirebaseApiKey() {
        return get("FIREBASE_API_KEY");
    }
    
    /**
     * Gemini API 키를 가져옵니다.
     * @return Gemini API 키
     */
    public static String getGeminiApiKey() {
        return get("GEMINI_API_KEY");
    }
    
    /**
     * Google Maps API 키를 가져옵니다.
     * @return Google Maps API 키
     */
    public static String getMapsApiKey() {
        return get("MAPS_API_KEY");
    }
} 