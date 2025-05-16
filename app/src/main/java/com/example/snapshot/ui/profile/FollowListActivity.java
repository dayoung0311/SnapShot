package com.example.snapshot.ui.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.snapshot.R;
import com.example.snapshot.databinding.ActivityFollowListBinding;
import com.example.snapshot.model.User;
import com.example.snapshot.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FollowListActivity extends AppCompatActivity {

    public static final String EXTRA_USER_ID = "extra_user_id";
    public static final String EXTRA_LIST_TYPE = "extra_list_type";
    public static final String TYPE_FOLLOWERS = "followers";
    public static final String TYPE_FOLLOWING = "following";
    
    private ActivityFollowListBinding binding;
    private UserRepository userRepository;
    private String userId;
    private String listType;
    
    private List<User> userList = new ArrayList<>();
    private UserAdapter adapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFollowListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 저장소 초기화
        userRepository = UserRepository.getInstance();
        
        // 인텐트에서 데이터 가져오기
        userId = getIntent().getStringExtra(EXTRA_USER_ID);
        listType = getIntent().getStringExtra(EXTRA_LIST_TYPE);
        
        if (userId == null || listType == null) {
            Toast.makeText(this, "잘못된 접근입니다", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // 툴바 설정
        setupToolbar();
        
        // 리사이클러뷰 설정
        setupRecyclerView();
        
        // 사용자 목록 로드
        loadUsers();
    }
    
    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(listType.equals(TYPE_FOLLOWERS) ? 
                    R.string.followers_list : R.string.following_list);
        }
        
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }
    
    private void setupRecyclerView() {
        adapter = new UserAdapter(this, userList, true);
        binding.recyclerUsers.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerUsers.setAdapter(adapter);
    }
    
    private void loadUsers() {
        showLoading(true);
        
        if (listType.equals(TYPE_FOLLOWERS)) {
            loadFollowers();
        } else if (listType.equals(TYPE_FOLLOWING)) {
            loadFollowing();
        }
    }
    
    private void loadFollowers() {
        userRepository.getUserById(userId)
                .addOnSuccessListener(documentSnapshot -> {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null && user.getFollowers() != null && !user.getFollowers().isEmpty()) {
                        loadUserData(user.getFollowers());
                    } else {
                        // 팔로워가 없는 경우
                        showEmptyView();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "사용자 정보를 로드하는 중 오류가 발생했습니다: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                });
    }
    
    private void loadFollowing() {
        userRepository.getUserById(userId)
                .addOnSuccessListener(documentSnapshot -> {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null && user.getFollowing() != null && !user.getFollowing().isEmpty()) {
                        loadUserData(user.getFollowing());
                    } else {
                        // 팔로잉이 없는 경우
                        showEmptyView();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "사용자 정보를 로드하는 중 오류가 발생했습니다: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                });
    }
    
    private void loadUserData(List<String> userIds) {
        userList.clear();
        adapter.notifyDataSetChanged(); // 먼저 기존 목록을 비우고 UI에 반영
        
        if (userIds == null || userIds.isEmpty()) {
            showEmptyView();
            return;
        }
        
        showLoading(true);
        
        userRepository.getUsersByIds(userIds)
            .addOnSuccessListener(users -> {
                userList.addAll(users);
                adapter.notifyDataSetChanged();
                
                if (userList.isEmpty()) {
                    showEmptyView();
                } else {
                    binding.tvEmpty.setVisibility(View.GONE);
                    binding.recyclerUsers.setVisibility(View.VISIBLE);
                }
                showLoading(false);
            })
            .addOnFailureListener(e -> {
                showLoading(false);
                showEmptyView(); // 오류 발생 시에도 빈 화면 처리
                Toast.makeText(this, "사용자 목록을 불러오는 중 오류가 발생했습니다: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
            });
    }
    
    private void showEmptyView() {
        showLoading(false);
        binding.tvEmpty.setVisibility(View.VISIBLE);
        binding.recyclerUsers.setVisibility(View.GONE);
    }
    
    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.recyclerUsers.setVisibility(isLoading ? View.GONE : 
                (userList.isEmpty() ? View.GONE : View.VISIBLE));
        binding.tvEmpty.setVisibility(isLoading || !userList.isEmpty() ? View.GONE : View.VISIBLE);
    }
} 