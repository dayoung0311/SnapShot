package com.example.snapshot.ui.profile;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.snapshot.R;
import com.example.snapshot.databinding.FragmentProfileBinding;
import com.example.snapshot.model.Post;
import com.example.snapshot.model.Tag;
import com.example.snapshot.model.User;
import com.example.snapshot.repository.PostRepository;
import com.example.snapshot.repository.TagRepository;
import com.example.snapshot.repository.UserRepository;
import com.example.snapshot.ui.auth.LoginActivity;
import com.example.snapshot.ui.home.TagAdapter;
import com.example.snapshot.ui.profile.FollowListActivity;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private UserRepository userRepository;
    private PostRepository postRepository;
    private TagRepository tagRepository;
    
    private List<Post> postList = new ArrayList<>();
    private List<Tag> savedTagList = new ArrayList<>();
    private User currentUser;
    
    private ProfilePostAdapter postAdapter;
    private TagAdapter tagAdapter;
    
    // 프로필 편집 결과를 처리하기 위한 ActivityResultLauncher
    private ActivityResultLauncher<Intent> editProfileLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ActivityResultLauncher 초기화
        editProfileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // 프로필이 성공적으로 편집되었으면 사용자 데이터 새로고침
                        FirebaseUser firebaseUser = userRepository.getCurrentUser();
                        if (firebaseUser != null) {
                            loadUserData(firebaseUser.getUid());
                        }
                    }
                }
        );
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 저장소 초기화
        userRepository = UserRepository.getInstance();
        postRepository = PostRepository.getInstance();
        tagRepository = TagRepository.getInstance();
        
        // 현재 로그인된 사용자 확인
        FirebaseUser firebaseUser = userRepository.getCurrentUser();
        if (firebaseUser == null) {
            navigateToLogin();
            return;
        }
        
        // 리사이클러뷰 초기화
        setupRecyclerViews();
        
        // 이벤트 리스너 설정
        setupListeners();
        
        // 사용자 데이터 로드
        loadUserData(firebaseUser.getUid());
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        // 현재 로그인된 사용자 확인 및 데이터 새로고침
        // FirebaseUser firebaseUser = userRepository.getCurrentUser();
        // if (firebaseUser != null) {
            // 태그 목록만 새로고침
            // loadSavedTags(firebaseUser.getUid()); // 중복 호출 가능성 제거
        // }
    }
    
    private void setupRecyclerViews() {
        // 게시물 어댑터 설정
        postAdapter = new ProfilePostAdapter(requireContext(), postList);
        binding.recyclerPosts.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        binding.recyclerPosts.setAdapter(postAdapter);
        
        // 저장된 태그 어댑터 설정
        tagAdapter = new TagAdapter(requireContext(), savedTagList, true);
        binding.recyclerSavedTags.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.recyclerSavedTags.setAdapter(tagAdapter);
        
        // 태그 클릭 리스너 설정
        tagAdapter.setOnTagClickListener(position -> {
            if (position >= 0 && position < savedTagList.size()) {
                Tag tag = savedTagList.get(position);
                navigateToTagDetail(tag.getTagId());
            }
        });
    }
    
    private void setupListeners() {
        // 프로필 편집 버튼 클릭 리스너
        binding.btnEditProfile.setOnClickListener(v -> {
            // EditProfileActivity 시작
            Intent intent = new Intent(requireContext(), EditProfileActivity.class);
            editProfileLauncher.launch(intent);
        });
        
        // 설정 버튼 클릭 리스너
        binding.btnSettings.setOnClickListener(v -> {
            showSettingsOptions();
        });
        
        // 팔로워 클릭
        binding.tvFollowerCount.setOnClickListener(v -> {
            if (currentUser != null) {
                Intent intent = new Intent(requireContext(), FollowListActivity.class);
                intent.putExtra(FollowListActivity.EXTRA_USER_ID, currentUser.getUserId());
                intent.putExtra(FollowListActivity.EXTRA_LIST_TYPE, FollowListActivity.TYPE_FOLLOWERS);
                startActivity(intent);
            }
        });
        
        // 팔로잉 클릭
        binding.tvFollowingCount.setOnClickListener(v -> {
            if (currentUser != null) {
                Intent intent = new Intent(requireContext(), FollowListActivity.class);
                intent.putExtra(FollowListActivity.EXTRA_USER_ID, currentUser.getUserId());
                intent.putExtra(FollowListActivity.EXTRA_LIST_TYPE, FollowListActivity.TYPE_FOLLOWING);
                startActivity(intent);
            }
        });
    }
    
    private void loadUserData(String userId) {
        showLoading(true);
        
        userRepository.getUserById(userId)
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentUser = documentSnapshot.toObject(User.class);
                        if (currentUser != null) {
                            updateUI(currentUser);
                            loadUserPosts(userId);
                            loadSavedTags(userId);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(requireContext(), "사용자 정보를 불러오는 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
                });
    }
    
    private void updateUI(User user) {
        // 사용자 이름 설정
        binding.tvUsername.setText(user.getUsername());
        
        // 프로필 이미지 설정
        if (user.getProfilePicUrl() != null && !user.getProfilePicUrl().isEmpty()) {
            Glide.with(this)
                    .load(user.getProfilePicUrl())
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .into(binding.ivProfile);
        } else {
            binding.ivProfile.setImageResource(R.drawable.ic_profile_placeholder);
        }
        
        // 게시물 수, 팔로워 수, 팔로잉 수 설정
        binding.tvPostCount.setText(String.valueOf(0)); // 포스트 수는 나중에 업데이트
        binding.tvFollowerCount.setText(String.valueOf(user.getFollowerCount()));
        binding.tvFollowingCount.setText(String.valueOf(user.getFollowingCount()));
        
        // 자기소개 설정
        if (user.getBio() != null && !user.getBio().isEmpty()) {
            binding.tvBio.setText(user.getBio());
            binding.tvBio.setVisibility(View.VISIBLE);
        } else {
            binding.tvBio.setVisibility(View.GONE);
        }
    }
    
    private void loadUserPosts(String userId) {
        postRepository.getPostsByUser(userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    postList.clear();
                    
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        Post post = document.toObject(Post.class);
                        if (post != null) {
                            postList.add(post);
                        }
                    }
                    
                    // 바인딩 널 체크 추가
                    if (binding != null) {
                        // 게시물 수 업데이트
                        binding.tvPostCount.setText(String.valueOf(postList.size()));
                        
                        // 어댑터 갱신
                        postAdapter.notifyDataSetChanged();
                        
                        // 로딩 표시 숨기기
                        showLoading(false);
                    }
                })
                .addOnFailureListener(e -> {
                    if (binding != null) {
                        // 로딩 표시 숨기기
                        showLoading(false);
                        
                        // 오류 메시지 표시
                        Toast.makeText(requireContext(), "게시물을 불러오는 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    /**
     * 사용자가 저장한 태그 목록을 로드합니다.
     */
    private void loadSavedTags(String userId) {
        android.util.Log.d("ProfileFragment", "저장된 태그 로드 시작 - 사용자 ID: " + userId);
        
        // 로딩 표시
        if (binding != null) {
            binding.progressBarTags.setVisibility(View.VISIBLE);
            binding.tvNoSavedTags.setVisibility(View.GONE);
        }
        
        // 기존 목록 초기화
        savedTagList.clear();
        tagAdapter.notifyDataSetChanged();
        
        tagRepository.getSavedTagsByUser(userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<String> savedTagIds = new ArrayList<>();
                    
                    // saved_tags 컬렉션에서 태그 ID만 추출
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        String tagId = document.getString("tagId");
                        if (tagId != null) {
                            savedTagIds.add(tagId);
                        }
                    }
                    
                    android.util.Log.d("ProfileFragment", "저장된 태그 ID 조회 결과 - 태그 개수: " + savedTagIds.size());
                    
                    if (savedTagIds.isEmpty()) {
                        // 저장된 태그가 없는 경우
                        if (binding != null) {
                            android.util.Log.d("ProfileFragment", "저장된 태그가 없음");
                            updateSavedTagsUI();
                            binding.progressBarTags.setVisibility(View.GONE);
                        }
                    } else {
                        // 저장된 태그 ID로 태그 정보 가져오기
                        loadTagsByIds(savedTagIds);
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("ProfileFragment", "저장된 태그 목록 조회 실패: " + e.getMessage());
                    if (binding != null) {
                        binding.progressBarTags.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), "저장된 태그를 불러오는 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
    
    /**
     * 태그 ID 목록을 통해 태그 정보를 로드합니다.
     */
    private void loadTagsByIds(List<String> tagIds) {
        android.util.Log.d("ProfileFragment", "태그 정보 로드 시작 - 태그 ID 개수: " + tagIds.size());
        
        if (tagIds.isEmpty()) {
            if (binding != null) {
                updateSavedTagsUI();
                binding.progressBarTags.setVisibility(View.GONE);
            }
            return;
        }
        
        // 기존 태그 목록 초기화 (loadSavedTags에서 이미 처리했으므로 여기서 clear하지 않음)
        // savedTagList.clear(); 
        
        // 모든 태그 IDs가 10개 이하일 경우 한 번에 조회
        if (tagIds.size() <= 10) {
            android.util.Log.d("ProfileFragment", "태그 일괄 조회 - 태그 ID 개수: " + tagIds.size());
            tagRepository.getTagsByIds(tagIds)
                .addOnSuccessListener(querySnapshot -> {
                    int count = 0;
                    for (DocumentSnapshot document : querySnapshot) {
                        Tag tag = document.toObject(Tag.class);
                        if (tag != null) {
                            // 중복 추가 방지
                            boolean exists = false;
                            for (Tag existingTag : savedTagList) {
                                if (existingTag.getTagId().equals(tag.getTagId())) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                savedTagList.add(tag);
                                count++;
                            }
                        }
                    }
                    
                    android.util.Log.d("ProfileFragment", "태그 정보 로드 완료 - 로드된 태그 개수: " + count);
                    
                    if (binding != null) {
                        // 태그가 추가된 후 어댑터 갱신
                        tagAdapter.notifyDataSetChanged();
                        updateSavedTagsUI();
                        binding.progressBarTags.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("ProfileFragment", "태그 정보 로드 실패: " + e.getMessage());
                    if (binding != null) {
                        Toast.makeText(requireContext(), "태그 정보를 불러오는 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        updateSavedTagsUI();
                        binding.progressBarTags.setVisibility(View.GONE);
                    }
                });
            return;
        }
        
        // Firebase에는 whereIn 쿼리에 최대 10개의 값만 허용되므로 필요한 경우 분할하여 처리
        final int batchSize = 10;
        int batchCount = (tagIds.size() + batchSize - 1) / batchSize; // 올림 나눗셈
        final int[] completedBatches = {0};
        
        android.util.Log.d("ProfileFragment", "태그 배치 처리 - 배치 수: " + batchCount);
        
        for (int i = 0; i < batchCount; i++) {
            int startIndex = i * batchSize;
            int endIndex = Math.min((i + 1) * batchSize, tagIds.size());
            List<String> batchIds = tagIds.subList(startIndex, endIndex);
            
            android.util.Log.d("ProfileFragment", "배치 " + (i+1) + " 처리 시작 - 태그 ID 개수: " + batchIds.size());
            
            tagRepository.getTagsByIds(batchIds)
                    .addOnSuccessListener(querySnapshot -> {
                        completedBatches[0]++;
                        int count = 0;
                        
                        for (DocumentSnapshot document : querySnapshot) {
                            Tag tag = document.toObject(Tag.class);
                            if (tag != null) {
                                // 중복 추가 방지
                                boolean exists = false;
                                for (Tag existingTag : savedTagList) {
                                    if (existingTag.getTagId().equals(tag.getTagId())) {
                                        exists = true;
                                        break;
                                    }
                                }
                                if (!exists) {
                                    savedTagList.add(tag);
                                    count++;
                                }
                            }
                        }
                        
                        android.util.Log.d("ProfileFragment", "배치 " + completedBatches[0] + "/" + batchCount + " 완료 - 로드된 태그 개수: " + count);
                        
                        if (binding != null && completedBatches[0] >= batchCount) {
                            // 모든 배치 완료 시 UI 업데이트
                            android.util.Log.d("ProfileFragment", "모든 배치 처리 완료 - 총 로드된 태그 개수: " + savedTagList.size());
                            tagAdapter.notifyDataSetChanged();
                            updateSavedTagsUI();
                            binding.progressBarTags.setVisibility(View.GONE);
                        }
                    })
                    .addOnFailureListener(e -> {
                        completedBatches[0]++;
                        android.util.Log.e("ProfileFragment", "배치 " + completedBatches[0] + "/" + batchCount + " 처리 실패: " + e.getMessage());
                        
                        if (binding != null) {
                            Toast.makeText(requireContext(), "태그 정보를 불러오는 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            
                            if (completedBatches[0] >= batchCount) {
                                // 모든 배치 완료 시 UI 업데이트
                                tagAdapter.notifyDataSetChanged();
                                updateSavedTagsUI();
                                binding.progressBarTags.setVisibility(View.GONE);
                            }
                        }
                    });
        }
    }
    
    /**
     * 저장된 태그 UI 업데이트
     */
    private void updateSavedTagsUI() {
        if (binding == null) {
            android.util.Log.w("ProfileFragment", "updateSavedTagsUI: binding이 null입니다.");
            return;
        }
        
        android.util.Log.d("ProfileFragment", "저장된 태그 UI 갱신 - 태그 개수: " + savedTagList.size());
        
        if (savedTagList.isEmpty()) {
            binding.tvNoSavedTags.setVisibility(View.VISIBLE);
            binding.recyclerSavedTags.setVisibility(View.GONE);
            android.util.Log.d("ProfileFragment", "저장된 태그 없음 UI 표시");
        } else {
            binding.tvNoSavedTags.setVisibility(View.GONE);
            binding.recyclerSavedTags.setVisibility(View.VISIBLE);
            android.util.Log.d("ProfileFragment", "저장된 태그 목록 UI 표시");
            
            // RecyclerView 레이아웃 다시 측정하여 강제 갱신
            binding.recyclerSavedTags.post(() -> {
                tagAdapter.notifyDataSetChanged();
                binding.recyclerSavedTags.requestLayout();
                binding.recyclerSavedTags.invalidate();
            });
        }
    }
    
    /**
     * 태그 상세 화면으로 이동
     */
    private void navigateToTagDetail(String tagId) {
        Intent intent = new Intent(requireContext(), com.example.snapshot.ui.tag.TagDetailActivity.class);
        intent.putExtra(com.example.snapshot.ui.tag.TagDetailActivity.EXTRA_TAG_ID, tagId);
        startActivity(intent);
    }
    
    private void showSettingsOptions() {
        // TODO: 설정 메뉴 표시 (로그아웃 등)
        // 임시로 로그아웃 기능만 구현
        userRepository.logoutUser();
        navigateToLogin();
    }
    
    private void navigateToLogin() {
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }
    
    private void showLoading(boolean isLoading) {
        if (binding == null) return;
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
