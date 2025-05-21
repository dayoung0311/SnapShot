package com.example.snapshot.ui.post;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.snapshot.R;
import com.example.snapshot.databinding.ActivityPostDetailBinding;
import com.example.snapshot.model.Post;
import com.example.snapshot.repository.PostRepository;
import com.example.snapshot.repository.ReportRepository;
import com.example.snapshot.repository.UserRepository;
import com.example.snapshot.ui.profile.ProfileActivity;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PostDetailActivity extends AppCompatActivity {
    
    public static final String EXTRA_POST_ID = "extra_post_id";
    
    private ActivityPostDetailBinding binding;
    private PostRepository postRepository;
    private UserRepository userRepository;
    private String postId;
    private Post currentPost;
    private boolean isCurrentUserPost = false;
    
    // 게시물 수정 결과를 처리하기 위한 ActivityResultLauncher
    private ActivityResultLauncher<Intent> editPostLauncher;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // ActivityResultLauncher 초기화
        editPostLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == AppCompatActivity.RESULT_OK) {
                        // 게시물이 성공적으로 수정되었으면 데이터 새로고침
                        loadPostData();
                    }
                }
        );
        
        // 뷰 바인딩 초기화
        binding = ActivityPostDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 저장소 초기화
        postRepository = PostRepository.getInstance();
        userRepository = UserRepository.getInstance();
        
        // 인텐트에서 게시물 ID 가져오기
        postId = getIntent().getStringExtra(EXTRA_POST_ID);
        if (postId == null) {
            Toast.makeText(this, R.string.error_loading_post, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // 툴바 설정
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.post_detail);
        }
        
        // 게시물 데이터 로드
        loadPostData();
        
        // 이벤트 리스너 설정
        setupListeners();
    }
    
    private void loadPostData() {
        // 로딩 표시
        showLoading(true);
        
        // 게시물 데이터 가져오기
        postRepository.getPostById(postId)
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    currentPost = documentSnapshot.toObject(Post.class);
                    if (currentPost != null) {
                        displayPostData(currentPost);
                        
                        // 현재 로그인한 사용자가 작성자인지 확인
                        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                        if (currentUser != null && currentPost.getUserId().equals(currentUser.getUid())) {
                            isCurrentUserPost = true;
                            invalidateOptionsMenu(); // 메뉴 업데이트
                        }
                    } else {
                        Toast.makeText(PostDetailActivity.this, R.string.error_loading_post, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } else {
                    Toast.makeText(PostDetailActivity.this, R.string.error_loading_post, Toast.LENGTH_SHORT).show();
                    finish();
                }
                showLoading(false);
            })
            .addOnFailureListener(e -> {
                Toast.makeText(PostDetailActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                finish();
                showLoading(false);
            });
    }
    
    private void displayPostData(Post post) {
        // 이미지 로드
        Glide.with(this)
                .load(post.getImageUrl())
                .placeholder(R.drawable.placeholder_image)
                .into(binding.ivPostImage);
        
        // 작성자 정보 로드
        userRepository.getUserById(post.getUserId())
            .addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    String username = documentSnapshot.getString("username");
                    String profileImageUrl = documentSnapshot.getString("profileImageUrl");
                    
                    if (username != null) {
                        binding.tvUsername.setText(username);
                    }
                    
                    if (profileImageUrl != null) {
                        Glide.with(PostDetailActivity.this)
                                .load(profileImageUrl)
                                .placeholder(R.drawable.default_profile)
                                .circleCrop()
                                .into(binding.ivProfileImage);
                    }
                }
            });
        
        // 게시물 내용 설정
        binding.tvCaption.setText(post.getCaption());
        
        // 타임스탬프 형식 변환
        Timestamp timestamp = post.getCreationDate();
        if (timestamp != null) {
            Date date = timestamp.toDate();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            binding.tvTimestamp.setText(sdf.format(date));
        }
        
        // 댓글 수 및 좋아요 수 설정
        binding.tvLikesCount.setText(String.valueOf(post.getLikeCount()));
        binding.tvCommentsCount.setText(String.valueOf(post.getCommentCount()));
        
        // 좋아요 상태 UI 업데이트
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        updateLikeButtonUI(currentUser != null && post.getUserLikes().contains(currentUser.getUid()));
    }
    
    // 좋아요 버튼 UI 업데이트 메소드 추가
    private void updateLikeButtonUI(boolean isLiked) {
        if (isLiked) {
            binding.btnLike.setImageResource(R.drawable.ic_like_filled);
            binding.btnLike.setColorFilter(ContextCompat.getColor(this, R.color.error));
        } else {
            binding.btnLike.setImageResource(R.drawable.ic_like);
            binding.btnLike.setColorFilter(ContextCompat.getColor(this, R.color.black)); // 기본 색상 (예: 검은색)
        }
        // 좋아요 수 업데이트 (currentPost가 null이 아닐 때만)
        if (currentPost != null) {
            binding.tvLikesCount.setText(String.valueOf(currentPost.getLikeCount()));
        }
    }
    
    private void setupListeners() {
        // 좋아요 버튼 클릭
        binding.btnLike.setOnClickListener(v -> toggleLike());
        
        // 댓글 버튼 클릭
        binding.btnComment.setOnClickListener(v -> {
            Intent intent = new Intent(this, CommentActivity.class);
            intent.putExtra(CommentActivity.EXTRA_POST_ID, postId);
            startActivity(intent);
        });
        
        // 프로필 클릭
        binding.layoutUserInfo.setOnClickListener(v -> {
            if (currentPost != null) {
                Intent intent = new Intent(this, ProfileActivity.class);
                intent.putExtra(ProfileActivity.EXTRA_USER_ID, currentPost.getUserId());
                startActivity(intent);
            }
        });
        
        // 공유 버튼 클릭
        binding.btnShare.setOnClickListener(v -> sharePost());
    }
    
    // 좋아요 토글 메소드 추가
    private void toggleLike() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, R.string.login_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentPost == null) return;
        
        String userId = currentUser.getUid();
        boolean isCurrentlyLiked = currentPost.getUserLikes().contains(userId);
        
        // Firestore 업데이트 요청
        postRepository.toggleLike(postId, userId)
            .addOnSuccessListener(aVoid -> {
                // 로컬 Post 객체 업데이트 및 UI 갱신
                if (isCurrentlyLiked) {
                    currentPost.getUserLikes().remove(userId);
                    currentPost.setLikeCount(currentPost.getLikeCount() - 1);
                } else {
                    currentPost.getUserLikes().add(userId);
                    currentPost.setLikeCount(currentPost.getLikeCount() + 1);
                }
                updateLikeButtonUI(!isCurrentlyLiked);
            })
            .addOnFailureListener(e -> {
                Toast.makeText(this, "좋아요 처리 중 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
    
    // 공유 메소드 추가
    private void sharePost() {
        if (currentPost == null) return;
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        String shareBody = getString(R.string.share_post_text, currentPost.getCaption(), currentPost.getImageUrl()); // 예시 텍스트
        String shareSubject = getString(R.string.share_post_subject, currentPost.getUserName()); // 예시 제목
        
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, shareSubject);
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
        
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)));
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 메뉴 표시
        getMenuInflater().inflate(R.menu.menu_post_detail, menu);
        
        // 본인 포스트인 경우 수정/삭제 옵션 표시, 아닌 경우 신고 옵션만 표시
        if (isCurrentUserPost) {
            menu.findItem(R.id.menu_report_post).setVisible(false);
        } else {
            menu.findItem(R.id.menu_edit_post).setVisible(false);
            menu.findItem(R.id.menu_delete_post).setVisible(false);
        }
        
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (itemId == R.id.menu_edit_post) {
            editPost();
            return true;
        } else if (itemId == R.id.menu_delete_post) {
            confirmDeletePost();
            return true;
        } else if (itemId == R.id.menu_report_post) {
            showReportDialog();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    private void editPost() {
        if (currentPost != null) {
            Intent intent = new Intent(this, CreatePostActivity.class);
            intent.putExtra(CreatePostActivity.EXTRA_EDIT_POST_ID, postId);
            editPostLauncher.launch(intent);
        }
    }
    
    private void confirmDeletePost() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_post)
                .setMessage(R.string.confirm_delete_post)
                .setPositiveButton(R.string.yes, (dialog, which) -> deletePost())
                .setNegativeButton(R.string.no, null)
                .show();
    }
    
    private void deletePost() {
        showLoading(true);
        
        postRepository.deletePost(postId)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(PostDetailActivity.this, R.string.post_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(PostDetailActivity.this, getString(R.string.error_delete_post) + ": " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                });
    }
    
    /**
     * 포스트 신고 다이얼로그 표시
     */
    private void showReportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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
        
        AlertDialog dialog = builder.create();
        
        // 신고 제출 및 취소 버튼 추가
        builder.setPositiveButton(R.string.report_submit, (dialogInterface, i) -> {
            // 선택된 라디오 버튼 가져오기
            int selectedId = radioGroup.getCheckedRadioButtonId();
            
            if (selectedId == -1) {
                // 아무것도 선택되지 않은 경우
                Toast.makeText(this, R.string.report_reason, Toast.LENGTH_SHORT).show();
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
            
            submitReport(reason);
        });
        
        builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> dialog.dismiss());
        
        // 다이얼로그 표시
        builder.create().show();
    }
    
    /**
     * 신고 제출 처리
     * @param reason 신고 사유
     */
    private void submitReport(String reason) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, R.string.login_required, Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (currentPost == null) {
            Toast.makeText(this, R.string.error_loading_post, Toast.LENGTH_SHORT).show();
            return;
        }
        
        showLoading(true);
        String reporterId = currentUser.getUid();
        
        // 신고 제출
        ReportRepository reportRepository = ReportRepository.getInstance();
        reportRepository.reportPost(reporterId, postId, reason)
            .addOnSuccessListener(aVoid -> {
                showLoading(false);
                Toast.makeText(this, R.string.report_success, Toast.LENGTH_SHORT).show();
            })
            .addOnFailureListener(e -> {
                showLoading(false);
                // 이미 신고한 경우 특별 메시지 표시
                if (e.getMessage() != null && e.getMessage().contains("이미 이 포스트를 신고하셨습니다")) {
                    Toast.makeText(this, R.string.report_already_reported, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, getString(R.string.report_failed) + ": " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                }
            });
    }
    
    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.layoutContent.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }
} 