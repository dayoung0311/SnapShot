package com.example.snapshot.ui.tag;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.snapshot.R;
import com.example.snapshot.databinding.ActivityTagDetailBinding;
import com.example.snapshot.model.Post;
import com.example.snapshot.model.Tag;
import com.example.snapshot.repository.NotificationRepository;
import com.example.snapshot.repository.PostRepository;
import com.example.snapshot.repository.TagRepository;
import com.example.snapshot.repository.UserRepository;
import com.example.snapshot.ui.home.PostAdapter;
import com.example.snapshot.utils.EnvConfig;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.Query;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TagDetailActivity extends AppCompatActivity implements OnMapReadyCallback {

    private ActivityTagDetailBinding binding;
    private TagRepository tagRepository;
    private PostRepository postRepository;
    private UserRepository userRepository;
    private NotificationRepository notificationRepository;
    private PostAdapter postAdapter;
    private List<Post> postList = new ArrayList<>();
    
    private String tagId;
    private Tag currentTag;
    private GoogleMap googleMap;
    private String currentUserId;
    private boolean isTagSaved = false;
    private boolean isTagSubscribed = false;
    private MenuItem saveMenuItem;
    private MenuItem subscribeMenuItem;
    
    public static final String EXTRA_TAG_ID = "extra_tag_id";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        binding = ActivityTagDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 저장소 초기화
        tagRepository = TagRepository.getInstance();
        postRepository = PostRepository.getInstance();
        userRepository = UserRepository.getInstance();
        notificationRepository = NotificationRepository.getInstance();
        
        // 현재 사용자 정보 가져오기
        FirebaseUser currentUser = userRepository.getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        }
        
        // 툴바 설정
        setupToolbar();
        
        // 리사이클러뷰 설정
        setupRecyclerView();
        
        // 탭 레이아웃 설정
        setupTabLayout();
        
        // 지도 초기화
        binding.mapView.onCreate(savedInstanceState);
        binding.mapView.getMapAsync(this);
        
        // 인텐트에서 태그 ID 가져오기
        tagId = getIntent().getStringExtra(EXTRA_TAG_ID);
        if (tagId == null || tagId.isEmpty()) {
            Toast.makeText(this, "태그 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // 태그 정보 로드
        loadTagInfo();
        
        // 태그 저장/구독 상태 확인
        checkTagSavedStatus();
        checkTagSubscriptionStatus();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_tag_detail, menu);
        saveMenuItem = menu.findItem(R.id.menu_save_tag);
        subscribeMenuItem = menu.findItem(R.id.menu_subscribe_tag);
        updateMenuIcons();
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.menu_save_tag) {
            if (isTagSaved) {
                unsaveTag();
            } else {
                saveTag();
            }
            return true;
        } else if (id == R.id.menu_subscribe_tag) {
            if (isTagSubscribed) {
                unsubscribeFromTag();
            } else {
                subscribeToTag();
            }
            return true;
        } else if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
    }
    
    private void setupRecyclerView() {
        postAdapter = new PostAdapter(postList, this);
        binding.recyclerPosts.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPosts.setAdapter(postAdapter);
    }
    
    private void setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                if (position == 0) { // 게시물 탭
                    binding.recyclerPosts.setVisibility(View.VISIBLE);
                    binding.mapContainer.setVisibility(View.GONE);
                } else if (position == 1) { // 정보 탭
                    binding.recyclerPosts.setVisibility(View.GONE);
                    
                    // 위치 태그인 경우 지도 표시
                    if (currentTag != null && Tag.TYPE_LOCATION.equals(currentTag.getTagType())) {
                        binding.mapContainer.setVisibility(View.VISIBLE);
                        updateMapWithLocation();
                    } else {
                        binding.mapContainer.setVisibility(View.GONE);
                    }
                }
            }
            
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // 사용하지 않음
            }
            
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // 사용하지 않음
            }
        });
    }
    
    private void loadTagInfo() {
        showLoading(true);
        
        tagRepository.getTagById(tagId)
                .addOnSuccessListener(documentSnapshot -> {
                    showLoading(false);
                    if (documentSnapshot.exists()) {
                        currentTag = documentSnapshot.toObject(Tag.class);
                        if (currentTag != null) {
                            updateTagUI(currentTag);
                            loadPostsWithTag();
                            
                            // 태그 사용 횟수 증가
                            tagRepository.incrementTagUseCount(tagId);
                        }
                    } else {
                        Toast.makeText(this, "태그 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "태그 정보 로드 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
    }
    
    private void updateTagUI(Tag tag) {
        // 태그 이름 설정
        binding.tvTagName.setText(tag.getName());
        
        // 태그 유형 설정
        String tagTypeText = "";
        switch (tag.getTagType()) {
            case Tag.TYPE_LOCATION:
                tagTypeText = "위치 태그";
                break;
            case Tag.TYPE_PRODUCT:
                tagTypeText = "제품 태그";
                break;
            case Tag.TYPE_BRAND:
                tagTypeText = "브랜드 태그";
                break;
            case Tag.TYPE_PRICE:
                tagTypeText = "가격 태그";
                break;
            case Tag.TYPE_EVENT:
                tagTypeText = "이벤트 태그";
                break;
        }
        binding.tvTagType.setText(tagTypeText);
        
        // 태그 설명 설정
        binding.tvTagDescription.setText(tag.getDescription());
        
        // 툴바 제목 설정
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(tag.getName());
        }
        
        // 태그 유형별 추가 정보 처리
        if (Tag.TYPE_LOCATION.equals(tag.getTagType())) {
            setupLocationTag(tag);
        } else if (Tag.TYPE_PRODUCT.equals(tag.getTagType())) {
            setupProductTag(tag);
        } else if (Tag.TYPE_PRICE.equals(tag.getTagType())) {
            setupPriceTag(tag);
        } else if (Tag.TYPE_EVENT.equals(tag.getTagType())) {
            setupEventTag(tag);
        }
    }
    
    private void setupLocationTag(Tag tag) {
        Map<String, Object> tagData = tag.getTagData();
        if (tagData != null) {
            // 주소 정보가 있으면 설명에 추가
            String address = (String) tagData.get("address");
            if (address != null && !address.isEmpty()) {
                binding.tvTagDescription.setText(address);
            }
        }
    }
    
    private void updateMapWithLocation() {
        if (googleMap != null && currentTag != null && Tag.TYPE_LOCATION.equals(currentTag.getTagType())) {
            Map<String, Object> tagData = currentTag.getTagData();
            if (tagData != null) {
                Object coordinatesObj = tagData.get("coordinates");
                if (coordinatesObj instanceof GeoPoint) {
                    GeoPoint coordinates = (GeoPoint) coordinatesObj;
                    LatLng location = new LatLng(coordinates.getLatitude(), coordinates.getLongitude());
                    
                    // 지도 초기화
                    googleMap.clear();
                    
                    // 마커 추가
                    googleMap.addMarker(new MarkerOptions()
                            .position(location)
                            .title(currentTag.getName()));
                    
                    // 카메라 위치 이동
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f));
                }
            }
        }
    }
    
    private void setupProductTag(Tag tag) {
        Map<String, Object> tagData = tag.getTagData();
        if (tagData != null) {
            // 가격 정보가 있으면 설명에 추가
            Object priceObj = tagData.get("price");
            String brand = (String) tagData.get("brand");
            
            StringBuilder description = new StringBuilder();
            if (brand != null && !brand.isEmpty()) {
                description.append("브랜드: ").append(brand).append("\n");
            }
            
            if (priceObj instanceof Number) {
                double price = ((Number) priceObj).doubleValue();
                description.append("가격: ").append(formatPrice(price)).append("원");
            }
            
            if (description.length() > 0) {
                binding.tvTagDescription.setText(description.toString());
            }
            
            // 제품 URL이 있으면 링크 버튼 표시
            String productUrl = (String) tagData.get("productUrl");
            if (productUrl != null && !productUrl.isEmpty()) {
                binding.btnExternalLink.setVisibility(View.VISIBLE);
                binding.btnExternalLink.setText("구매하기");
                binding.btnExternalLink.setOnClickListener(v -> {
                    openUrl(productUrl);
                });
            } else {
                binding.btnExternalLink.setVisibility(View.GONE);
            }
        }
    }
    
    private void setupPriceTag(Tag tag) {
        Map<String, Object> tagData = tag.getTagData();
        if (tagData != null) {
            Object amountObj = tagData.get("amount");
            String currency = (String) tagData.get("currency");
            
            if (amountObj instanceof Number) {
                double amount = ((Number) amountObj).doubleValue();
                String priceText = formatPrice(amount);
                if (currency != null && !currency.isEmpty()) {
                    priceText += " " + currency;
                } else {
                    priceText += "원";
                }
                binding.tvTagDescription.setText(priceText);
            }
        }
    }
    
    private void setupEventTag(Tag tag) {
        Map<String, Object> tagData = tag.getTagData();
        if (tagData != null) {
            String startDate = (String) tagData.get("startDate");
            String endDate = (String) tagData.get("endDate");
            String website = (String) tagData.get("website");
            
            StringBuilder description = new StringBuilder();
            if (startDate != null && !startDate.isEmpty()) {
                description.append("시작일: ").append(startDate);
                if (endDate != null && !endDate.isEmpty()) {
                    description.append("\n종료일: ").append(endDate);
                }
                description.append("\n");
            }
            
            if (tag.getDescription() != null && !tag.getDescription().isEmpty()) {
                description.append(tag.getDescription());
            }
            
            if (description.length() > 0) {
                binding.tvTagDescription.setText(description.toString());
            }
            
            // 웹사이트가 있으면 링크 버튼 표시
            if (website != null && !website.isEmpty()) {
                binding.btnExternalLink.setVisibility(View.VISIBLE);
                binding.btnExternalLink.setText("웹사이트 방문");
                binding.btnExternalLink.setOnClickListener(v -> {
                    openUrl(website);
                });
            } else {
                binding.btnExternalLink.setVisibility(View.GONE);
            }
        }
    }
    
    private void openUrl(String url) {
        if (url != null && !url.isEmpty()) {
            // URL 스킴 확인 및 추가
            String properUrl = url;
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                properUrl = "https://" + url;
            }
            
            try {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                intent.setData(android.net.Uri.parse(properUrl));
                startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                // 인텐트를 처리할 수 있는 앱이 없는 경우 사용자에게 알림
                Toast.makeText(this, "웹 페이지를 열 수 있는 앱이 설치되어 있지 않습니다.", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                // 기타 예외 처리 (예: 잘못된 URL 형식)
                Toast.makeText(this, "웹 페이지를 여는 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private String formatPrice(double price) {
        return NumberFormat.getNumberInstance(Locale.KOREA).format(price);
    }
    
    private void loadPostsWithTag() {
        showLoading(true);
        
        postRepository.getPostsByTagId(tagId)
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    showLoading(false);
                    postList.clear();
                    
                    for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                        Post post = document.toObject(Post.class);
                        if (post != null) {
                            postList.add(post);
                        }
                    }
                    
                    postAdapter.notifyDataSetChanged();
                    
                    // 게시물이 없는 경우 메시지 표시
                    if (postList.isEmpty()) {
                        binding.tvEmptyPosts.setVisibility(View.VISIBLE);
                    } else {
                        binding.tvEmptyPosts.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "게시물 로드 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    
    private void checkTagSavedStatus() {
        if (currentUserId == null || tagId == null) {
            return;
        }
        
        tagRepository.isTagSavedByUser(currentUserId, tagId)
                .addOnSuccessListener(documentSnapshot -> {
                    isTagSaved = documentSnapshot.exists();
                    updateMenuIcons();
                });
    }
    
    private void checkTagSubscriptionStatus() {
        if (currentUserId == null || tagId == null) {
            return;
        }
        
        notificationRepository.isTagSubscribedByUser(currentUserId, tagId)
                .addOnSuccessListener(documentSnapshot -> {
                    isTagSubscribed = documentSnapshot.exists();
                    updateMenuIcons();
                });
    }
    
    private void updateMenuIcons() {
        if (saveMenuItem != null) {
            saveMenuItem.setIcon(isTagSaved 
                    ? R.drawable.ic_bookmark_filled 
                    : R.drawable.ic_bookmark_border);
            saveMenuItem.setTitle(isTagSaved 
                    ? R.string.unsave_tag 
                    : R.string.save_tag);
        }
        
        if (subscribeMenuItem != null) {
            subscribeMenuItem.setIcon(isTagSubscribed 
                    ? R.drawable.ic_notifications_active 
                    : R.drawable.ic_notifications);
            subscribeMenuItem.setTitle(isTagSubscribed 
                    ? R.string.unsubscribe_tag 
                    : R.string.subscribe_tag);
        }
    }
    
    private void saveTag() {
        if (currentUserId == null || currentTag == null) {
            return;
        }
        
        tagRepository.saveTagForUser(currentUserId, tagId)
                .addOnSuccessListener(aVoid -> {
                    isTagSaved = true;
                    updateMenuIcons();
                    Toast.makeText(this, R.string.tag_saved, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "태그 저장 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    
    private void unsaveTag() {
        if (currentUserId == null || currentTag == null) {
            return;
        }
        
        tagRepository.unsaveTagForUser(currentUserId, tagId)
                .addOnSuccessListener(aVoid -> {
                    isTagSaved = false;
                    updateMenuIcons();
                    Toast.makeText(this, R.string.tag_unsaved, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "태그 저장 취소 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    
    private void subscribeToTag() {
        if (currentUserId == null || currentTag == null) {
            return;
        }
        
        notificationRepository.subscribeToTag(currentUserId, tagId, currentTag.getName())
                .addOnSuccessListener(aVoid -> {
                    isTagSubscribed = true;
                    updateMenuIcons();
                    Toast.makeText(this, R.string.tag_subscribed, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "태그 알림 설정 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    
    private void unsubscribeFromTag() {
        if (currentUserId == null || currentTag == null) {
            return;
        }
        
        notificationRepository.unsubscribeFromTag(currentUserId, tagId)
                .addOnSuccessListener(aVoid -> {
                    isTagSubscribed = false;
                    updateMenuIcons();
                    Toast.makeText(this, R.string.tag_unsubscribed, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "태그 알림 취소 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    
    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }
    
    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        
        if (currentTag != null && Tag.TYPE_LOCATION.equals(currentTag.getTagType())) {
            updateMapWithLocation();
        }
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        binding.mapView.onStart();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        binding.mapView.onResume();
    }
    
    @Override
    protected void onPause() {
        binding.mapView.onPause();
        super.onPause();
    }
    
    @Override
    protected void onStop() {
        binding.mapView.onStop();
        super.onStop();
    }
    
    @Override
    protected void onDestroy() {
        binding.mapView.onDestroy();
        super.onDestroy();
    }
    
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        binding.mapView.onSaveInstanceState(outState);
    }
    
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        binding.mapView.onLowMemory();
    }
} 