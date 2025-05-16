package com.example.snapshot.repository;

import com.example.snapshot.model.Tag;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.WriteBatch;
import com.google.firebase.Timestamp;

import org.imperiumlabs.geofirestore.GeoFirestore;
import org.imperiumlabs.geofirestore.GeoQuery;
import org.imperiumlabs.geofirestore.listeners.GeoQueryEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TagRepository {
    private static final String TAGS_COLLECTION = "tags";
    private static final String LOCATION_TAGS_COLLECTION = "location_tags";
    private static final String SAVED_TAGS_COLLECTION = "saved_tags";
    private static final String TAG_USAGE_COLLECTION = "tag_usage";
    private static final String TAG = "TagRepository";
    
    private final FirebaseFirestore firestore;
    private final GeoFirestore geoFirestore;
    
    // 싱글톤 패턴
    private static TagRepository instance;
    
    public static TagRepository getInstance() {
        if (instance == null) {
            instance = new TagRepository();
        }
        return instance;
    }
    
    private TagRepository() {
        firestore = FirebaseFirestore.getInstance();
        // GeoFirestore 초기화 - 위치 태그 컬렉션 연결
        geoFirestore = new GeoFirestore(firestore.collection(LOCATION_TAGS_COLLECTION));
    }
    
    // 새로운 태그 생성
    public Task<Void> createTag(Tag tag) {
        DocumentReference tagRef = firestore.collection(TAGS_COLLECTION).document();
        String tagId = tagRef.getId();
        tag.setTagId(tagId);
        
        // 사용 횟수 초기화
        Map<String, Object> updates = new HashMap<>();
        updates.put("useCount", 0);
        
        WriteBatch batch = firestore.batch();
        
        // 태그 저장
        batch.set(tagRef, tag);
        
        // 사용 통계 문서 생성
        DocumentReference usageRef = firestore.collection(TAG_USAGE_COLLECTION).document(tagId);
        batch.set(usageRef, updates);
        
        // 위치 태그인 경우 GeoFirestore에도 저장
        if (Tag.TYPE_LOCATION.equals(tag.getTagType())) {
            Map<String, Object> tagData = tag.getTagData();
            if (tagData != null && tagData.containsKey("coordinates")) {
                GeoPoint geoPoint = (GeoPoint) tagData.get("coordinates");
                if (geoPoint != null) {
                    // 위치 태그 정보
                    Map<String, Object> locationTagData = new HashMap<>();
                    locationTagData.put("tagId", tagId);
                    locationTagData.put("name", tag.getName());
                    locationTagData.put("description", tag.getDescription());
                    
                    // GeoFirestore용 문서 생성
                    DocumentReference locationRef = firestore.collection(LOCATION_TAGS_COLLECTION).document(tagId);
                    batch.set(locationRef, locationTagData);
                    
                    // 배치 작업 완료 후 GeoFirestore에 위치 저장
                    return batch.commit().continueWithTask(task -> {
                        if (task.isSuccessful()) {
                            // GeoFirestore.setLocation 메서드는 void를 반환하므로 직접 호출 후 null 결과 반환
                            geoFirestore.setLocation(tagId, 
                                    new com.google.firebase.firestore.GeoPoint(
                                            geoPoint.getLatitude(), geoPoint.getLongitude()));
                            return Tasks.forResult(null);
                        } else {
                            return Tasks.forException(task.getException());
                        }
                    });
                }
            }
        }
        
        return batch.commit();
    }
    
    // 태그 사용 횟수 증가
    public Task<Void> incrementTagUseCount(String tagId) {
        if (tagId == null || tagId.isEmpty()) {
            // Log.e(TAG, "incrementTagUseCount: tagId가 null이거나 비어있습니다."); // 로그 추가 가능
            return Tasks.forException(new IllegalArgumentException("Tag ID는 null이거나 비어있을 수 없습니다."));
        }
        DocumentReference usageRef = firestore.collection(TAG_USAGE_COLLECTION).document(tagId);
        return usageRef.get().continueWithTask(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    // 문서가 존재하면 useCount 증가
                    // Log.d(TAG, "incrementTagUseCount: 기존 문서 업데이트 - " + tagId); // 로그 추가 가능
                    return usageRef.update("useCount", FieldValue.increment(1));
                } else {
                    // 문서가 존재하지 않으면 useCount: 1로 새로 생성
                    // Log.d(TAG, "incrementTagUseCount: 새 문서 생성 - " + tagId); // 로그 추가 가능
                    Map<String, Object> initialUsage = new HashMap<>();
                    initialUsage.put("useCount", 1L); // Firestore는 정수를 Long으로 처리
                    // 필요하다면 다른 초기 필드도 여기에 추가 가능 (예: tagName, 최초 사용일 등)
                    return usageRef.set(initialUsage);
                }
            } else {
                // .get() 실패
                // Log.e(TAG, "incrementTagUseCount: usageRef.get() 실패 - " + tagId, task.getException()); // 로그 추가 가능
                return Tasks.forException(task.getException());
            }
        });
    }
    
    // 태그 마지막 사용일 업데이트
    public Task<Void> updateTagLastUsed(String tagId) {
        DocumentReference tagRef = firestore.collection(TAGS_COLLECTION).document(tagId);
        return tagRef.update("lastUsed", Timestamp.now()); // com.google.firebase.Timestamp 사용
    }
    
    // 특정 태그 가져오기
    public Task<DocumentSnapshot> getTagById(String tagId) {
        return firestore.collection(TAGS_COLLECTION).document(tagId).get();
    }
    
    // 특정 유형의 태그 검색
    public Query getTagsByType(String tagType) {
        return firestore.collection(TAGS_COLLECTION)
                .whereEqualTo("tagType", tagType)
                .orderBy("name");
    }
    
    // 태그 이름으로 검색
    public Query searchTagsByName(String name) {
        String lowercaseName = name != null ? name.toLowerCase() : ""; // 검색어를 소문자로 변환
        return firestore.collection(TAGS_COLLECTION)
                .orderBy("name") // Firestore에 저장된 name 필드도 소문자여야 함
                .startAt(lowercaseName)
                .endAt(lowercaseName + "\uf8ff");
    }
    
    // 태그 타입과 이름으로 검색
    public Query searchTagsByTypeAndName(String tagType, String name) {
        String lowercaseName = name != null ? name.toLowerCase() : ""; // 검색어를 소문자로 변환
        return firestore.collection(TAGS_COLLECTION)
                .whereEqualTo("tagType", tagType)
                .orderBy("name")
                .startAt(lowercaseName) // 소문자로 변환된 이름 사용
                .endAt(lowercaseName + "\uf8ff"); // 소문자로 변환된 이름 사용
    }
    
    // 인기 태그 가져오기
    public Query getTrendingTags(int limit) {
        return firestore.collection(TAGS_COLLECTION)
                .orderBy("useCount", Query.Direction.DESCENDING)
                .limit(limit);
    }
    
    // 다중 태그 검색 - 여러 태그 유형과 조건으로 검색
    public Task<List<Tag>> searchByMultipleTags(Map<String, Object> conditions) {
        List<Task<QuerySnapshot>> queries = new ArrayList<>();
        
        // 태그 유형별 쿼리 생성
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            String tagType = entry.getKey();
            Object value = entry.getValue();
            
            Query query = firestore.collection(TAGS_COLLECTION)
                    .whereEqualTo("tagType", tagType);
            
            // 태그 유형별 추가 필터링
            if (tagType.equals(Tag.TYPE_LOCATION) && value instanceof String) {
                // 위치 검색 - 주소 텍스트 검색
                String locationName = (String) value;
                query = query.orderBy("name")
                      .startAt(locationName)
                      .endAt(locationName + "\uf8ff");
            } else if (tagType.equals(Tag.TYPE_PRICE) && value instanceof Number) {
                // 가격 범위 검색 - 지정된 가격 이하
                double priceValue = ((Number) value).doubleValue();
                query = query.whereLessThanOrEqualTo("tagData.amount", priceValue);
            } else if (tagType.equals(Tag.TYPE_PRICE) && value instanceof Map) {
                // 가격 범위 검색 - 최소값/최대값 지정
                Map<String, Object> priceRange = (Map<String, Object>) value;
                if (priceRange.containsKey("min") && priceRange.containsKey("max")) {
                    double minPrice = ((Number) priceRange.get("min")).doubleValue();
                    double maxPrice = ((Number) priceRange.get("max")).doubleValue();
                    query = query.whereGreaterThanOrEqualTo("tagData.amount", minPrice)
                          .whereLessThanOrEqualTo("tagData.amount", maxPrice);
                }
            } else if (tagType.equals(Tag.TYPE_EVENT) && value instanceof Map) {
                // 이벤트 기간 검색
                Map<String, Object> dateRange = (Map<String, Object>) value;
                if (dateRange.containsKey("startDate")) {
                    query = query.whereGreaterThanOrEqualTo("tagData.startDate", dateRange.get("startDate"));
                }
                if (dateRange.containsKey("endDate")) {
                    query = query.whereLessThanOrEqualTo("tagData.endDate", dateRange.get("endDate"));
                }
            } else if (tagType.equals(Tag.TYPE_BRAND) && value instanceof String) {
                // 브랜드 검색 - 이름 텍스트 검색
                String brandName = (String) value;
                query = query.orderBy("name")
                      .startAt(brandName)
                      .endAt(brandName + "\uf8ff");
            } else if (value instanceof String) {
                // 일반 문자열 검색 (태그 이름)
                String searchText = (String) value;
                query = query.orderBy("name")
                      .startAt(searchText)
                      .endAt(searchText + "\uf8ff");
            }
            
            queries.add(query.get());
        }
        
        // 모든 쿼리 결과 병합
        return Tasks.whenAllSuccess(queries).continueWith(task -> {
            List<Tag> results = new ArrayList<>();
            
            if (task.isSuccessful()) {
                List<Object> queryResults = task.getResult();
                
                for (Object result : queryResults) {
                    QuerySnapshot snapshot = (QuerySnapshot) result;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Tag tag = doc.toObject(Tag.class);
                        if (tag != null) {
                            results.add(tag);
                        }
                    }
                }
            }
            
            return results;
        });
    }
    
    // 위치 및 키워드 복합 검색
    public Task<List<Tag>> searchByLocationAndKeywords(double latitude, double longitude, 
                                                     double radiusInKm, List<String> keywords) {
        // 1. 위치 기반 검색
        GeoQuery geoQuery = searchTagsByLocation(latitude, longitude, radiusInKm);
        
        // 2. 키워드 검색을 위한 준비
        List<Task<QuerySnapshot>> keywordQueries = new ArrayList<>();
        for (String keyword : keywords) {
            Query query = searchTagsByName(keyword);
            keywordQueries.add(query.get());
        }
        
        // 3. 키워드 검색 결과 가져오기
        return Tasks.whenAllSuccess(keywordQueries).continueWith(task -> {
            List<Tag> results = new ArrayList<>();
            Map<String, Boolean> processedTagIds = new HashMap<>();
            
            if (task.isSuccessful()) {
                List<Object> queryResults = task.getResult();
                
                for (Object result : queryResults) {
                    QuerySnapshot snapshot = (QuerySnapshot) result;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Tag tag = doc.toObject(Tag.class);
                        if (tag != null && !processedTagIds.containsKey(tag.getTagId())) {
                            results.add(tag);
                            processedTagIds.put(tag.getTagId(), true);
                        }
                    }
                }
            }
            
            return results;
        });
    }
    
    // 위치 기반 태그 검색 - GeoFirestore 사용
    public GeoQuery searchTagsByLocation(double latitude, double longitude, double radiusInKm) {
        return geoFirestore.queryAtLocation(
                new com.google.firebase.firestore.GeoPoint(latitude, longitude), radiusInKm);
    }
    
    // 위치 기반 태그 검색 리스너 설정
    public void addGeoQueryEventListener(GeoQuery geoQuery, GeoQueryEventListener listener) {
        geoQuery.addGeoQueryEventListener(listener);
    }
    
    // 태그 업데이트
    public Task<Void> updateTag(Tag tag) {
        Task<Void> updateTask = firestore.collection(TAGS_COLLECTION).document(tag.getTagId()).set(tag);
        
        // 위치 태그인 경우 GeoFirestore에도 업데이트
        if (Tag.TYPE_LOCATION.equals(tag.getTagType())) {
            Map<String, Object> tagData = tag.getTagData();
            if (tagData != null && tagData.containsKey("coordinates")) {
                GeoPoint geoPoint = (GeoPoint) tagData.get("coordinates");
                if (geoPoint != null) {
                    geoFirestore.setLocation(tag.getTagId(), new com.google.firebase.firestore.GeoPoint(
                            geoPoint.getLatitude(), geoPoint.getLongitude()));
                }
            }
        }
        
        return updateTask;
    }
    
    // 태그 삭제
    public Task<Void> deleteTag(String tagId) {
        WriteBatch batch = firestore.batch();
        
        // 태그 문서 삭제
        batch.delete(firestore.collection(TAGS_COLLECTION).document(tagId));
        
        // 사용 통계 문서 삭제
        batch.delete(firestore.collection(TAG_USAGE_COLLECTION).document(tagId));
        
        // 배치 작업 커밋
        Task<Void> batchTask = batch.commit();
        
        // GeoFirestore에서 위치 정보 삭제
        geoFirestore.removeLocation(tagId);
        
        return batchTask;
    }
    
    // 여러 태그 ID로 태그 일괄 조회
    public Task<QuerySnapshot> getTagsByIds(List<String> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            android.util.Log.w(TAG, "getTagsByIds: 태그 ID 목록이 비어있습니다.");
            return Tasks.forException(new IllegalArgumentException("태그 ID 목록이 비어있습니다."));
        }
        
        // 태그 ID 목록을 로그에 출력
        StringBuilder tagIdsLog = new StringBuilder();
        for (int i = 0; i < Math.min(tagIds.size(), 10); i++) {
            tagIdsLog.append(tagIds.get(i)).append(", ");
        }
        if (tagIds.size() > 10) {
            tagIdsLog.append("... 외 ").append(tagIds.size() - 10).append("개");
        }
        
        android.util.Log.d(TAG, "getTagsByIds: " + tagIds.size() + "개의 태그 조회 시도 - 태그 ID: " + tagIdsLog.toString());
        
        return firestore.collection(TAGS_COLLECTION)
                .whereIn("tagId", tagIds)
                .get()
                .addOnSuccessListener(result -> {
                    if (result.isEmpty()) {
                        android.util.Log.w(TAG, "getTagsByIds: 조회된 태그가 없습니다. 요청 IDs: " + tagIdsLog.toString());
                    } else {
                        StringBuilder foundIds = new StringBuilder();
                        for (DocumentSnapshot doc : result.getDocuments()) {
                            Tag tag = doc.toObject(Tag.class);
                            if (tag != null) {
                                foundIds.append(tag.getTagId()).append(", ");
                            }
                        }
                        android.util.Log.d(TAG, "getTagsByIds 성공: " + result.size() + "개의 태그 조회됨 - 태그 ID: " + foundIds.toString());
                    }
                })
                .addOnFailureListener(e -> 
                    android.util.Log.e(TAG, "getTagsByIds 실패: " + e.getMessage() + " - 요청 IDs: " + tagIdsLog.toString()));
    }
    
    // 사용자가 태그 저장
    public Task<Void> saveTagForUser(String userId, String tagId) {
        android.util.Log.d(TAG, "saveTagForUser: 사용자 ID=" + userId + ", 태그 ID=" + tagId);
        
        String docId = userId + "_" + tagId;
        DocumentReference savedTagRef = firestore.collection(SAVED_TAGS_COLLECTION).document(docId);
        
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("tagId", tagId);
        data.put("savedAt", FieldValue.serverTimestamp());
        
        return savedTagRef.set(data)
                .addOnSuccessListener(aVoid -> android.util.Log.d(TAG, "태그 저장 성공: " + tagId))
                .addOnFailureListener(e -> android.util.Log.e(TAG, "태그 저장 실패: " + e.getMessage()));
    }
    
    // 사용자가 저장한 태그 목록 조회 (단일 필드만 사용하여 정렬)
    public Query getSavedTagsByUser(String userId) {
        android.util.Log.d(TAG, "getSavedTagsByUser: 사용자 ID=" + userId);
        return firestore.collection(SAVED_TAGS_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("savedAt", Query.Direction.DESCENDING);
    }
    
    // 사용자가 태그 저장 취소
    public Task<Void> unsaveTagForUser(String userId, String tagId) {
        String docId = userId + "_" + tagId;
        return firestore.collection(SAVED_TAGS_COLLECTION).document(docId).delete();
    }
    
    // 사용자가 특정 태그를 저장했는지 확인
    public Task<DocumentSnapshot> isTagSavedByUser(String userId, String tagId) {
        String docId = userId + "_" + tagId;
        return firestore.collection(SAVED_TAGS_COLLECTION).document(docId).get();
    }
} 