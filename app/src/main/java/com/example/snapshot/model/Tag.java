package com.example.snapshot.model;

import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.Timestamp;
import java.util.HashMap;
import java.util.Map;

public class Tag {
    public static final String TYPE_LOCATION = "location";
    public static final String TYPE_PRODUCT = "product";
    public static final String TYPE_BRAND = "brand";
    public static final String TYPE_PRICE = "price";
    public static final String TYPE_EVENT = "event";
    
    private String tagId;
    private String tagType; // location, product, brand, price, event
    private String name;
    private String description;
    private Map<String, Object> tagData; // 태그 유형에 따른 추가 데이터
    
    // 추가된 필드
    private String creatorId;
    private long useCount;
    private Timestamp lastUsed;
    private long relatedPostsCount;
    private double trendingScore;
    private Double latitude;
    private Double longitude;
    private String address;
    
    // 빈 생성자 - Firestore에 필요
    public Tag() {
        tagData = new HashMap<>();
    }
    
    public Tag(String tagId, String tagType, String name, String description) {
        this.tagId = tagId;
        this.tagType = tagType;
        this.name = name;
        this.description = description;
        this.tagData = new HashMap<>();
    }
    
    // 위치 태그 생성 팩토리 메서드
    public static Tag createLocationTag(String tagId, String name, String address, 
                                        double latitude, double longitude) {
        Tag tag = new Tag(tagId, TYPE_LOCATION, name, address);
        tag.getTagData().put("address", address);
        tag.getTagData().put("coordinates", new GeoPoint(latitude, longitude));
        return tag;
    }
    
    // 제품 태그 생성 팩토리 메서드
    public static Tag createProductTag(String tagId, String name, String description, 
                                      String productUrl, double price, String brand) {
        Tag tag = new Tag(tagId, TYPE_PRODUCT, name, description);
        tag.getTagData().put("productUrl", productUrl);
        tag.getTagData().put("price", price);
        tag.getTagData().put("brand", brand);
        return tag;
    }
    
    // 브랜드 태그 생성 팩토리 메서드
    public static Tag createBrandTag(String tagId, String name, String description, 
                                    String brandUrl, String logoUrl) {
        Tag tag = new Tag(tagId, TYPE_BRAND, name, description);
        tag.getTagData().put("brandUrl", brandUrl);
        tag.getTagData().put("logoUrl", logoUrl);
        return tag;
    }
    
    // 가격 태그 생성 팩토리 메서드
    public static Tag createPriceTag(String tagId, String name, double amount, String currency) {
        Tag tag = new Tag(tagId, TYPE_PRICE, name, amount + " " + currency);
        tag.getTagData().put("amount", amount);
        tag.getTagData().put("currency", currency);
        return tag;
    }
    
    // 이벤트 태그 생성 팩토리 메서드
    public static Tag createEventTag(String tagId, String name, String description, 
                                    String startDate, String endDate, String website) {
        Tag tag = new Tag(tagId, TYPE_EVENT, name, description);
        tag.getTagData().put("startDate", startDate);
        tag.getTagData().put("endDate", endDate);
        tag.getTagData().put("website", website);
        return tag;
    }
    
    // Getter 및 Setter
    public String getTagId() {
        return tagId;
    }
    
    public void setTagId(String tagId) {
        this.tagId = tagId;
    }
    
    public String getTagType() {
        return tagType;
    }
    
    public void setTagType(String tagType) {
        this.tagType = tagType;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        if (name != null) {
            this.name = name.toLowerCase(); // 소문자로 변환하여 저장
        } else {
            this.name = null;
        }
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Map<String, Object> getTagData() {
        return tagData;
    }
    
    public void setTagData(Map<String, Object> tagData) {
        this.tagData = tagData;
    }
    
    // 추가된 필드들의 Getter 및 Setter
    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    public long getUseCount() {
        return useCount;
    }

    public void setUseCount(long useCount) {
        this.useCount = useCount;
    }

    public Timestamp getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(Timestamp lastUsed) {
        this.lastUsed = lastUsed;
    }

    public long getRelatedPostsCount() {
        return relatedPostsCount;
    }

    public void setRelatedPostsCount(long relatedPostsCount) {
        this.relatedPostsCount = relatedPostsCount;
    }

    public double getTrendingScore() {
        return trendingScore;
    }

    public void setTrendingScore(double trendingScore) {
        this.trendingScore = trendingScore;
    }

    public Double getLatitude() {
        if (tagData != null && TYPE_LOCATION.equals(tagType) && tagData.containsKey("coordinates")) {
            GeoPoint geoPoint = (GeoPoint) tagData.get("coordinates");
            if (geoPoint != null) return geoPoint.getLatitude();
        }
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
        if (tagData != null && TYPE_LOCATION.equals(tagType)) {
            GeoPoint oldPoint = tagData.containsKey("coordinates") ? (GeoPoint) tagData.get("coordinates") : new GeoPoint(0,0);
            tagData.put("coordinates", new GeoPoint(latitude != null ? latitude : 0, oldPoint != null ? oldPoint.getLongitude() : 0));
        }
    }

    public Double getLongitude() {
        if (tagData != null && TYPE_LOCATION.equals(tagType) && tagData.containsKey("coordinates")) {
            GeoPoint geoPoint = (GeoPoint) tagData.get("coordinates");
            if (geoPoint != null) return geoPoint.getLongitude();
        }
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
        if (tagData != null && TYPE_LOCATION.equals(tagType)) {
            GeoPoint oldPoint = tagData.containsKey("coordinates") ? (GeoPoint) tagData.get("coordinates") : new GeoPoint(0,0);
            tagData.put("coordinates", new GeoPoint(oldPoint != null ? oldPoint.getLatitude() : 0, longitude != null ? longitude : 0));
        }
    }

    public String getAddress() {
        if (tagData != null && TYPE_LOCATION.equals(tagType) && tagData.containsKey("address")) {
            return (String) tagData.get("address");
        }
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
        if (tagData != null && TYPE_LOCATION.equals(tagType)) {
            tagData.put("address", address);
        }
    }
    
    // Firestore 저장을 위한 toMap() 메소드
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("tagId", tagId);
        map.put("name", name);
        map.put("description", description);
        map.put("tagType", tagType);
        map.put("creatorId", creatorId);
        map.put("useCount", useCount);
        map.put("lastUsed", lastUsed); // Timestamp는 Firestore에서 직접 지원
        map.put("relatedPostsCount", relatedPostsCount);
        map.put("trendingScore", trendingScore);
        // tagData는 유형별로 필드가 다르므로, 필요한 공통 필드만 넣거나, 혹은 tagData 자체를 넣을 수 있음
        // 여기서는 주요 필드만 포함하고, tagData는 별도로 관리하거나 필요시 추가
        if (TYPE_LOCATION.equals(tagType)) {
            if (latitude != null && longitude != null) {
                map.put("coordinates", new GeoPoint(latitude, longitude));
            }
            map.put("address", address);
        } else {
            // 다른 태그 유형에 대한 특정 필드가 있다면 여기서 추가
            // 예: map.putAll(tagData); // tagData의 모든 내용을 포함할 수도 있음
        }
        return map;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Tag tag = (Tag) obj;
        return tagId.equals(tag.tagId);
    }
    
    @Override
    public int hashCode() {
        return tagId.hashCode();
    }
} 