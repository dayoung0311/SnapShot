package com.example.snapshot.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.RadioGroup;
import android.widget.EditText;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.snapshot.R;
import com.example.snapshot.databinding.FragmentHomeBinding;
import com.example.snapshot.model.Post;
import com.example.snapshot.model.Tag;
import com.example.snapshot.model.User;
import com.example.snapshot.repository.PostRepository;
import com.example.snapshot.repository.ReportRepository;
import com.example.snapshot.repository.UserRepository;
import com.example.snapshot.repository.TagRepository;
import com.example.snapshot.ui.post.CommentActivity;
import com.example.snapshot.ui.profile.ProfileActivity;
import com.example.snapshot.ui.tag.TagDetailActivity;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {
    
    private FragmentHomeBinding binding;
    private PostAdapter postAdapter;
    private PostRepository postRepository;
    private UserRepository userRepository;
    private TagRepository tagRepository;
    private ReportRepository reportRepository;
    
    private List<Post> postList = new ArrayList<>();
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 저장소 초기화
        postRepository = PostRepository.getInstance();
        userRepository = UserRepository.getInstance();
        tagRepository = TagRepository.getInstance();
        reportRepository = ReportRepository.getInstance();
        
        // 어댑터 초기화
        setupRecyclerView();
        
        // 스와이프 새로고침 설정
        binding.swipeRefreshLayout.setOnRefreshListener(this::loadPosts);
        
        // 초기 데이터 로드
        loadPosts();
    }
    
    private void setupRecyclerView() {
        postAdapter = new PostAdapter(postList, getContext());
        binding.recyclerPosts.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.recyclerPosts.setAdapter(postAdapter);
        
        // 포스트 상호작용 리스너 설정
        postAdapter.setOnPostInteractionListener(new PostAdapter.OnPostInteractionListener() {
            @Override
            public void onLikeClicked(int position) {
                toggleLike(position);
            }
            
            @Override
            public void onCommentClicked(int position) {
                navigateToComments(position);
            }
            
            @Override
            public void onShareClicked(int position) {
                sharePost(position);
            }
            
            @Override
            public void onTagClicked(int postPosition, int tagPosition) {
                navigateToTagDetails(postPosition, tagPosition);
            }
            
            @Override
            public void onUserProfileClicked(int position) {
                navigateToUserProfile(position);
            }
            
            @Override
            public void onReportClicked(int position) {
                showReportDialogForPost(position);
            }
        });
        
        // 수정: 새로운 OnPostTagSaveListener 사용
        postAdapter.setOnPostTagSaveListener(new PostAdapter.OnPostTagSaveListener() {
            @Override
            public void onPostTagSave(int postAdapterPosition, int tagAdapterPositionInPost) {
                saveTag(postAdapterPosition, tagAdapterPositionInPost);
            }
        });
    }
    
    private void loadPosts() {
        showLoading(true);
        
        FirebaseUser currentUser = userRepository.getCurrentUser();
        if (currentUser == null) {
            showLoading(false);
            return;
        }
        
        // 현재 사용자 정보 가져오기
        userRepository.getUserById(currentUser.getUid())
                .addOnSuccessListener(documentSnapshot -> {
                    User user = documentSnapshot.toObject(User.class);
                    if (user != null) {
                        // 팔로우 중인 사용자 목록 가져오기
                        List<String> followingList = user.getFollowing();
                        
                        // 팔로우 중인 사용자가 없다면 인기 게시물 표시
                        if (followingList.isEmpty()) {
                            loadPopularPosts();
                        } else {
                            // 팔로우 중인 사용자의 게시물 표시
                            loadFollowingPosts(followingList);
                        }
                    } else {
                        loadPopularPosts();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(getContext(), R.string.error_network, Toast.LENGTH_SHORT).show();
                });
    }
    
    private void loadFollowingPosts(List<String> followingList) {
        FirebaseUser currentUser = userRepository.getCurrentUser();
        if (currentUser != null && !followingList.contains(currentUser.getUid())) {
            followingList.add(currentUser.getUid());
        }

        // Firestore에서 제한된 사용자 목록을 비동기로 가져오기
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users")
            .whereEqualTo("restricted", true)
            .get()
            .addOnSuccessListener(snapshot -> {
                List<String> restrictedUsers = new ArrayList<>();
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    restrictedUsers.add(doc.getId());
                }
                // restrictedUsers를 활용해 게시물 쿼리 실행
                Query query = postRepository.getFilteredPostsForHomeFeed(followingList, restrictedUsers);
                query.get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            postList.clear();
                            for (DocumentSnapshot document : queryDocumentSnapshots) {
                                Post post = document.toObject(Post.class);
                                if (post != null && !restrictedUsers.contains(post.getUserId())) {
                                    postList.add(post);
                                }
                            }
                            if (postList.isEmpty()) {
                                showEmptyView(true);
                            } else {
                                showEmptyView(false);
                            }
                            postAdapter.notifyDataSetChanged();
                            showLoading(false);
                        })
                        .addOnFailureListener(e -> {
                            showLoading(false);
                            Toast.makeText(getContext(), R.string.error_network, Toast.LENGTH_SHORT).show();
                        });
            })
            .addOnFailureListener(e -> {
                // 제한된 사용자 목록 가져오기 실패 시 기존 방식으로 로드
                Query query = postRepository.getPostsForHomeFeed(followingList);
                loadPostsFromQuery(query);
            });
    }
    
    private void loadPopularPosts() {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users")
            .whereEqualTo("restricted", true)
            .get()
            .addOnSuccessListener(snapshot -> {
                List<String> restrictedUsers = new ArrayList<>();
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    restrictedUsers.add(doc.getId());
                }
                Query query = postRepository.getPopularPosts();
                query.get()
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            postList.clear();
                            for (DocumentSnapshot document : queryDocumentSnapshots) {
                                Post post = document.toObject(Post.class);
                                if (post != null && !restrictedUsers.contains(post.getUserId())) {
                                    postList.add(post);
                                }
                            }
                            if (postList.isEmpty()) {
                                showEmptyView(true);
                            } else {
                                showEmptyView(false);
                            }
                            postAdapter.notifyDataSetChanged();
                            showLoading(false);
                        })
                        .addOnFailureListener(e -> {
                            showLoading(false);
                            Toast.makeText(getContext(), R.string.error_network, Toast.LENGTH_SHORT).show();
                        });
            })
            .addOnFailureListener(e -> {
                Query query = postRepository.getPopularPosts();
                loadPostsFromQuery(query);
            });
    }
    
    /**
     * 주어진 쿼리로 게시물 로드 (기존 방식)
     */
    private void loadPostsFromQuery(Query query) {
        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    postList.clear();
                    
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        Post post = document.toObject(Post.class);
                        if (post != null) {
                            postList.add(post);
                        }
                    }
                    
                    if (postList.isEmpty()) {
                        showEmptyView(true);
                    } else {
                        showEmptyView(false);
                    }
                    
                    postAdapter.notifyDataSetChanged();
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(getContext(), R.string.error_network, Toast.LENGTH_SHORT).show();
                });
    }
    
    private void toggleLike(int position) {
        FirebaseUser currentUser = userRepository.getCurrentUser();
        if (currentUser == null || position >= postList.size()) return;
        
        Post post = postList.get(position);
        String postId = post.getPostId();
        String userId = currentUser.getUid();
        
        // 로컬 UI 즉시 업데이트 (선반영)
        boolean isLiked = post.getUserLikes().contains(userId);
        if (isLiked) {
                        post.getUserLikes().remove(userId);
            post.setLikeCount(post.getLikeCount() - 1);
        } else {
                        post.getUserLikes().add(userId);
            post.setLikeCount(post.getLikeCount() + 1);
        }
                        postAdapter.notifyItemChanged(position);
        
        // Firestore 업데이트
        postRepository.toggleLike(postId, userId)
            .addOnFailureListener(e -> {
                // 실패 시 UI 롤백
                if (isLiked) { // 원래 좋아요 상태였으므로 다시 추가
                    post.getUserLikes().add(userId);
                    post.setLikeCount(post.getLikeCount() + 1);
                } else { // 원래 좋아요 상태 아니었으므로 다시 제거
                    post.getUserLikes().remove(userId);
                    post.setLikeCount(post.getLikeCount() - 1);
                }
                postAdapter.notifyItemChanged(position);
                Toast.makeText(getContext(), "좋아요 처리에 실패했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
    
    private void navigateToComments(int position) {
        if (position < 0 || position >= postList.size()) {
            return;
        }
        
        Post post = postList.get(position);
        
        // 댓글 화면으로 이동
        Intent intent = new Intent(getContext(), CommentActivity.class);
        intent.putExtra(CommentActivity.EXTRA_POST_ID, post.getPostId());
        startActivity(intent);
    }
    
    private void sharePost(int position) {
        if (position < 0 || position >= postList.size()) {
            return;
        }
        
        Post post = postList.get(position);
        
        // 공유할 내용 생성
        StringBuilder shareContent = new StringBuilder();
        shareContent.append(post.getUserName()).append("님의 포스트\n\n");
        
        if (post.getCaption() != null && !post.getCaption().isEmpty()) {
            shareContent.append(post.getCaption()).append("\n\n");
        }
        
        // 태그 추가
        if (post.getTags() != null && !post.getTags().isEmpty()) {
            shareContent.append("태그: ");
            for (int i = 0; i < post.getTags().size(); i++) {
                Tag tag = post.getTags().get(i);
                shareContent.append("#").append(tag.getName());
                
                if (i < post.getTags().size() - 1) {
                    shareContent.append(", ");
                }
            }
            shareContent.append("\n\n");
        }
        
        // 앱 다운로드 안내 추가
        shareContent.append("SnapShot 앱에서 더 많은 콘텐츠를 확인하세요!");
        
        // 공유 인텐트 생성
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareContent.toString());
        
        // 공유 다이얼로그 표시
        startActivity(Intent.createChooser(shareIntent, "포스트 공유"));
    }
    
    private void navigateToTagDetails(int postPosition, int tagPosition) {
        if (postPosition < 0 || postPosition >= postList.size()) {
            return;
        }
        
        Post post = postList.get(postPosition);
        if (post.getTags() == null || tagPosition < 0 || tagPosition >= post.getTags().size()) {
            return;
        }
        
        Tag tag = post.getTags().get(tagPosition);
        // 태그 상세 화면으로 이동
        Intent intent = new Intent(getContext(), TagDetailActivity.class);
        intent.putExtra(TagDetailActivity.EXTRA_TAG_ID, tag.getTagId());
        startActivity(intent);
    }
    
    private void navigateToUserProfile(int position) {
        if (position < 0 || position >= postList.size()) {
            return;
        }
        
        Post post = postList.get(position);
        
        // 프로필 화면으로 이동
        Intent intent = new Intent(getContext(), ProfileActivity.class);
        intent.putExtra(ProfileActivity.EXTRA_USER_ID, post.getUserId());
        startActivity(intent);
    }
    
    private void saveTag(int postPosition, int tagPosition) {
        if (postPosition < 0 || postPosition >= postList.size()) {
            return;
        }
        Post post = postList.get(postPosition);
        if (post == null || post.getTags() == null || tagPosition < 0 || tagPosition >= post.getTags().size()) {
            return;
        }
        Tag tagToSave = post.getTags().get(tagPosition);
        
        FirebaseUser currentUser = userRepository.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(getContext(), R.string.login_required, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 태그 저장 로직 (TagRepository 사용)
        tagRepository.saveTagForUser(currentUser.getUid(), tagToSave.getTagId())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "'" + tagToSave.getName() + "' 태그가 저장되었습니다.", Toast.LENGTH_SHORT).show();
                    tagRepository.incrementTagUseCount(tagToSave.getTagId()); // 태그 사용 횟수 증가
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "태그 저장 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
    
    private void showLoading(boolean isLoading) {
        binding.swipeRefreshLayout.setRefreshing(isLoading);
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }
    
    private void showEmptyView(boolean isEmpty) {
        binding.tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.recyclerPosts.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }
    
    // 홈 피드에서 포스트 신고 다이얼로그 표시
    private void showReportDialogForPost(int position) {
        if (position < 0 || position >= postList.size()) return;
        Post post = postList.get(position);
        if (post == null) return;
        
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_report, null);
        builder.setView(dialogView);

        // 다이얼로그 제목 설정
        TextView titleText = dialogView.findViewById(R.id.title_report);
        titleText.setText(R.string.report_post_title);

        // 라디오 그룹 및 기타 이유 입력창 설정
        RadioGroup radioGroup = dialogView.findViewById(R.id.radio_group_report_reason);
        EditText customReasonEdit = dialogView.findViewById(R.id.edit_text_custom_reason);

        // '기타' 옵션 선택 시 입력창 표시
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_other) {
                customReasonEdit.setVisibility(View.VISIBLE);
            } else {
                customReasonEdit.setVisibility(View.GONE);
            }
        });

        builder.setPositiveButton(R.string.report_submit, (dialogInterface, i) -> {
            int selectedId = radioGroup.getCheckedRadioButtonId();
            if (selectedId == -1) {
                Toast.makeText(getContext(), R.string.report_reason, Toast.LENGTH_SHORT).show();
                return;
            }
            String reason;
            if (selectedId == R.id.radio_other) {
                reason = customReasonEdit.getText().toString().trim();
                if (reason.isEmpty()) {
                    customReasonEdit.setError(getString(R.string.error_empty_fields));
                    return;
                }
            } else {
                RadioButton selectedRadioButton = dialogView.findViewById(selectedId);
                reason = selectedRadioButton.getText().toString();
            }
            submitReportForPost(position, reason);
        });
        builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {});
        builder.create().show();
    }

    // 홈 피드에서 포스트 신고 처리
    private void submitReportForPost(int position, String reason) {
        FirebaseUser currentUser = userRepository.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(getContext(), R.string.login_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (position < 0 || position >= postList.size()) {
            Toast.makeText(getContext(), R.string.error_loading_post, Toast.LENGTH_SHORT).show();
            return;
        }
        Post post = postList.get(position);
        if (post == null) {
            Toast.makeText(getContext(), R.string.error_loading_post, Toast.LENGTH_SHORT).show();
            return;
        }
        String reporterId = currentUser.getUid();
        String postId = post.getPostId();
        // 신고 제출
        reportRepository.reportPost(reporterId, postId, reason)
            .addOnSuccessListener(aVoid -> {
                Toast.makeText(getContext(), R.string.report_success, Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e -> {
                if (e.getMessage() != null && e.getMessage().contains("이미 이 포스트를 신고하셨습니다")) {
                    Toast.makeText(getContext(), R.string.report_already_reported, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), getString(R.string.report_failed) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 