package com.example.snapshot.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.snapshot.R;
import com.example.snapshot.model.Post;
import com.example.snapshot.ui.post.PostDetailActivity;

import java.util.List;

public class ProfilePostAdapter extends RecyclerView.Adapter<ProfilePostAdapter.PostViewHolder> {

    private final List<Post> posts;
    private final Context context;

    public ProfilePostAdapter(Context context, List<Post> posts) {
        this.context = context;
        this.posts = posts;
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = posts.get(position);
        
        // 게시물 썸네일 로드 - null 체크 추가
        if (post != null && holder.postImage != null) {
            // 기본 이미지 로드 구성
            Glide.with(context)
                    .load(post.getImageUrl() != null ? post.getImageUrl() : R.drawable.placeholder_image)
                    .placeholder(R.drawable.placeholder_image)
                    .centerCrop()
                    .into(holder.postImage);
        }
        
        // 게시물 클릭 이벤트
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, PostDetailActivity.class);
            intent.putExtra(PostDetailActivity.EXTRA_POST_ID, post.getPostId());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView postImage;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            postImage = itemView.findViewById(R.id.iv_post_image);
        }
    }
} 