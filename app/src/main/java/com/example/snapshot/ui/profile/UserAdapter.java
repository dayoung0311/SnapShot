package com.example.snapshot.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.snapshot.R;
import com.example.snapshot.model.User;
import com.example.snapshot.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

/**
 * 사용자 목록을 표시하는 RecyclerView 어댑터
 */
public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    private final Context context;
    private final List<User> userList;
    private final UserRepository userRepository;
    private final boolean showFollowButton;
    
    public UserAdapter(Context context, List<User> userList, boolean showFollowButton) {
        this.context = context;
        this.userList = userList;
        this.userRepository = UserRepository.getInstance();
        this.showFollowButton = showFollowButton;
    }
    
    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = userList.get(position);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        
        holder.bind(user);
        
        // 사용자 프로필로 이동하는 이벤트
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ProfileActivity.class);
            intent.putExtra(ProfileActivity.EXTRA_USER_ID, user.getUserId());
            context.startActivity(intent);
        });
        
        // 팔로우 버튼 처리
        if (showFollowButton && currentUser != null && !user.getUserId().equals(currentUser.getUid())) {
            holder.btnFollow.setVisibility(View.VISIBLE);
            
            // 팔로우 상태 확인 및 버튼 업데이트
            checkFollowStatus(holder, user);
            
            // 팔로우/언팔로우 버튼 클릭 이벤트
            holder.btnFollow.setOnClickListener(v -> toggleFollow(holder, user));
        } else {
            holder.btnFollow.setVisibility(View.GONE);
        }
    }
    
    @Override
    public int getItemCount() {
        return userList.size();
    }
    
    private void checkFollowStatus(UserViewHolder holder, User user) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        
        userRepository.isFollowing(currentUser.getUid(), user.getUserId())
                .addOnSuccessListener(isFollowing -> updateFollowButton(holder, isFollowing));
    }
    
    private void updateFollowButton(UserViewHolder holder, boolean isFollowing) {
        if (isFollowing) {
            holder.btnFollow.setText(R.string.unfollow);
            holder.btnFollow.setBackgroundResource(R.drawable.bg_button_secondary);
            holder.btnFollow.setTextColor(context.getResources().getColor(R.color.colorUnfollow));
        } else {
            holder.btnFollow.setText(R.string.follow);
            holder.btnFollow.setBackgroundResource(R.drawable.bg_button_primary);
            holder.btnFollow.setTextColor(context.getResources().getColor(R.color.white));
        }
    }
    
    private void toggleFollow(UserViewHolder holder, User user) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        
        String currentUserId = currentUser.getUid();
        
        userRepository.isFollowing(currentUserId, user.getUserId())
                .addOnSuccessListener(isFollowing -> {
                    if (isFollowing) {
                        // 언팔로우
                        userRepository.unfollowUser(currentUserId, user.getUserId())
                                .addOnSuccessListener(aVoid -> {
                                    updateFollowButton(holder, false);
                                    user.setFollowerCount(Math.max(0, user.getFollowerCount() - 1));
                                })
                                .addOnFailureListener(e -> 
                                    Toast.makeText(context, "언팔로우 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    } else {
                        // 팔로우
                        userRepository.followUser(currentUserId, user.getUserId())
                                .addOnSuccessListener(aVoid -> {
                                    updateFollowButton(holder, true);
                                    user.setFollowerCount(user.getFollowerCount() + 1);
                                })
                                .addOnFailureListener(e -> 
                                    Toast.makeText(context, "팔로우 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
    }
    
    static class UserViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivProfile;
        private final TextView tvUsername;
        private final TextView tvName;
        private final Button btnFollow;
        
        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfile = itemView.findViewById(R.id.iv_profile);
            tvUsername = itemView.findViewById(R.id.tv_username);
            tvName = itemView.findViewById(R.id.tv_name);
            btnFollow = itemView.findViewById(R.id.btn_follow);
        }
        
        public void bind(User user) {
            tvUsername.setText("@" + user.getUsername());
            tvName.setText(user.getUsername());
            
            if (user.getProfilePicUrl() != null && !user.getProfilePicUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(user.getProfilePicUrl())
                        .placeholder(R.drawable.default_profile)
                        .circleCrop()
                        .into(ivProfile);
            } else {
                ivProfile.setImageResource(R.drawable.default_profile);
            }
        }
    }
} 