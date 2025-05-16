package com.example.snapshot.ui.post;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.snapshot.R;
import com.example.snapshot.databinding.ActivityCreatePostBinding;
import com.example.snapshot.model.Post;
import com.example.snapshot.model.Tag;
import com.example.snapshot.repository.PostRepository;
import com.example.snapshot.repository.UserRepository;
import com.example.snapshot.repository.TagRepository;
import com.example.snapshot.service.TagSuggestionService;
import com.example.snapshot.ui.home.TagAdapter;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.Timestamp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class CreatePostActivity extends AppCompatActivity {
    
    public static final String EXTRA_EDIT_POST_ID = "edit_post_id"; // 수정 모드용 postId 키

    private ActivityCreatePostBinding binding;
    private PostRepository postRepository;
    private UserRepository userRepository;
    private TagRepository tagRepository;
    private TagSuggestionService tagSuggestionService;
    
    private Uri selectedImageUri;
    private List<Tag> addedTags = new ArrayList<>();
    private TagAdapter addedTagsAdapter;
    private TagAdapter suggestedTagsAdapter;
    private List<Tag> suggestedTags = new ArrayList<>();
    
    private String editingPostId = null; // 수정 중인 게시물의 ID, null이면 새 게시물 작성 모드
    private Post currentEditingPost = null; // 수정 중인 게시물의 원본 데이터
    
    // 권한 요청 코드
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 101;
    
    // 갤러리에서 이미지 선택 런처
    private final ActivityResultLauncher<String> getContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    binding.ivPostImage.setImageURI(uri);
                    binding.btnAddImage.setVisibility(View.GONE);
                    
                    // 이미지에서 태그 추천
                    generateTagSuggestions();
                }
            });
    
    // 카메라로 사진 촬영 런처
    private final ActivityResultLauncher<Void> takePicture = registerForActivityResult(
            new ActivityResultContracts.TakePicturePreview(),
            bitmap -> {
                if (bitmap != null) {
                    binding.ivPostImage.setImageBitmap(bitmap);
                    binding.btnAddImage.setVisibility(View.GONE);
                    
                    // 비트맵을 Uri로 변환 (임시 방법)
                    selectedImageUri = getImageUri(bitmap);
                    
                    // 이미지에서 태그 추천
                    suggestTagsFromBitmap(bitmap);
                }
            });
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 시스템 윈도우 설정
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        
        // 뷰 바인딩 초기화
        binding = ActivityCreatePostBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 저장소 초기화
        postRepository = PostRepository.getInstance();
        userRepository = UserRepository.getInstance();
        tagRepository = TagRepository.getInstance();
        tagSuggestionService = TagSuggestionService.getInstance(this);
        
        // 툴바 설정
        setupToolbar();
        
        // 태그 리사이클러뷰 설정
        setupRecyclerViews();
        
        // 이벤트 리스너 설정
        setupListeners();
        
        // 기본 태그 타입 칩 추가
        addTagTypeChips();

        // 인텐트에서 postId 확인 (수정 모드인지)
        if (getIntent().hasExtra(EXTRA_EDIT_POST_ID)) {
            editingPostId = getIntent().getStringExtra(EXTRA_EDIT_POST_ID);
            if (editingPostId != null && !editingPostId.isEmpty()) {
                // 수정 모드이므로 기존 게시물 데이터 로드
                loadPostForEditing(editingPostId);
            }
        }

        // 툴바 제목 설정 (새 게시물 or 게시물 수정)
        setupToolbarTitle();
    }
    
    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }
    
    private void setupRecyclerViews() {
        // 추가된 태그 어댑터 설정
        addedTagsAdapter = new TagAdapter(this, addedTags, false);
        binding.recyclerAddedTags.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.recyclerAddedTags.setAdapter(addedTagsAdapter);
        
        // 추천 태그 어댑터 설정 - isViewMode를 true로 설정하여 롱 홀드 시 저장 메뉴가 표시되도록 함
        suggestedTagsAdapter = new TagAdapter(this, suggestedTags, true);
        binding.recyclerSuggestedTags.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.recyclerSuggestedTags.setAdapter(suggestedTagsAdapter);
        
        // 태그 클릭 리스너 설정
        suggestedTagsAdapter.setOnTagClickListener(position -> {
            if (position >= 0 && position < suggestedTags.size()) {
                // 추천 태그는 클릭 시 바로 추가
                Tag clickedTag = suggestedTags.get(position);
                addTag(clickedTag); 
            }
        });
        
        // 추가된 태그 클릭 리스너 설정
        addedTagsAdapter.setOnTagClickListener(position -> {
            // 단순 클릭 시 동작 없음 또는 태그 상세 정보 표시 (현재는 비워둠)
            // if (position >= 0 && position < addedTags.size()) {
            //     // 예: navigateToTagDetail(addedTags.get(position));
            // }
        });
        
        // 추가된 태그 롱클릭 리스너 설정
        addedTagsAdapter.setOnTagLongClickListener(new TagAdapter.OnTagLongClickListener() {
            @Override
            public void onTagEdit(int position) {
                // 추가된 태그 롱클릭 시 편집 다이얼로그 표시
                if (position >= 0 && position < addedTags.size()) {
                    showTagEditDialog(addedTags.get(position), position);
                }
            }
            
            @Override
            public void onTagDelete(int position) {
                // 태그 삭제 처리
                if (position >= 0 && position < addedTags.size()) {
                    confirmTagDelete(addedTags.get(position), position);
                }
            }
            
            @Override
            public void onTagSave(int position) {
                // 태그 저장 기능 구현
                if (position >= 0 && position < addedTags.size()) {
                    saveTagForCurrentUser(addedTags.get(position));
                }
            }
        });
        
        // 추천 태그 어댑터 롱클릭 리스너 설정
        suggestedTagsAdapter.setOnTagLongClickListener(new TagAdapter.OnTagLongClickListener() {
            @Override
            public void onTagEdit(int position) {
                // 추천 태그 롱클릭 시 편집 다이얼로그 (내용 수정 후 추가/업데이트)
                if (position >= 0 && position < suggestedTags.size()) {
                    showTagEditDialog(suggestedTags.get(position)); 
                }
            }
            
            @Override
            public void onTagDelete(int position) {
                // 삭제 기능 없음
            }
            
            @Override
            public void onTagSave(int position) {
                // 저장 기능 구현
                if (position >= 0 && position < suggestedTags.size()) {
                    saveTagForCurrentUser(suggestedTags.get(position));
                }
            }
        });
    }
    
    private void setupListeners() {
        // 이미지 추가 버튼 클릭
        binding.btnAddImage.setOnClickListener(v -> {
            showImagePickerOptions();
        });
        
        // 게시 버튼 클릭
        binding.btnPost.setOnClickListener(v -> {
            createPost();
        });
    }
    
    private void showImagePickerOptions() {
        String[] options = {getString(R.string.take_photo), getString(R.string.upload_photo)};
        
        // 여기서는 간단하게 갤러리만 열도록 구현
        checkAndRequestPermissions();
    }
    
    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        } else {
            openGallery();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(this, R.string.error_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void openGallery() {
        getContent.launch("image/*");
    }
    
    private void openCamera() {
        takePicture.launch(null);
    }
    
    private Uri getImageUri(Bitmap bitmap) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(getContentResolver(), bitmap, "Title", null);
        return Uri.parse(path);
    }
    
    private void addTagTypeChips() {
        // 위치 태그
        Chip locationChip = new Chip(this);
        locationChip.setText(R.string.tag_location);
        locationChip.setChipIconResource(R.drawable.ic_location);
        locationChip.setClickable(true);
        locationChip.setCheckable(true);
        locationChip.setTag(Tag.TYPE_LOCATION);
        binding.chipGroupTags.addView(locationChip);
        
        // 제품 태그
        Chip productChip = new Chip(this);
        productChip.setText(R.string.tag_product);
        productChip.setChipIconResource(R.drawable.ic_product);
        productChip.setClickable(true);
        productChip.setCheckable(true);
        productChip.setTag(Tag.TYPE_PRODUCT);
        binding.chipGroupTags.addView(productChip);
        
        // 브랜드 태그
        Chip brandChip = new Chip(this);
        brandChip.setText(R.string.tag_brand);
        brandChip.setChipIconResource(R.drawable.ic_brand);
        brandChip.setClickable(true);
        brandChip.setCheckable(true);
        brandChip.setTag(Tag.TYPE_BRAND);
        binding.chipGroupTags.addView(brandChip);
        
        // 가격 태그
        Chip priceChip = new Chip(this);
        priceChip.setText(R.string.tag_price);
        priceChip.setChipIconResource(R.drawable.ic_price);
        priceChip.setClickable(true);
        priceChip.setCheckable(true);
        priceChip.setTag(Tag.TYPE_PRICE);
        binding.chipGroupTags.addView(priceChip);
        
        // 이벤트 태그
        Chip eventChip = new Chip(this);
        eventChip.setText(R.string.tag_event);
        eventChip.setChipIconResource(R.drawable.ic_event);
        eventChip.setClickable(true);
        eventChip.setCheckable(true);
        eventChip.setTag(Tag.TYPE_EVENT);
        binding.chipGroupTags.addView(eventChip);
        
        // 태그 유형 클릭 이벤트 처리
        locationChip.setOnClickListener(v -> showTagEditDialog(Tag.TYPE_LOCATION));
        productChip.setOnClickListener(v -> showTagEditDialog(Tag.TYPE_PRODUCT));
        brandChip.setOnClickListener(v -> showTagEditDialog(Tag.TYPE_BRAND));
        priceChip.setOnClickListener(v -> showTagEditDialog(Tag.TYPE_PRICE));
        eventChip.setOnClickListener(v -> showTagEditDialog(Tag.TYPE_EVENT));
    }
    
    /**
     * 태그 세부 정보를 입력받는 다이얼로그를 표시합니다. (새 태그 추가용 - 태그 타입만 받는 경우)
     */
    private void showTagEditDialog(String tagType) {
        // 새 태그를 만드는 것이므로 첫 번째 인자는 null, 두 번째 인자로 tagType을 전달
        showTagEditDialog(null, tagType);
    }
    
    /**
     * 태그 세부 정보를 입력받는 다이얼로그를 표시합니다. (기존 태그 클릭 시 - Tag 객체를 받는 경우)
     * 이 메서드는 주로 태그 클릭 시 호출되어, 태그의 상세 정보를 보여주거나 편집할 수 있는
     * 다이얼로그(showTagEditDialog(Tag, String))를 띄우는 역할을 합니다.
     */
    private void showTagEditDialog(Tag tag) {
        if (tag != null) {
            // 기존 태그 정보를 바탕으로 태그 유형별 상세 다이얼로그를 띄웁니다.
            // 이 다이얼로그에서 사용자는 정보를 보거나 수정할 수 있습니다.
            // 여기서 호출하는 showTagEditDialog는 (Tag, String) 시그니처를 가져야 합니다.
            showTagEditDialog(tag, tag.getTagType());
        }
    }
    
    /**
     * 태그 세부 정보를 입력받는 다이얼로그를 표시합니다. (핵심 로직)
     * @param tag 기존 태그 (수정 모드일 경우). null이면 새 태그 생성 모드 (tagType으로 구분)
     * @param tagType 태그 유형 (위치, 제품, 브랜드, 가격, 이벤트)
     */
    private void showTagEditDialog(Tag tag, String tagType) {
        // 기존: if (tag == null || tagType == null || tagType.isEmpty()) {
        // 새 태그 추가 시 tag는 null이므로, tagType만 체크하도록 변경합니다.
        if (tagType == null || tagType.isEmpty()) {
            Toast.makeText(this, "잘못된 태그 정보입니다(타입 없음).", Toast.LENGTH_SHORT).show();
            return;
        }

        // 다이얼로그 레이아웃 인플레이트
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_tag, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        String tagTypeTitle = "";
        if (Tag.TYPE_LOCATION.equals(tagType)) {
            tagTypeTitle = getString(R.string.tag_type_location);
        } else if (Tag.TYPE_PRODUCT.equals(tagType)) {
            tagTypeTitle = getString(R.string.tag_type_product);
        } else if (Tag.TYPE_BRAND.equals(tagType)) {
            tagTypeTitle = getString(R.string.tag_type_brand);
        } else if (Tag.TYPE_PRICE.equals(tagType)) {
            tagTypeTitle = getString(R.string.tag_type_price);
        } else if (Tag.TYPE_EVENT.equals(tagType)) {
            tagTypeTitle = getString(R.string.tag_type_event);
        }
        builder.setTitle(tagTypeTitle + " 태그 수정");

        EditText etTagName = dialogView.findViewById(R.id.et_tag_name);
        EditText etTagDescription = dialogView.findViewById(R.id.et_tag_description);

        // 기존 태그가 있는 경우 정보 채우기 (tag가 null이 아닐 때)
        if (tag != null) {
            etTagName.setText(tag.getName());
            if (tag.getDescription() != null) {
                etTagDescription.setText(tag.getDescription());
            }
        } else {
            // 새 태그 추가 시에는 필드를 비워둠
            etTagName.setText("");
            etTagDescription.setText("");
        }

        ViewGroup fieldContainer = dialogView.findViewById(R.id.container_tag_fields);
        fieldContainer.removeAllViews();
        View additionalFieldsView = null;

        switch (tagType) {
            case Tag.TYPE_LOCATION:
                additionalFieldsView = getLayoutInflater().inflate(R.layout.layout_location_fields, fieldContainer, false);
                fieldContainer.addView(additionalFieldsView);
                Button btnGetLocation = additionalFieldsView.findViewById(R.id.btn_get_current_location);
                btnGetLocation.setOnClickListener(v -> getCurrentLocation(dialogView));
                break;
            case Tag.TYPE_PRODUCT:
                additionalFieldsView = getLayoutInflater().inflate(R.layout.layout_product_fields, fieldContainer, false);
                fieldContainer.addView(additionalFieldsView);
                break;
            case Tag.TYPE_BRAND:
                additionalFieldsView = getLayoutInflater().inflate(R.layout.layout_brand_fields, fieldContainer, false);
                fieldContainer.addView(additionalFieldsView);
                break;
            case Tag.TYPE_PRICE:
                additionalFieldsView = getLayoutInflater().inflate(R.layout.layout_price_fields, fieldContainer, false);
                fieldContainer.addView(additionalFieldsView);
                break;
            case Tag.TYPE_EVENT:
                additionalFieldsView = getLayoutInflater().inflate(R.layout.layout_event_fields, fieldContainer, false);
                fieldContainer.addView(additionalFieldsView);
                setupDatePickers(dialogView);
                break;
        }
        if (additionalFieldsView != null) {
            fillAdditionalFields(tag, additionalFieldsView, tagType);
        }

        builder.setView(dialogView);
        builder.setPositiveButton(tag == null ? "추가" : "수정", (dialog, which) -> {
            try {
                // 기본 정보 가져오기
                String name = etTagName.getText().toString().trim();
                String description = etTagDescription.getText().toString().trim();

                // 유효성 검사
                if (name.isEmpty()) {
                    etTagName.setError(getString(R.string.error_required_field));
                    return;
                }

                // 태그 ID (새 태그면 생성, 기존 태그면 유지)
                String tagId = tag != null ? tag.getTagId() : UUID.randomUUID().toString();

                // 태그 유형에 따라 다른 방식으로 태그 생성
                Tag newTag; 
                try {
                    switch (tagType) {
                        case Tag.TYPE_LOCATION:
                            newTag = createLocationTag(tagId, name, description, dialogView);
                            break;
                        case Tag.TYPE_PRODUCT:
                            newTag = createProductTag(tagId, name, description, dialogView);
                            break;
                        case Tag.TYPE_BRAND:
                            newTag = createBrandTag(tagId, name, description, dialogView);
                            break;
                        case Tag.TYPE_PRICE:
                            newTag = createPriceTag(tagId, name, description, dialogView);
                            break;
                        case Tag.TYPE_EVENT:
                            newTag = createEventTag(tagId, name, description, dialogView);
                            break;
                        default:
                            newTag = new Tag(tagId, name, description, tagType);
                            break;
                    }

                    // creatorId 설정 (새 태그인 경우 현재 사용자 ID, 기존 태그인 경우 유지)
                    FirebaseUser currentUser = userRepository.getCurrentUser();
                    if (currentUser != null) {
                        if (tag == null) { // 새 태그
                            newTag.setCreatorId(currentUser.getUid());
                        } else if (tag.getCreatorId() != null) { // 기존 태그에 creatorId가 있으면 유지
                            newTag.setCreatorId(tag.getCreatorId());
                        } else { // 기존 태그에 creatorId가 없으면 현재 사용자 ID로 설정 (정책에 따라 다를 수 있음)
                             newTag.setCreatorId(currentUser.getUid());
                        }
                    }
                    
                    // useCount 및 lastUsed 등은 Firestore에 저장 시 TagRepository에서 관리하거나,
                    // 여기서 기본값을 설정할 수 있습니다. 여기서는 TagRepository의 updateTag가 처리하도록 둡니다.
                    // (Tag 모델의 생성자나 setter에서 초기화될 수 있음)

                    // 중요: newTag를 Firestore 'tags' 컬렉션에 저장 (upsert)
                    tagRepository.updateTag(newTag)
                        .addOnSuccessListener(aVoid_repo -> {
                            // Firestore에 성공적으로 저장/업데이트 후, 내부 리스트 및 UI 처리
                            // 이 때 tag는 showTagEditDialog의 입력 파라미터 (원본 태그)
                            if (tag != null) { 
                                // 기존 태그 업데이트 (addedTags 또는 suggestedTags 내부의 태그일 수 있음)
                                updateTag(tag, newTag); // CreatePostActivity 내부의 updateTag 메서드
                            } else {
                                // 새 태그 추가 (tag 파라미터가 null인 경우)
                                addTag(newTag); // CreatePostActivity 내부의 addTag 메서드
                            }
                            Toast.makeText(CreatePostActivity.this,
                                    tag == null ? "태그가 추가되었습니다" : "태그가 업데이트되었습니다",
                                    Toast.LENGTH_SHORT).show();
                        })
                        .addOnFailureListener(e_repo -> {
                            Toast.makeText(CreatePostActivity.this,
                                    "태그를 Firestore에 저장 중 오류 발생: " + e_repo.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        });

                } catch (IllegalArgumentException e) {
                    // 유효성 검사 실패 (이미 토스트 메시지 표시됨)
                    Toast.makeText(CreatePostActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(CreatePostActivity.this,
                        "태그 처리 중 오류가 발생했습니다: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("취소", null);
        builder.show();
    }
    
    /**
     * 태그 유형에 따라 추가 필드에 기존 값 채우기
     */
    private void fillAdditionalFields(Tag tag, View view, String tagType) {
        try {
            // switch 문 이전에 tag 객체와 tag.getTagData() null 체크 추가
            if (tag == null || tag.getTagData() == null) {
                // tag 또는 tagData가 null이면 추가 필드를 채울 수 없음
                // 필요시 로그를 남기거나 사용자에게 알림
                android.util.Log.w("CreatePostActivity", "fillAdditionalFields: Tag or TagData is null. Cannot fill fields for type: " + tagType);
                return;
            }

            Map<String, Object> data = tag.getTagData(); // 이제 data는 null이 아님

            switch (tagType) {
                case Tag.TYPE_LOCATION:
                    EditText etAddress = view.findViewById(R.id.et_location_address);
                    EditText etLatitude = view.findViewById(R.id.et_location_latitude);
                    EditText etLongitude = view.findViewById(R.id.et_location_longitude);

                    if (data.containsKey("address") && data.get("address") != null) {
                        etAddress.setText(data.get("address").toString());
                    } else {
                        etAddress.setText("");
                    }

                    if (data.containsKey("latitude") && data.get("latitude") != null &&
                        data.containsKey("longitude") && data.get("longitude") != null) {
                        try {
                            etLatitude.setText(String.valueOf(data.get("latitude")));
                            etLongitude.setText(String.valueOf(data.get("longitude")));
                        } catch (Exception e) {
                            etLatitude.setText("");
                            etLongitude.setText("");
                            android.util.Log.e("CreatePostActivity", "Error parsing coordinates for location tag: " + data, e);
                        }
                    } else {
                        etLatitude.setText("");
                        etLongitude.setText("");
                    }
                    break;
                    
                case Tag.TYPE_PRODUCT:
                    EditText etProductUrl = view.findViewById(R.id.et_product_url);
                    EditText etProductPrice = view.findViewById(R.id.et_product_price);
                    EditText etProductBrand = view.findViewById(R.id.et_product_brand);
                    
                    if (data.containsKey("url") && data.get("url") != null) {
                        etProductUrl.setText(data.get("url").toString());
                    } else {
                        etProductUrl.setText("");
                    }
                    
                    if (data.containsKey("price") && data.get("price") != null) {
                        try {
                            double priceValue = ((Number) data.get("price")).doubleValue();
                            // 가격을 정수 형태로 표시 (소수점 없이)
                            etProductPrice.setText(String.format(Locale.getDefault(), "%.0f", priceValue));
                        } catch (Exception e) {
                            etProductPrice.setText(""); 
                            android.util.Log.e("CreatePostActivity", "Error parsing price for product tag: " + data.get("price"), e);
                        }
                    } else {
                        etProductPrice.setText("");
                    }
                    
                    if (data.containsKey("brand") && data.get("brand") != null) {
                        etProductBrand.setText(data.get("brand").toString());
                    } else {
                        etProductBrand.setText("");
                    }
                    break;
                    
                case Tag.TYPE_BRAND:
                    EditText etBrandWebsite = view.findViewById(R.id.et_brand_website);
                    EditText etBrandLogoUrl = view.findViewById(R.id.et_brand_logo_url);

                    if (data.containsKey("brandUrl") && data.get("brandUrl") != null) {
                        etBrandWebsite.setText(data.get("brandUrl").toString());
                    } else {
                        etBrandWebsite.setText("");
                    }
                    if (data.containsKey("logoUrl") && data.get("logoUrl") != null) {
                        etBrandLogoUrl.setText(data.get("logoUrl").toString());
                    } else {
                        etBrandLogoUrl.setText("");
                    }
                    break;

                case Tag.TYPE_PRICE:
                    EditText etPriceAmount = view.findViewById(R.id.et_price_amount);
                    EditText etPriceCurrency = view.findViewById(R.id.et_price_currency);

                    if (data.containsKey("amount") && data.get("amount") != null) {
                         try {
                            double amountValue = ((Number) data.get("amount")).doubleValue();
                            etPriceAmount.setText(String.format(Locale.getDefault(), "%.0f", amountValue));
                        } catch (Exception e) {
                            etPriceAmount.setText("");
                            android.util.Log.e("CreatePostActivity", "Error parsing amount for price tag: " + data.get("amount"), e);
                        }
                    } else {
                        etPriceAmount.setText("");
                    }

                    if (data.containsKey("currency") && data.get("currency") != null) {
                        etPriceCurrency.setText(data.get("currency").toString());
                    } else {
                        etPriceCurrency.setText("KRW"); // 기본값 또는 빈 문자열
                    }
                    break;

                case Tag.TYPE_EVENT:
                    EditText etEventStartDate = view.findViewById(R.id.et_event_start_date);
                    EditText etEventEndDate = view.findViewById(R.id.et_event_end_date);
                    EditText etEventWebsite = view.findViewById(R.id.et_event_website);

                    if (data.containsKey("startDate") && data.get("startDate") != null) {
                        etEventStartDate.setText(data.get("startDate").toString());
                    } else {
                        etEventStartDate.setText("");
                    }
                    if (data.containsKey("endDate") && data.get("endDate") != null) {
                        etEventEndDate.setText(data.get("endDate").toString());
                    } else {
                        etEventEndDate.setText("");
                    }
                    if (data.containsKey("website") && data.get("website") != null) {
                        etEventWebsite.setText(data.get("website").toString());
                    } else {
                        etEventWebsite.setText("");
                    }
                    break;
            }
        } catch (Exception e) {
            android.util.Log.e("CreatePostActivity", "Error in fillAdditionalFields for tagType: " + tagType, e);
            Toast.makeText(this, "태그 데이터 표시 중 오류 발생: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * 날짜 선택 기능을 설정합니다.
     */
    private void setupDatePickers(View dialogView) {
        EditText etStartDate = dialogView.findViewById(R.id.et_event_start_date);
        EditText etEndDate = dialogView.findViewById(R.id.et_event_end_date);
        
        // 현재 날짜
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        
        // 시작일 선택
        etStartDate.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, selectedYear, selectedMonth, selectedDay) -> {
                String date = String.format(Locale.getDefault(), "%d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay);
                etStartDate.setText(date);
            }, year, month, day);
            datePickerDialog.show();
        });
        
        // 종료일 선택
        etEndDate.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, selectedYear, selectedMonth, selectedDay) -> {
                String date = String.format(Locale.getDefault(), "%d-%02d-%02d", selectedYear, selectedMonth + 1, selectedDay);
                etEndDate.setText(date);
            }, year, month, day);
            datePickerDialog.show();
        });
    }
    
    /**
     * 현재 위치 정보를 가져옵니다.
     */
    private void getCurrentLocation(View dialogView) {
        // 위치 권한 확인
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        
        // 위치 클라이언트 생성
        FusedLocationProviderClient fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        
        // 현재 위치 가져오기
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        EditText etLatitude = dialogView.findViewById(R.id.et_location_latitude);
                        EditText etLongitude = dialogView.findViewById(R.id.et_location_longitude);
                        
                        etLatitude.setText(String.valueOf(location.getLatitude()));
                        etLongitude.setText(String.valueOf(location.getLongitude()));
                        
                        // Geocoder를 사용하여 주소 가져오기
                        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                        try {
                            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                            if (!addresses.isEmpty()) {
                                Address address = addresses.get(0);
                                String addressText = address.getAddressLine(0);
                                
                                EditText etAddress = dialogView.findViewById(R.id.et_location_address);
                                etAddress.setText(addressText);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Toast.makeText(this, "위치 정보를 가져올 수 없습니다", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(this, e -> 
                        Toast.makeText(this, "위치 정보 가져오기 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
    
    /**
     * 위치 태그를 생성합니다.
     */
    private Tag createLocationTag(String tagId, String name, String description, View dialogView) {
        EditText etAddress = dialogView.findViewById(R.id.et_location_address);
        EditText etLatitude = dialogView.findViewById(R.id.et_location_latitude);
        EditText etLongitude = dialogView.findViewById(R.id.et_location_longitude);
        
        String address = etAddress.getText().toString().trim();
        String latitudeStr = etLatitude.getText().toString().trim();
        String longitudeStr = etLongitude.getText().toString().trim();
        
        if (address.isEmpty()) {
            etAddress.setError(getString(R.string.error_required_field));
            throw new IllegalArgumentException("주소를 입력해주세요");
        }
        
        double latitude;
        double longitude;
        
        try {
            latitude = Double.parseDouble(latitudeStr);
            longitude = Double.parseDouble(longitudeStr);
        } catch (NumberFormatException e) {
            etLatitude.setError(getString(R.string.error_invalid_coordinates));
            throw new IllegalArgumentException("유효한 좌표를 입력해주세요");
        }
        
        return Tag.createLocationTag(tagId, name, address, latitude, longitude);
    }
    
    /**
     * 제품 태그를 생성합니다.
     */
    private Tag createProductTag(String tagId, String name, String description, View dialogView) {
        EditText etProductUrl = dialogView.findViewById(R.id.et_product_url);
        EditText etProductPrice = dialogView.findViewById(R.id.et_product_price);
        EditText etProductBrand = dialogView.findViewById(R.id.et_product_brand);
        
        String productUrl = etProductUrl.getText().toString().trim();
        String priceStr = etProductPrice.getText().toString().trim();
        String brand = etProductBrand.getText().toString().trim();
        
        double price = 0;
        if (!priceStr.isEmpty()) {
            try {
                price = Double.parseDouble(priceStr);
            } catch (NumberFormatException e) {
                etProductPrice.setError(getString(R.string.error_invalid_coordinates));
                throw new IllegalArgumentException("유효한 가격을 입력해주세요");
            }
        }
        
        return Tag.createProductTag(tagId, name, description, productUrl, price, brand);
    }
    
    /**
     * 브랜드 태그를 생성합니다.
     */
    private Tag createBrandTag(String tagId, String name, String description, View dialogView) {
        EditText etBrandWebsite = dialogView.findViewById(R.id.et_brand_website);
        EditText etBrandLogoUrl = dialogView.findViewById(R.id.et_brand_logo_url);
        
        String brandWebsite = etBrandWebsite.getText().toString().trim();
        String brandLogoUrl = etBrandLogoUrl.getText().toString().trim();
        
        return Tag.createBrandTag(tagId, name, description, brandWebsite, brandLogoUrl);
    }
    
    /**
     * 가격 태그를 생성합니다.
     */
    private Tag createPriceTag(String tagId, String name, String description, View dialogView) {
        EditText etPriceAmount = dialogView.findViewById(R.id.et_price_amount);
        EditText etPriceCurrency = dialogView.findViewById(R.id.et_price_currency);
        
        String amountStr = etPriceAmount.getText().toString().trim();
        String currency = etPriceCurrency.getText().toString().trim();
        
        if (amountStr.isEmpty()) {
            etPriceAmount.setError(getString(R.string.error_required_field));
            throw new IllegalArgumentException("금액을 입력해주세요");
        }
        
        if (currency.isEmpty()) {
            currency = "KRW"; // 기본값
        }
        
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            etPriceAmount.setError(getString(R.string.error_invalid_coordinates));
            throw new IllegalArgumentException("유효한 금액을 입력해주세요");
        }
        
        return Tag.createPriceTag(tagId, name, amount, currency);
    }
    
    /**
     * 이벤트 태그를 생성합니다.
     */
    private Tag createEventTag(String tagId, String name, String description, View dialogView) {
        EditText etStartDate = dialogView.findViewById(R.id.et_event_start_date);
        EditText etEndDate = dialogView.findViewById(R.id.et_event_end_date);
        EditText etEventWebsite = dialogView.findViewById(R.id.et_event_website);
        
        String startDate = etStartDate.getText().toString().trim();
        String endDate = etEndDate.getText().toString().trim();
        String eventWebsite = etEventWebsite.getText().toString().trim();
        
        return Tag.createEventTag(tagId, name, description, startDate, endDate, eventWebsite);
    }
    
    private void generateTagSuggestions() {
        // 선택된 이미지가 있을 때만 실행
        if (selectedImageUri == null) return;
        
        try {
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
            suggestTagsFromBitmap(bitmap);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void suggestTagsFromBitmap(Bitmap bitmap) {
        showLoading(true);
        
        // Gemini API로 태그 추천
        tagSuggestionService.suggestTags(bitmap, new TagSuggestionService.TagSuggestionCallback() {
            @Override
            public void onTagsGenerated(List<Tag> tags) {
                runOnUiThread(() -> {
                    suggestedTags.clear();
                    suggestedTags.addAll(tags);
                    suggestedTagsAdapter.notifyDataSetChanged();
                    
                    // 추천 태그가 있으면 표시
                    if (!suggestedTags.isEmpty()) {
                        binding.tvAiSuggestedTags.setVisibility(View.VISIBLE);
                        binding.recyclerSuggestedTags.setVisibility(View.VISIBLE);
                    }
                    
                    showLoading(false);
                });
            }
            
            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> {
                    Toast.makeText(CreatePostActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
                    showLoading(false);
                });
            }
        });
    }
    
    private void addTag(Tag tag) {
        // 이미 추가된 태그인지 확인
        if (!addedTags.contains(tag)) {
            addedTags.add(tag);
            addedTagsAdapter.notifyDataSetChanged();
            
            // 추천 태그에서 제거
            suggestedTags.remove(tag);
            suggestedTagsAdapter.notifyDataSetChanged();
            
            // 추천 태그가 비어있으면 숨기기
            if (suggestedTags.isEmpty()) {
                binding.tvAiSuggestedTags.setVisibility(View.GONE);
                binding.recyclerSuggestedTags.setVisibility(View.GONE);
            }
        }
    }
    
    private void createPost() {
        String caption = binding.etCaption.getText().toString().trim();
        FirebaseUser firebaseUser = userRepository.getCurrentUser();

        if (firebaseUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedImageUri == null && editingPostId == null) { // 새 게시물 작성 시 이미지는 필수
            Toast.makeText(this, "이미지를 선택해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(caption) && addedTags.isEmpty()) {
            Toast.makeText(this, "내용을 입력하거나 태그를 추가해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        // 이미지 데이터 준비 (Bitmap에서 byte[]로 변환)
        byte[] imageData = null;
        boolean imageChanged = false; // 수정 모드에서 이미지가 변경되었는지 여부

        if (selectedImageUri != null) {
            // 새 이미지가 선택되었거나, 기존 이미지 URI와 다른 경우 (문자열 비교로 단순화)
            if (currentEditingPost == null || !selectedImageUri.toString().equals(currentEditingPost.getImageUrl())) {
                imageChanged = true;
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImageUri);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos); // 품질 80으로 압축
                    imageData = baos.toByteArray();
                } catch (IOException e) {
                    showLoading(false);
                    Toast.makeText(this, "이미지 처리 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        } else if (editingPostId != null && currentEditingPost != null && currentEditingPost.getImageUrl() == null) {
            // 수정 모드인데 기존 이미지가 없고 새 이미지도 선택 안 한 경우 (이론상 발생 안해야 함, 방어 코드)
             Toast.makeText(this, "이미지가 없습니다.", Toast.LENGTH_SHORT).show();
             showLoading(false);
             return;
        }

        if (imageChanged && imageData != null) {
            // 이미지가 변경되었으면 업로드 후 URL 받아오기
            String imageFileName = UUID.randomUUID().toString() + ".jpg";
            postRepository.uploadPostImage(imageFileName, imageData)
                    .addOnSuccessListener(taskSnapshot -> taskSnapshot.getStorage().getDownloadUrl()
                            .addOnSuccessListener(uri -> {
                                String imageUrl = uri.toString();
                                if (editingPostId != null) {
                                    updatePostInFirestore(firebaseUser, caption, imageUrl, addedTags);
                                } else {
                                    savePostToFirestore(firebaseUser, caption, imageUrl, addedTags);
                                }
                            })
                            .addOnFailureListener(e -> {
                                showLoading(false);
                                Toast.makeText(CreatePostActivity.this, "이미지 URL 가져오기 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }))
                    .addOnFailureListener(e -> {
                        showLoading(false);
                        Toast.makeText(CreatePostActivity.this, "이미지 업로드 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            // 이미지 변경 없으면 기존 URL 사용 (수정 모드) 또는 이미지 없이 진행 (새 게시물 - 현재는 막혀있음)
            String imageUrl = (editingPostId != null && currentEditingPost != null) ? currentEditingPost.getImageUrl() : null;
            if (editingPostId != null) {
                updatePostInFirestore(firebaseUser, caption, imageUrl, addedTags);
            } else {
                // 이 경우는 selectedImageUri가 null이고 새 게시물일 때인데, 위에서 이미 필터링됨.
                // 만약 이미지 없이 새 게시물 작성을 허용한다면 이 부분 로직 필요.
                savePostToFirestore(firebaseUser, caption, imageUrl, addedTags); 
            }
        }
    }

    private void savePostToFirestore(FirebaseUser firebaseUser, String caption, String imageUrl, List<Tag> tags) {
        String userId = firebaseUser.getUid();

        // UserRepository를 통해 현재 사용자 정보 가져오기
        userRepository.getUserById(userId).addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                com.example.snapshot.model.User currentUser = documentSnapshot.toObject(com.example.snapshot.model.User.class);
                if (currentUser != null) {
                    String userName = currentUser.getUsername();
                    String userProfilePic = currentUser.getProfilePicUrl();

                    Post newPost = new Post(
                            null, // postId는 Repository에서 생성
                            userId,
                            userName, // Firestore User 객체의 username 사용
                            userProfilePic, // Firestore User 객체의 profilePicUrl 사용
                            imageUrl,
                            caption,
                            null, // creationDate는 Post 모델 내부에서 Timestamp.now()로 처리 (또는 Repository에서 설정)
                            tags
                    );

                    postRepository.createPost(newPost)
                            .addOnSuccessListener(aVoid -> {
                                showLoading(false);
                                Toast.makeText(this, "게시물이 성공적으로 작성되었습니다.", Toast.LENGTH_SHORT).show();
                                setResult(Activity.RESULT_OK);
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                showLoading(false);
                                Toast.makeText(this, "게시물 작성 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                } else {
                    showLoading(false);
                    Toast.makeText(this, "게시물 작성 실패: 사용자 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show();
                }
            } else {
                showLoading(false);
                Toast.makeText(this, "게시물 작성 실패: 사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            showLoading(false);
            Toast.makeText(this, "게시물 작성 실패: 사용자 정보 조회 중 오류 발생 - " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void updatePostInFirestore(FirebaseUser firebaseUser, String caption, String imageUrl, List<Tag> tags) {
        if (editingPostId == null || currentEditingPost == null) {
            showLoading(false);
            Toast.makeText(this, "게시물 업데이트 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("caption", caption);
        // tags 필드 업데이트 시 tagNames 필드도 함께 업데이트
        List<String> tagNamesList = tags.stream().map(Tag::getName).collect(Collectors.toList());
        updates.put("tags", tags.stream().map(Tag::toMap).collect(Collectors.toList()));
        updates.put("tagNames", tagNamesList); // tagNames 필드 추가
        updates.put("lastModifiedDate", Timestamp.now());
        if (imageUrl != null) {
            updates.put("imageUrl", imageUrl);
        }

        postRepository.updatePost(editingPostId, updates)
            .addOnSuccessListener(aVoid -> {
                showLoading(false);
                Toast.makeText(CreatePostActivity.this, "게시물이 성공적으로 수정되었습니다.", Toast.LENGTH_SHORT).show();
                 // 태그 사용 횟수 업데이트 (기존 태그와 새 태그 구분 필요)
                updateTagUsageForUpdatedPost(currentEditingPost.getTags(), tags);
                setResult(Activity.RESULT_OK); // PostDetailActivity에 변경 알림
                finish();
            })
            .addOnFailureListener(e -> {
                showLoading(false);
                Toast.makeText(CreatePostActivity.this, "게시물 수정 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
    
    // Tag 객체를 Firestore에 저장하기 위한 Map으로 변환하는 메소드 (Tag 모델 내부에 두는 것이 더 적절)
    // private Map<String, Object> tagToMap(Tag tag) { ... }
    // 혹은 Tag 클래스에 toMap() 메소드 구현 필요
    
    // 게시물 업데이트 시 태그 사용량 변경 처리 (단순화된 버전)
    private void updateTagUsageForUpdatedPost(List<Tag> oldTags, List<Tag> newTags) {
        // 기존 태그 목록과 새 태그 목록을 비교하여 변경된 태그에 대해 사용 횟수 조정
        // (이 부분은 TagRepository와 상의하여 더 정교하게 구현 필요)
        // 예: 삭제된 태그는 사용횟수 감소, 새로 추가된 태그는 사용횟수 증가 등
        
        // 여기서는 단순하게 새 태그 목록 기준으로 사용 횟수 증가 및 마지막 사용일 업데이트
        for (Tag tag : newTags) {
            if (tag.getTagId() != null) {
                tagRepository.incrementTagUseCount(tag.getTagId());
                tagRepository.updateTagLastUsed(tag.getTagId());
            }
        }
    }

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnPost.setEnabled(!isLoading);
        binding.btnAddImage.setEnabled(!isLoading);
        binding.etCaption.setEnabled(!isLoading);
    }
    
    /**
     * 태그 삭제를 확인하는 다이얼로그를 표시합니다.
     */
    private void confirmTagDelete(Tag tag, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.delete_tag);
        builder.setMessage(R.string.confirm_delete_tag);
        builder.setPositiveButton(R.string.yes, (dialog, which) -> {
            deleteTag(position);
        });
        builder.setNegativeButton(R.string.no, null);
        builder.show();
    }
    
    /**
     * 지정된 위치의 태그를 삭제합니다.
     */
    private void deleteTag(int position) {
        if (position >= 0 && position < addedTags.size()) {
            addedTags.remove(position);
            addedTagsAdapter.notifyDataSetChanged();
        }
    }
    
    /**
     * 기존 태그를 새 태그로 업데이트합니다.
     */
    private void updateTag(Tag oldTag, Tag newTag) {
        // 기존 태그 찾기
        int index = -1;
        for (int i = 0; i < addedTags.size(); i++) {
            if (addedTags.get(i).getTagId().equals(oldTag.getTagId())) {
                index = i;
                break;
            }
        }
        
        // 태그를 찾은 경우 업데이트
        if (index >= 0) {
            addedTags.set(index, newTag);
            addedTagsAdapter.notifyItemChanged(index);
        } else {
            // 기존 태그가 추천 태그에 있는 경우, 추가
            for (int i = 0; i < suggestedTags.size(); i++) {
                if (suggestedTags.get(i).getTagId().equals(oldTag.getTagId())) {
                    suggestedTags.remove(i);
                    suggestedTagsAdapter.notifyItemRemoved(i);
                    addTag(newTag);
                    break;
                }
            }
        }
    }
    
    /**
     * 현재 사용자를 위해 태그를 저장합니다.
     */
    private void saveTagForCurrentUser(Tag tag) {
        FirebaseUser currentUser = userRepository.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다", Toast.LENGTH_SHORT).show();
            return;
        }

        // 디버그 정보
        String userId = currentUser.getUid();
        String tagId = tag.getTagId();
        android.util.Log.d("CreatePostActivity", "태그 저장 시도 - 사용자 ID: " + userId + ", 태그 ID: " + tagId);

        tagRepository.saveTagForUser(userId, tagId)
            .addOnSuccessListener(aVoid -> {
                android.util.Log.d("CreatePostActivity", "태그 저장 성공 - 태그 ID: " + tagId);
                Toast.makeText(this, "태그가 저장되었습니다", Toast.LENGTH_SHORT).show();
                
                // 태그 사용 횟수 증가
                tagRepository.incrementTagUseCount(tagId);
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("CreatePostActivity", "태그 저장 실패 - 태그 ID: " + tagId + ", 오류: " + e.getMessage());
                Toast.makeText(this, "태그 저장 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }

    private void setupToolbarTitle() {
        if (editingPostId != null) {
            binding.toolbar.setTitle(R.string.edit_post); // strings.xml에 edit_post 추가 필요
        } else {
            binding.toolbar.setTitle(R.string.create_post);
        }
    }

    private void loadPostForEditing(String postId) {
        showLoading(true);
        postRepository.getPostById(postId).addOnCompleteListener(task -> {
            showLoading(false);
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                currentEditingPost = task.getResult().toObject(Post.class);
                if (currentEditingPost != null) {
                    populateUiWithPostData(currentEditingPost);
                }
            } else {
                Toast.makeText(this, "게시물을 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show();
                finish(); // 게시물 로드 실패 시 액티비티 종료
            }
        });
    }

    private void populateUiWithPostData(Post post) {
        // 이미지 로드
        if (post.getImageUrl() != null && !post.getImageUrl().isEmpty()) {
            selectedImageUri = Uri.parse(post.getImageUrl()); // 기존 이미지 Uri로 설정
            Glide.with(this).load(post.getImageUrl()).into(binding.ivPostImage);
            binding.btnAddImage.setVisibility(View.GONE);
            // 수정 모드에서는 자동 태그 추천을 하지 않거나, 사용자 선택에 따라 제공할 수 있음 (여기서는 생략)
        }

        // 캡션 설정
        binding.etCaption.setText(post.getCaption());

        // 태그 설정
        if (post.getTags() != null) {
            addedTags.clear();
            addedTags.addAll(post.getTags());
            addedTagsAdapter.notifyDataSetChanged();
        }
        updateTagsVisibility();
    }

    private void updateTagsVisibility() {
        // 추가된 태그가 있는지 확인하여 태그 관련 UI 업데이트
        if (addedTags.isEmpty()) {
            binding.tvAiSuggestedTags.setVisibility(View.GONE);
            binding.recyclerSuggestedTags.setVisibility(View.GONE);
        } else {
            binding.tvAiSuggestedTags.setVisibility(View.VISIBLE);
            binding.recyclerSuggestedTags.setVisibility(View.VISIBLE);
        }
    }

    // 추가된 태그를 편집하기 위한 별도의 메서드 (Tag, int 시그니처)
    // 이전에 삭제되었거나 잘못 수정되었다면 여기에 전체 내용을 다시 삽입합니다.
    private void showTagEditDialog(Tag tagToEdit, int position) {
        if (tagToEdit == null || position < 0 || position >= addedTags.size()) {
            Toast.makeText(this, "잘못된 태그 정보입니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_tag, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        String tagType = tagToEdit.getTagType();
        String tagTypeTitle = "";
        if (Tag.TYPE_LOCATION.equals(tagType)) {
            tagTypeTitle = getString(R.string.tag_type_location);
        } else if (Tag.TYPE_PRODUCT.equals(tagType)) {
            tagTypeTitle = getString(R.string.tag_type_product);
        } else if (Tag.TYPE_BRAND.equals(tagType)) {
            tagTypeTitle = getString(R.string.tag_type_brand);
        } else if (Tag.TYPE_PRICE.equals(tagType)) {
            tagTypeTitle = getString(R.string.tag_type_price);
        } else if (Tag.TYPE_EVENT.equals(tagType)) {
            tagTypeTitle = getString(R.string.tag_type_event);
        } else {
            tagTypeTitle = "태그"; // 기본값 또는 적절한 문자열 리소스 사용
        }
        builder.setTitle(tagTypeTitle + " 태그 수정");

        EditText etTagName = dialogView.findViewById(R.id.et_tag_name);
        EditText etTagDescription = dialogView.findViewById(R.id.et_tag_description);

        etTagName.setText(tagToEdit.getName());
        if (tagToEdit.getDescription() != null) {
            etTagDescription.setText(tagToEdit.getDescription());
        }

        ViewGroup fieldContainer = dialogView.findViewById(R.id.container_tag_fields);
        fieldContainer.removeAllViews();
        View additionalFieldsView = null;

        switch (tagType) {
            case Tag.TYPE_LOCATION:
                additionalFieldsView = getLayoutInflater().inflate(R.layout.layout_location_fields, fieldContainer, false);
                fieldContainer.addView(additionalFieldsView);
                Button btnGetLocation = additionalFieldsView.findViewById(R.id.btn_get_current_location);
                btnGetLocation.setOnClickListener(v -> getCurrentLocation(dialogView));
                break;
            case Tag.TYPE_PRODUCT:
                additionalFieldsView = getLayoutInflater().inflate(R.layout.layout_product_fields, fieldContainer, false);
                fieldContainer.addView(additionalFieldsView);
                break;
            case Tag.TYPE_BRAND:
                additionalFieldsView = getLayoutInflater().inflate(R.layout.layout_brand_fields, fieldContainer, false);
                fieldContainer.addView(additionalFieldsView);
                break;
            case Tag.TYPE_PRICE:
                additionalFieldsView = getLayoutInflater().inflate(R.layout.layout_price_fields, fieldContainer, false);
                fieldContainer.addView(additionalFieldsView);
                break;
            case Tag.TYPE_EVENT:
                additionalFieldsView = getLayoutInflater().inflate(R.layout.layout_event_fields, fieldContainer, false);
                fieldContainer.addView(additionalFieldsView);
                setupDatePickers(dialogView);
                break;
        }
        if (additionalFieldsView != null) {
            fillAdditionalFields(tagToEdit, additionalFieldsView, tagType);
        }

        builder.setView(dialogView);
        builder.setPositiveButton("수정", (dialog, which) -> {
            try {
                String name = etTagName.getText().toString().trim();
                String description = etTagDescription.getText().toString().trim();

                if (name.isEmpty()) {
                    etTagName.setError(getString(R.string.error_required_field));
                    return;
                }

                String tagId = tagToEdit.getTagId(); 
                Tag updatedTag;

                switch (tagType) {
                    case Tag.TYPE_LOCATION:
                        updatedTag = createLocationTag(tagId, name, description, dialogView);
                        break;
                    case Tag.TYPE_PRODUCT:
                        updatedTag = createProductTag(tagId, name, description, dialogView);
                        break;
                    case Tag.TYPE_BRAND:
                        updatedTag = createBrandTag(tagId, name, description, dialogView);
                        break;
                    case Tag.TYPE_PRICE:
                        updatedTag = createPriceTag(tagId, name, description, dialogView);
                        break;
                    case Tag.TYPE_EVENT:
                        updatedTag = createEventTag(tagId, name, description, dialogView);
                        break;
                    default: // 일반 태그 또는 정의되지 않은 타입
                        updatedTag = new Tag(tagId, name, description, tagType);                    
                        break;
                }
                
                // 기존 태그의 주요 속성들 복사 (create<Type>Tag 가 모든 필드를 설정하지 않을 수 있으므로)
                updatedTag.setCreatorId(tagToEdit.getCreatorId());
                updatedTag.setUseCount(tagToEdit.getUseCount());
                updatedTag.setLastUsed(tagToEdit.getLastUsed());
                updatedTag.setRelatedPostsCount(tagToEdit.getRelatedPostsCount());
                updatedTag.setTrendingScore(tagToEdit.getTrendingScore());
                // TagData는 create<Type>Tag 메서드들이 이미 설정되었으므로, 여기서는 기본 타입일 경우에만 복사하거나,
                // 혹은 create<Type>Tag가 반환한 Tag 객체의 tagData를 유지하도록 주의해야 합니다.
                // 가장 안전한 방법은 create<Type>Tag가 반환한 객체의 tagData를 사용하고, 그 외 필드만 여기서 설정하는 것입니다.
                // 아래는 tagToEdit의 tagData를 덮어쓰는 방식이므로, 만약 create<Type>Tag가 특정 필드를 tagData에 기록했다면 유실될 수 있습니다.
                // 필요하다면 이 부분을 수정해야 합니다. (예: updatedTag.setTagData(create<Type>Tag(...).getTagData());)
                // 여기서는 기존처럼 tagToEdit의 것을 복사하되, create<Type>Tag가 반환한 tagData를 기본으로 하고, 필요한 값만 추가/수정하는 것이 좋을 수 있습니다.
                // 여기서는 create<Type>Tag가 반환한 tagData를 유지한다고 가정하고 아래 라인 주석 처리 또는 삭제합니다.
            } catch (IllegalArgumentException e) {
                Toast.makeText(CreatePostActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(CreatePostActivity.this, 
                        "태그 처리 중 오류가 발생했습니다: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("취소", null);
        builder.show();
    }
} 