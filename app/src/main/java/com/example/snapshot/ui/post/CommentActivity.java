package com.example.snapshot.ui.post;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;
import android.content.Context;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.snapshot.R;
import com.example.snapshot.databinding.ActivityCommentBinding;
import com.example.snapshot.model.Comment;
import com.example.snapshot.model.Post;
import com.example.snapshot.repository.CommentRepository;
import com.example.snapshot.repository.PostRepository;
import com.example.snapshot.repository.UserRepository;
import com.example.snapshot.ui.profile.ProfileActivity;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;
import com.google.firebase.Timestamp;
import android.widget.Button;
import android.widget.EditText;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import androidx.annotation.NonNull;
import android.util.Log;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.firestore.QuerySnapshot;

public class CommentActivity extends AppCompatActivity implements CommentAdapter.OnCommentActionListener {

    private static final String ACTIVITY_TAG = "CommentActivity";
    private ActivityCommentBinding binding;
    private CommentRepository commentRepository;
    private PostRepository postRepository;
    private UserRepository userRepository;
    
    private CommentAdapter commentAdapter;
    private List<Comment> commentList = new ArrayList<>();
    
    private String postId;
    private Post currentPost;
    
    public static final String EXTRA_POST_ID = "extra_post_id";

    // Variables for reply state
    private String replyingToCommentId = null;
    private int replyingToDepth = -1;
    private String replyingToUserName = null;

    // Variables for edit state
    private String editingCommentId = null;
    private String originalText = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCommentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 저장소 초기화
        commentRepository = CommentRepository.getInstance();
        postRepository = PostRepository.getInstance();
        userRepository = UserRepository.getInstance();
        
        // 툴바 설정
        setupToolbar();
        
        // 인텐트에서 포스트 ID 가져오기
        postId = getIntent().getStringExtra(EXTRA_POST_ID);
        if (postId == null || postId.isEmpty()) {
            Toast.makeText(this, "게시물 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // 리사이클러뷰 설정
        setupRecyclerView();
        
        // 현재 사용자 정보 가져오기
        loadCurrentUserProfile();
        
        // 포스트 정보 로드
        loadPostInfo();
        
        // 댓글 목록 로드
        loadComments();
        
        // 이벤트 리스너 설정
        setupListeners();
        
        // 스와이프 새로고침 설정
        binding.swipeRefreshLayout.setOnRefreshListener(this::loadComments);
    }
    
    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }
    
    private void setupRecyclerView() {
        commentAdapter = new CommentAdapter(commentList, this, this);
        binding.recyclerComments.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerComments.setAdapter(commentAdapter);
    }
    
