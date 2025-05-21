package com.example.snapshot.ui.home;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.snapshot.R;
import com.example.snapshot.databinding.ItemPostBinding;
import com.example.snapshot.model.Post;
import com.example.snapshot.model.Tag;
import com.example.snapshot.repository.UserRepository;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {
    
    private final List<Post> posts;
    private final Context context;
    private OnPostInteractionListener listener;
    private OnPostTagSaveListener postTagSaveListener;
    private final UserRepository userRepository;
    
    public PostAdapter(List<Post> posts, Context context) {
        this.posts = posts;
        this.context = context;
        this.userRepository = UserRepository.getInstance();
    }
    
    public void setOnPostInteractionListener(OnPostInteractionListener listener) {
        this.listener = listener;
    }
    
    public void setOnPostTagSaveListener(OnPostTagSaveListener postTagSaveListener) {
        this.postTagSaveListener = postTagSaveListener;
    }
    
    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPostBinding binding = ItemPostBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new PostViewHolder(binding);
    }
    
    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = posts.get(position);
        holder.bind(post, position);
    }
    
    @Override
    public int getItemCount() {
        return posts.size();
    }
    
    public interface OnPostInteractionListener {
        void onLikeClicked(int position);
        void onCommentClicked(int position);
        void onShareClicked(int position);
        void onTagClicked(int postPosition, int tagPosition);
        void onUserProfileClicked(int position);
        void onReportClicked(int position);
    }
    
    public interface OnPostTagSaveListener {
        void onPostTagSave(int postAdapterPosition, int tagAdapterPositionInPost);
    }
    
    class PostViewHolder extends RecyclerView.ViewHolder {
        
        private final ItemPostBinding binding;
        
        public PostViewHolder(@NonNull ItemPostBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        
        public void bind(Post post, int position) {
            // 사용자 정보 설정
            binding.tvUserName.setText(post.getUserName());
            
            // 프로필 이미지 로드
            if (post.getUserProfilePic() != null && !post.getUserProfilePic().isEmpty()) {
                Glide.with(context)
                        .load(post.getUserProfilePic())
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .into(binding.ivUserProfile);
            } else {
                binding.ivUserProfile.setImageResource(R.drawable.ic_profile_placeholder);
            }
            
            // 게시물 날짜 설정
            if (post.getCreationDate() != null) {
                CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                        post.getCreationDate().getSeconds() * 1000,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                );
                binding.tvPostDate.setText(timeAgo);
            }
            
            // 게시물 이미지 로드
            if (post.getImageUrl() != null && !post.getImageUrl().isEmpty()) {
                Glide.with(context)
                        .load(post.getImageUrl())
                        .placeholder(R.color.grey_light)
                        .error(R.color.grey_light)
                        .into(binding.ivPostImage);
            } else {
                binding.ivPostImage.setImageResource(R.color.grey_light);
            }
            
            // 캡션 설정
            binding.tvCaption.setText(post.getCaption());
            
            // 좋아요 수 설정
            String likeText = context.getResources().getQuantityString(
                    R.plurals.like_count, post.getLikeCount(), post.getLikeCount());
            binding.tvLikeCount.setText(likeText);
            
            // 좋아요 상태 설정
            FirebaseUser currentUser = userRepository.getCurrentUser();
            if (currentUser != null && post.getUserLikes().contains(currentUser.getUid())) {
                binding.btnLike.setImageResource(R.drawable.ic_like_filled);
                binding.btnLike.setColorFilter(ContextCompat.getColor(context, R.color.error));
            } else {
                binding.btnLike.setImageResource(R.drawable.ic_like);
                binding.btnLike.setColorFilter(ContextCompat.getColor(context, R.color.black));
            }
            
            // 태그 설정
            setupTags(post.getTags(), position);
            
            // 클릭 리스너 설정
            setupClickListeners(position);
        }
        
        private void setupTags(List<Tag> tags, int postPosition) {
            if (tags == null || tags.isEmpty()) {
                binding.recyclerTags.setVisibility(View.GONE);
                return;
            }
            
            binding.recyclerTags.setVisibility(View.VISIBLE);
            TagAdapter tagAdapter = new TagAdapter(context, tags, true);
            binding.recyclerTags.setAdapter(tagAdapter);
            binding.recyclerTags.setLayoutManager(
                    new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            
            tagAdapter.setOnTagClickListener(tagPosition -> {
                if (listener != null) {
                    listener.onTagClicked(postPosition, tagPosition);
                }
            });
            
            tagAdapter.setOnTagLongClickListener(new TagAdapter.OnTagLongClickListener() {
                @Override
                public void onTagEdit(int tagPosition) {
                    // 포스트 조회 화면에서는 사용하지 않음
                }
                
                @Override
                public void onTagDelete(int tagPosition) {
                    // 포스트 조회 화면에서는 사용하지 않음
                }
                
                @Override
                public void onTagSave(int tagPosition) {
                    if (postTagSaveListener != null) {
                        postTagSaveListener.onPostTagSave(getAdapterPosition(), tagPosition);
                    }
                }
            });
        }
        
        private void setupClickListeners(int position) {
            binding.btnLike.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onLikeClicked(position);
                }
            });
            
            binding.btnComment.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCommentClicked(position);
                }
            });
            
            binding.btnShare.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onShareClicked(position);
                }
            });
            
            binding.ivUserProfile.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onUserProfileClicked(position);
                }
            });
            
            binding.tvUserName.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onUserProfileClicked(position);
                }
            });
            
            binding.btnMoreOptions.setOnClickListener(v -> {
                androidx.appcompat.widget.PopupMenu popupMenu = new androidx.appcompat.widget.PopupMenu(context, binding.btnMoreOptions);
                popupMenu.inflate(R.menu.menu_post_detail);
                FirebaseUser currentUser = userRepository.getCurrentUser();
                boolean isMyPost = currentUser != null && posts.get(position).getUserId().equals(currentUser.getUid());
                if (isMyPost) {
                    popupMenu.getMenu().findItem(R.id.menu_report_post).setVisible(false);
                    popupMenu.getMenu().findItem(R.id.menu_edit_post).setVisible(true);
                    popupMenu.getMenu().findItem(R.id.menu_delete_post).setVisible(true);
                } else {
                    popupMenu.getMenu().findItem(R.id.menu_report_post).setVisible(true);
                    popupMenu.getMenu().findItem(R.id.menu_edit_post).setVisible(false);
                    popupMenu.getMenu().findItem(R.id.menu_delete_post).setVisible(false);
                }
                popupMenu.setOnMenuItemClickListener(item -> {
                    int id = item.getItemId();
                    if (id == R.id.menu_report_post) {
                        if (listener != null) listener.onReportClicked(position);
                        return true;
                    }
                    // 수정/삭제 등 다른 메뉴는 필요시 추가
                    return false;
                });
                popupMenu.show();
            });
        }
    }
} 