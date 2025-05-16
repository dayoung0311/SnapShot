package com.example.snapshot.ui.notifications;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.snapshot.R;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private final Context context;
    private final List<NotificationItem> notifications;
    private OnNotificationClickListener listener;
    
    public NotificationAdapter(Context context, List<NotificationItem> notifications) {
        this.context = context;
        this.notifications = notifications;
    }
    
    public void setOnNotificationClickListener(OnNotificationClickListener listener) {
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        NotificationItem notification = notifications.get(position);
        holder.bind(notification, position);
    }
    
    @Override
    public int getItemCount() {
        return notifications.size();
    }
    
    public interface OnNotificationClickListener {
        void onNotificationClick(int position);
    }
    
    class NotificationViewHolder extends RecyclerView.ViewHolder {
        
        private final CircleImageView ivUserProfile;
        private final TextView tvNotificationText;
        private final TextView tvNotificationTime;
        private final ImageView ivNotificationContent;
        private final View unreadIndicator;
        private final View itemContainer;
        
        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            ivUserProfile = itemView.findViewById(R.id.iv_user_profile);
            tvNotificationText = itemView.findViewById(R.id.tv_notification_text);
            tvNotificationTime = itemView.findViewById(R.id.tv_notification_time);
            ivNotificationContent = itemView.findViewById(R.id.iv_notification_content);
            unreadIndicator = itemView.findViewById(R.id.view_unread_indicator);
            itemContainer = itemView.findViewById(R.id.notification_container);
        }
        
        public void bind(NotificationItem notification, int position) {
            // 알림 텍스트 설정
            tvNotificationText.setText(notification.getNotificationText());
            
            // 알림 시간 설정
            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                    notification.getTimestamp(),
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
            );
            tvNotificationTime.setText(timeAgo);
            
            // 사용자 프로필 이미지 로드
            if (notification.getUserProfilePic() != null && !notification.getUserProfilePic().isEmpty()) {
                Glide.with(context)
                        .load(notification.getUserProfilePic())
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .into(ivUserProfile);
            } else {
                ivUserProfile.setImageResource(R.drawable.ic_profile_placeholder);
            }
            
            // 알림 관련 콘텐츠 이미지 로드
            if (notification.getContentImageUrl() != null && !notification.getContentImageUrl().isEmpty()) {
                ivNotificationContent.setVisibility(View.VISIBLE);
                Glide.with(context)
                        .load(notification.getContentImageUrl())
                        .placeholder(R.color.grey_light)
                        .error(R.color.grey_light)
                        .centerCrop()
                        .into(ivNotificationContent);
            } else {
                ivNotificationContent.setVisibility(View.GONE);
            }
            
            // 읽음/안읽음 상태에 따른 스타일 적용
            if (notification.isRead()) {
                // 읽은 알림은 회색 배경과 읽음 표시기 숨김
                itemContainer.setBackgroundColor(ContextCompat.getColor(context, R.color.white));
                unreadIndicator.setVisibility(View.GONE);
                tvNotificationText.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
            } else {
                // 안읽은 알림은 강조 배경과 읽음 표시기 표시
                itemContainer.setBackgroundColor(ContextCompat.getColor(context, R.color.unread_notification));
                unreadIndicator.setVisibility(View.VISIBLE);
                tvNotificationText.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            }
            
            // 클릭 리스너 설정
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNotificationClick(position);
                }
            });
        }
    }
} 