    private void loadCurrentUserProfile() {
        FirebaseUser currentUser = userRepository.getCurrentUser();
        if (currentUser != null) {
            userRepository.getUserById(currentUser.getUid())
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String profileUrl = documentSnapshot.getString("profilePicUrl");
                            if (profileUrl != null && !profileUrl.isEmpty()) {
                                Glide.with(this)
                                        .load(profileUrl)
                                        .placeholder(R.drawable.default_profile)
                                        .error(R.drawable.default_profile)
                                        .into(binding.ivUserProfile);
                            }
                        }
                    });
        }
    }
    
    private void loadPostInfo() {
        postRepository.getPostById(postId)
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        currentPost = documentSnapshot.toObject(Post.class);
                    }
                });
    }
    
    private void loadComments() {
        showLoading(true);
        
        Query query = commentRepository.getCommentsForPost(postId);
        
        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Comment> rawComments = new ArrayList<>();
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        Comment comment = document.toObject(Comment.class);
                        if (comment != null) {
                           rawComments.add(comment);
                        }
                    }

                    // Process comments for nesting
                    List<Comment> processedComments = processCommentsForNesting(rawComments);

                    commentList.clear();
                    commentList.addAll(processedComments);

                    if (commentList.isEmpty()) {
                        showEmptyView(true);
                    } else {
                        showEmptyView(false);
                    }
                    
                    commentAdapter.notifyDataSetChanged();
                    binding.swipeRefreshLayout.setRefreshing(false);
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    binding.swipeRefreshLayout.setRefreshing(false);
                    showLoading(false);
                    Toast.makeText(this, "댓글을 불러오는 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    
    private List<Comment> processCommentsForNesting(List<Comment> rawComments) {
        List<Comment> nestedList = new ArrayList<>();
        Map<String, List<Comment>> repliesMap = new HashMap<>();

        // Separate top-level comments and replies
        for (Comment comment : rawComments) {
            if (comment.getParentId() == null || comment.getParentId().isEmpty()) {
                nestedList.add(comment); // Add top-level comment
                // Prepare list for its potential replies
                if (!repliesMap.containsKey(comment.getCommentId())) {
                    repliesMap.put(comment.getCommentId(), new ArrayList<>());
                }
            } else {
                // Add reply to the map, keyed by its parentId
                 if (!repliesMap.containsKey(comment.getParentId())) {
                    repliesMap.put(comment.getParentId(), new ArrayList<>());
                }
                repliesMap.get(comment.getParentId()).add(comment);
            }
        }

        // Add replies recursively (simple one-level depth for now for demonstration)
        // A more robust solution might involve recursion or a different data structure
        List<Comment> finalList = new ArrayList<>();
        for (Comment topLevel : nestedList) {
            finalList.add(topLevel);
            if (repliesMap.containsKey(topLevel.getCommentId())) {
                finalList.addAll(repliesMap.get(topLevel.getCommentId()));
            }
        }

        return finalList;
    }
    
    private void setupListeners() {
        binding.swipeRefreshLayout.setOnRefreshListener(this::loadComments);

        // Post/Update Comment Button
        binding.btnPostComment.setOnClickListener(v -> {
            String commentText = binding.etComment.getText().toString().trim();
            if (!TextUtils.isEmpty(commentText)) {
                if (editingCommentId != null) {
                    // Update existing comment
                    updateComment(editingCommentId, commentText);
                } else {
                    // Post new comment or reply
                    postComment(commentText, replyingToCommentId, replyingToDepth);
                }
            } else {
                Toast.makeText(this, "댓글 내용을 입력해주세요.", Toast.LENGTH_SHORT).show();
            }
        });

         // Cancel Edit/Reply Button (Initially hidden)
         binding.btnCancelEditReply.setOnClickListener(v -> cancelEditReplyMode());
    }
    
    private void postComment(String text, String parentId, int parentDepth) {
        FirebaseUser currentUser = userRepository.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        showLoading(true);
        
        userRepository.getUserById(currentUser.getUid())
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("name");
                        String profilePicUrl = documentSnapshot.getString("profilePicUrl");
                        
                        int depth = (parentId == null) ? 0 : parentDepth + 1;

                        Comment comment = new Comment(
                                null, // ID generated by repository
                                postId,
                                currentUser.getUid(),
                                name,
                                profilePicUrl,
                                text,
                                Timestamp.now(), // Use server timestamp
                                parentId,        // Set parentId for replies
                                depth            // Set depth
                        );
                        
                        commentRepository.addComment(comment, postId)
                                .addOnSuccessListener(aVoid -> {
                                    resetInputAndState();
                                    loadComments();
                                })
                                .addOnFailureListener(e -> {
                                    showLoading(false);
                                    Toast.makeText(this, "댓글 작성 중 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        showLoading(false);
                        Toast.makeText(this, "사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "사용자 정보 로딩 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    
    private void updateComment(String commentId, String newText) {
        showLoading(true);
        commentRepository.updateComment(commentId, newText)
                .addOnSuccessListener(aVoid -> {
                    resetInputAndState();
                    loadComments();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "댓글 수정 중 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    
    @Override
    public void onReplyComment(int position) {
        if (position < 0 || position >= commentList.size()) return;
        Comment commentToReply = commentList.get(position);

        editingCommentId = null; // Ensure not in edit mode
        originalText = null;

        replyingToCommentId = commentToReply.getCommentId();
        replyingToDepth = commentToReply.getDepth();
        replyingToUserName = commentToReply.getUserName();

        // Update UI for reply mode
        binding.etComment.setText("@" + replyingToUserName + " ");
        binding.etComment.requestFocus();
        binding.etComment.setSelection(binding.etComment.getText().length()); // Cursor to end
        binding.btnCancelEditReply.setVisibility(View.VISIBLE); // Show cancel button

        // Show keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(binding.etComment, InputMethodManager.SHOW_IMPLICIT);

        Toast.makeText(this, commentToReply.getUserName() + "님에게 답글다는 중", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onEditComment(int position) {
        if (position < 0 || position >= commentList.size()) return;
        Comment commentToEdit = commentList.get(position);

        // Check if current user is the author
        FirebaseUser currentUser = userRepository.getCurrentUser();
        if (currentUser == null || !commentToEdit.getUserId().equals(currentUser.getUid())) {
             Toast.makeText(this, "자신의 댓글만 수정할 수 있습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        replyingToCommentId = null; // Ensure not in reply mode
        replyingToDepth = -1;
        replyingToUserName = null;

        editingCommentId = commentToEdit.getCommentId();
        originalText = commentToEdit.getText();

        // Update UI for edit mode
        binding.etComment.setText(originalText);
        binding.etComment.requestFocus();
         binding.etComment.setSelection(binding.etComment.getText().length()); // Cursor to end
        binding.btnCancelEditReply.setVisibility(View.VISIBLE); // Show cancel button

        // Show keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(binding.etComment, InputMethodManager.SHOW_IMPLICIT);

         Toast.makeText(this, "댓글 수정 중", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onDeleteComment(int position) {
        if (position < 0 || position >= commentList.size()) return;
        Comment commentToDelete = commentList.get(position);

         // Check if current user is the author
        FirebaseUser currentUser = userRepository.getCurrentUser();
        if (currentUser == null || !commentToDelete.getUserId().equals(currentUser.getUid())) {
             Toast.makeText(this, "자신의 댓글만 삭제할 수 있습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("댓글 삭제")
                .setMessage("이 댓글을 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, which) -> deleteCommentConfirmed(commentToDelete))
                .setNegativeButton("취소", null)
                .show();
    }
    
    private void deleteCommentConfirmed(Comment comment) {
         showLoading(true);
        commentRepository.deleteComment(comment.getCommentId(), comment.getPostId())
                .addOnSuccessListener(aVoid -> {
                    resetInputAndState(); // Cancel edit/reply if deleting the target
                    loadComments(); // Refresh list
                })
                .addOnFailureListener(e -> {
                     showLoading(false);
                    Toast.makeText(this, "댓글 삭제 중 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    
    @Override
    public void onUserProfileClicked(int position) {
        if (position < 0 || position >= commentList.size()) return;
        String userId = commentList.get(position).getUserId();
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra(ProfileActivity.EXTRA_USER_ID, userId);
        startActivity(intent);
    }
    
    @Override
    public void onMentionClicked(String username) {
        Log.d(ACTIVITY_TAG, "Mention clicked for username: @" + username);
        Toast.makeText(this, "@" + username + " 프로필 찾는 중...", Toast.LENGTH_SHORT).show();

        if (userRepository == null) {
             Log.e(ACTIVITY_TAG, "UserRepository is null!");
             Toast.makeText(this, "오류: 사용자 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show();
             return;
        }

        userRepository.findUserByUsername(username)
            .addOnSuccessListener(new OnSuccessListener<QuerySnapshot>() {
                @Override
                public void onSuccess(QuerySnapshot queryDocumentSnapshots) {
                    String userId = userRepository.getUserIdFromSnapshot(queryDocumentSnapshots);
                    if (userId != null) {
                        Log.d(ACTIVITY_TAG, "Found user ID: " + userId + " for username: " + username + ". Starting ProfileActivity.");
                        Intent intent = new Intent(CommentActivity.this, ProfileActivity.class);
                        intent.putExtra(ProfileActivity.EXTRA_USER_ID, userId);
                        startActivity(intent);
                    } else {
                        Log.w(ACTIVITY_TAG, "User ID not found for username: " + username);
                        Toast.makeText(CommentActivity.this, "@" + username + " 사용자를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.e(ACTIVITY_TAG, "Error finding user by username: " + username, e);
                    Toast.makeText(CommentActivity.this, "사용자 검색 중 오류 발생", Toast.LENGTH_SHORT).show();
                }
            });
    }
    
    private void cancelEditReplyMode() {
        resetInputAndState();
        hideKeyboard();
    }
    
    private void resetInputAndState() {
         binding.etComment.setText("");
         binding.etComment.clearFocus();
         binding.btnCancelEditReply.setVisibility(View.GONE); // Hide cancel button

         replyingToCommentId = null;
         replyingToDepth = -1;
         replyingToUserName = null;
         editingCommentId = null;
         originalText = null;
         showLoading(false); // Ensure loading indicator is hidden
    }
    
    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = this.getCurrentFocus();
        if (view == null) {
            view = new View(this);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
    
    private void showLoading(boolean show) {
        binding.progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.btnPostComment.setEnabled(!show); // Disable button while loading
    }
    
    private void showEmptyView(boolean show) {
        binding.tvNoComments.setVisibility(show ? View.VISIBLE : View.GONE);
        binding.recyclerComments.setVisibility(show ? View.GONE : View.VISIBLE);
    }
} 