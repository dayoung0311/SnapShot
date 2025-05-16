package com.example.snapshot.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.example.snapshot.utils.EnvConfig;
import com.example.snapshot.model.Tag;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.ai.client.generativeai.type.GenerationConfig;
import com.google.ai.client.generativeai.type.RequestOptions;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TagSuggestionService {
    private static final String TAG = "TagSuggestionService";
    
    private final GenerativeModelFutures modelFutures;
    private final Executor executor;
    
    // Gemini API 요청 타임아웃 (초)
    private static final long API_TIMEOUT_SECONDS = 10;
    
    // 싱글톤 패턴
    private static TagSuggestionService instance;
    
    public static TagSuggestionService getInstance(Context context) {
        if (instance == null) {
            instance = new TagSuggestionService(context);
        }
        return instance;
    }
    
    // 인스턴스 해제
    public static void releaseInstance() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }
    
    // 리소스 해제
    public void shutdown() {
        if (executor instanceof java.util.concurrent.ExecutorService) {
            try {
                java.util.concurrent.ExecutorService executorService = 
                        (java.util.concurrent.ExecutorService) executor;
                executorService.shutdown();
                if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (Exception e) {
                Log.e(TAG, "Executor 종료 중 오류 발생", e);
            }
        }
    }
    
    private TagSuggestionService(Context context) {
        // Gemini API 키 가져오기
        String apiKey = EnvConfig.getGeminiApiKey();
        
        // API 키가 없거나 테스트 키인 경우 로그 출력
        if (apiKey == null || apiKey.isEmpty()) {
            Log.w(TAG, "유효한 Gemini API 키가 설정되지 않았습니다. 더미 데이터를 사용합니다.");
        }
        
        // Gemini 모델 생성 - 단순화된 설정 사용
        GenerativeModel model = new GenerativeModel("gemini-1.5-flash", apiKey);
        
        // Java 버전용 Gemini 모델 생성
        modelFutures = GenerativeModelFutures.from(model);
        
        executor = Executors.newSingleThreadExecutor();
    }
    
    /**
     * 이미지에서 태그 추천을 받습니다.
     * @param image 이미지 비트맵
     * @param callback 결과를 받을 콜백
     */
    public void suggestTags(Bitmap image, TagSuggestionCallback callback) {
        try {
            // 무료 할당량 초과 등의 이유로 API 호출이 실패할 경우를 대비한 임시 더미 태그
            List<Tag> fallbackTags = generateFallbackTags();
            
            // API 키가 설정되지 않은 경우 바로 더미 데이터 반환
            String apiKey = EnvConfig.getGeminiApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                Log.w(TAG, "유효한 API 키가 없어 더미 태그를 사용합니다.");
                callback.onTagsGenerated(fallbackTags);
                return;
            }
            
            // 이미지에서 태그를 추출하는 프롬프트 - Gemini 1.5 모델용으로 최적화
            String prompt = "이 이미지를 분석해서 SNS 포스팅을 위한 태그 정보를 JSON으로 추출해주세요.\n\n" +
                    "다음 카테고리별로 태그를 자세히 추출해주세요:\n" +
                    "1. locations: 이미지에 보이는 장소나 위치 (예: 스타벅스 강남점, 한강공원, 롯데월드, 강남역 등)\n" +
                    "2. products: 이미지에 보이는 제품이나 물건 (예: 아메리카노, 맥북 프로, 아이폰, 나이키 운동화 등)\n" +
                    "3. brands: 이미지에 보이는 브랜드 (예: 애플, 스타벅스, 나이키, 삼성 등)\n" +
                    "4. prices: 이미지와 관련된 가격대 (예: 5만원대, 10만원 미만, 저가, 고가 등)\n" +
                    "5. events: 이미지에 관련된 이벤트나 활동 (예: 생일파티, 전시회, 여행, 데이트 등)\n\n" +
                    "각 카테고리별로 최대 3개까지의 태그를 추출하고, 각 태그에는 명확한 이름과 상세한 설명을 포함해주세요.\n" + 
                    "확실하지 않은 정보는 포함하지 말고, 이미지에서 명확하게 보이는 정보만 추출해주세요.\n\n" +
                    "정확히 다음 JSON 형식으로만 응답해주세요:\n" +
                    "{\n" +
                    "  \"locations\": [{\"name\": \"장소명\", \"description\": \"장소에 대한 상세 설명\"}],\n" +
                    "  \"products\": [{\"name\": \"제품명\", \"description\": \"제품에 대한 상세 설명\"}],\n" +
                    "  \"brands\": [{\"name\": \"브랜드명\", \"description\": \"브랜드에 대한 설명\"}],\n" +
                    "  \"prices\": [{\"name\": \"가격대\"}],\n" +
                    "  \"events\": [{\"name\": \"이벤트명\", \"description\": \"이벤트에 대한 설명\"}]\n" +
                    "}\n\n" +
                    "이미지에 해당 정보가 없다면 빈 배열로 표시하세요. 반드시 위 JSON 형식만 응답하고 다른 설명은 추가하지 마세요.";
            
            // Gemini API 호출 - 비동기로 처리
            executor.execute(() -> {
                try {
                    // 이미지와 프롬프트로 Content 생성
                    Content content = new Content.Builder()
                            .addText(prompt)
                            .addImage(image)
                            .build();
                    
                    // API 호출 및 콜백 설정 (Java 버전용 API 사용)
                    ListenableFuture<GenerateContentResponse> responseFuture = 
                            modelFutures.generateContent(content);
                    
                    // 타임아웃 설정
                    try {
                        // 타임아웃 내에 응답을 받지 못하면 fallback 사용
                        GenerateContentResponse response = responseFuture.get(API_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        processApiResponse(response, callback, fallbackTags);
                    } catch (Exception e) {
                        Log.e(TAG, "Gemini API 호출 타임아웃 또는 오류 발생", e);
                        callback.onTagsGenerated(fallbackTags);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Gemini API 호출 중 오류 발생", e);
                    callback.onTagsGenerated(fallbackTags);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "태그 생성 준비 중 오류", e);
            callback.onError("태그 생성 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    // API 응답 처리
    private void processApiResponse(GenerateContentResponse response, 
                                   TagSuggestionCallback callback,
                                   List<Tag> fallbackTags) {
        try {
            // 응답에서 텍스트 추출
            String responseText = response.getText();
            if (responseText == null || responseText.isEmpty()) {
                Log.e(TAG, "Gemini API로부터 빈 응답 받음");
                callback.onTagsGenerated(fallbackTags);
                return;
            }
            
            // JSON 응답 추출
            String jsonStr = extractJsonFromResponse(responseText);
            if (jsonStr.equals("{}")) {
                Log.e(TAG, "응답에서 유효한 JSON을 찾을 수 없음: " + responseText);
                callback.onTagsGenerated(fallbackTags);
                return;
            }
            
            // JSON을 태그 리스트로 파싱
            List<Tag> tags = parseJsonToTags(jsonStr);
            
            // 태그가 없으면 fallback 사용
            if (tags.isEmpty()) {
                Log.w(TAG, "응답에서 태그를 추출할 수 없음");
                callback.onTagsGenerated(fallbackTags);
            } else {
                callback.onTagsGenerated(tags);
            }
        } catch (Exception e) {
            Log.e(TAG, "응답 파싱 오류", e);
            callback.onTagsGenerated(fallbackTags);
        }
    }
    
    // 더미 태그 생성
    private List<Tag> generateFallbackTags() {
        List<Tag> fallbackTags = new ArrayList<>();
        
        // 위치 태그
        String tagId = UUID.randomUUID().toString();
        fallbackTags.add(Tag.createLocationTag(tagId, "카페", "서울시 강남구 역삼동", 37.501, 127.036));
        
        tagId = UUID.randomUUID().toString();
        fallbackTags.add(Tag.createLocationTag(tagId, "한강공원", "서울시 서초구 반포동", 37.513, 126.997));
        
        // 제품 태그
        tagId = UUID.randomUUID().toString();
        fallbackTags.add(Tag.createProductTag(tagId, "아메리카노", "에스프레소에 뜨거운 물을 넣은 커피", 
                "https://coffee.example.com", 4500, "스타벅스"));
        
        tagId = UUID.randomUUID().toString();
        fallbackTags.add(Tag.createProductTag(tagId, "맥북 프로", "애플의 프로페셔널 노트북", 
                "https://apple.com/macbook-pro", 2500000, "Apple"));
        
        // 브랜드 태그
        tagId = UUID.randomUUID().toString();
        Tag brandTag = Tag.createBrandTag(tagId, "Apple", "글로벌 테크 기업", 
                "https://apple.com", "https://apple.com/logo.png");
        fallbackTags.add(brandTag);
        
        // 가격 태그
        tagId = UUID.randomUUID().toString();
        fallbackTags.add(Tag.createPriceTag(tagId, "5만원대", 50000, "KRW"));
        
        // 이벤트 태그
        tagId = UUID.randomUUID().toString();
        fallbackTags.add(Tag.createEventTag(tagId, "신제품 출시", "새로운 제품 출시 이벤트", 
                "2024-07-01", "2024-07-31", "https://example.com/event"));
        
        return fallbackTags;
    }
    
    // 응답 텍스트에서 JSON 부분만 추출
    private String extractJsonFromResponse(String response) {
        try {
            int startIndex = response.indexOf("{");
            int endIndex = response.lastIndexOf("}") + 1;
            
            if (startIndex >= 0 && endIndex > startIndex) {
                return response.substring(startIndex, endIndex);
            }
        } catch (Exception e) {
            Log.e(TAG, "JSON 추출 중 오류 발생", e);
        }
        
        // JSON을 찾을 수 없으면 빈 객체 반환
        return "{}";
    }
    
    // JSON 응답을 태그 리스트로 파싱
    private List<Tag> parseJsonToTags(String jsonResponse) {
        List<Tag> tags = new ArrayList<>();
        
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            
            // 1. 위치 태그 파싱
            if (jsonObject.has("locations")) {
                JSONArray locationsArray = jsonObject.getJSONArray("locations");
                for (int i = 0; i < locationsArray.length(); i++) {
                    JSONObject locationObj = locationsArray.getJSONObject(i);
                    String name = locationObj.getString("name");
                    String description = locationObj.optString("description", name);
                    
                    // 위치 태그는 좌표가 없는 상태로 생성 (위도/경도는 나중에 설정 가능)
                    // 기본 좌표는 서울 중심으로 설정
                    String tagId = UUID.randomUUID().toString();
                    Tag locationTag = Tag.createLocationTag(tagId, name, description, 37.5665, 126.9780);
                    tags.add(locationTag);
                }
            }
            
            // 2. 제품 태그 파싱
            if (jsonObject.has("products")) {
                JSONArray productsArray = jsonObject.getJSONArray("products");
                for (int i = 0; i < productsArray.length(); i++) {
                    JSONObject productObj = productsArray.getJSONObject(i);
                    String name = productObj.getString("name");
                    String description = productObj.optString("description", name);
                    
                    // 가격 정보가 없는 제품 태그는 가격을 0으로 설정
                    String tagId = UUID.randomUUID().toString();
                    Tag productTag = Tag.createProductTag(tagId, name, description, "", 0, "");
                    tags.add(productTag);
                }
            }
            
            // 3. 브랜드 태그 파싱
            if (jsonObject.has("brands")) {
                JSONArray brandsArray = jsonObject.getJSONArray("brands");
                for (int i = 0; i < brandsArray.length(); i++) {
                    JSONObject brandObj = brandsArray.getJSONObject(i);
                    String name = brandObj.getString("name");
                    String description = brandObj.optString("description", name);
                    
                    String tagId = UUID.randomUUID().toString();
                    Tag brandTag = Tag.createBrandTag(tagId, name, description, "", "");
                    tags.add(brandTag);
                }
            }
            
            // 4. 가격 태그 파싱
            if (jsonObject.has("prices")) {
                JSONArray pricesArray = jsonObject.getJSONArray("prices");
                for (int i = 0; i < pricesArray.length(); i++) {
                    JSONObject priceObj = pricesArray.getJSONObject(i);
                    String name = priceObj.getString("name");
                    
                    // 가격 태그는 임시로 현재 이름 그대로, 금액 추출을 시도해 볼 수 있음
                    double amount = 0;
                    String currency = "KRW";
                    
                    // 가격 문자열에서 숫자만 추출 시도
                        try {
                        String numberOnly = name.replaceAll("[^0-9]", "");
                        if (!numberOnly.isEmpty()) {
                            amount = Double.parseDouble(numberOnly);
                            }
                    } catch (Exception e) {
                        // 숫자 추출 실패 시 0으로 유지
                        Log.w(TAG, "가격 태그에서 숫자 추출 실패: " + name);
                    }
                    
                    String tagId = UUID.randomUUID().toString();
                    Tag priceTag = Tag.createPriceTag(tagId, name, amount, currency);
                    tags.add(priceTag);
                }
            }
            
            // 5. 이벤트 태그 파싱
            if (jsonObject.has("events")) {
                JSONArray eventsArray = jsonObject.getJSONArray("events");
                for (int i = 0; i < eventsArray.length(); i++) {
                    JSONObject eventObj = eventsArray.getJSONObject(i);
                    String name = eventObj.getString("name");
                    String description = eventObj.optString("description", name);
                    
                    // 이벤트 태그 (날짜 없음)
                    String tagId = UUID.randomUUID().toString();
                    Tag eventTag = Tag.createEventTag(tagId, name, description, "", "", "");
                    tags.add(eventTag);
                }
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "JSON 파싱 오류", e);
        }
        
        return tags;
    }
    
    public interface TagSuggestionCallback {
        void onTagsGenerated(List<Tag> tags);
        void onError(String errorMessage);
    }
} 