package com.example.snapshot.ui.notifications;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.example.snapshot.R;
import com.example.snapshot.databinding.FragmentNotificationsBinding;
import com.example.snapshot.model.Notification;
import com.example.snapshot.repository.NotificationRepository;
import com.example.snapshot.repository.PostRepository;
import com.example.snapshot.repository.UserRepository;
import com.example.snapshot.ui.post.PostDetailActivity;
import com.example.snapshot.ui.profile.ProfileActivity;
import com.example.snapshot.ui.tag.TagDetailActivity;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment {

    private FragmentNotificationsBinding binding;
    private UserRepository userRepository;
    private NotificationRepository notificationRepository;
    private PostRepository postRepository;
    private NotificationAdapter notificationAdapter;
    private List<NotificationItem> notificationList = new ArrayList<>();
    
    private String currentUserId;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 저장소 초기화
        userRepository = UserRepository.getInstance();
        notificationRepository = NotificationRepository.getInstance();
        postRepository = PostRepository.getInstance();
        
        // 현재 로그인된 사용자 확인
        FirebaseUser currentUser = userRepository.getCurrentUser();
        if (currentUser == null) {
            return;
        }
        
        currentUserId = currentUser.getUid();
        
        // 리사이클러뷰 설정
        setupRecyclerView();
        
        // 새로고침 설정
        binding.swipeRefreshLayout.setOnRefreshListener(this::loadNotifications);
        
        // 초기 데이터 로드
        loadNotifications();
        
        // 모두 읽음으로 표시 버튼
        binding.btnMarkAllAsRead.setOnClickListener(v -> markAllAsRead());
        
        // 모두 지우기 버튼
        binding.btnClearAll.setOnClickListener(v -> clearAllNotifications());
    }
    
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_notifications, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_mark_all_read) {
            markAllAsRead();
            return true;
        } else if (id == R.id.menu_clear_all) {
            clearAllNotifications();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void setupRecyclerView() {
        notificationAdapter = new NotificationAdapter(requireContext(), notificationList);
        binding.recyclerNotifications.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerNotifications.setAdapter(notificationAdapter);
        
        // 알림 클릭 리스너 설정
        notificationAdapter.setOnNotificationClickListener(position -> {
            if (position >= 0 && position < notificationList.size()) {
                NotificationItem item = notificationList.get(position);
                markNotificationAsRead(item);
                navigateToNotificationTarget(item);
            }
        });
    }
    
    private void loadNotifications() {
        if (currentUserId == null) {
            showEmptyView(true);
            showLoading(false);
            return;
        }
        
        showLoading(true);
        
        // Firebase에서 알림 데이터 로드 - 실시간 리스너 사용
        Query query = notificationRepository.getNotificationsForUser(currentUserId);
        query.addSnapshotListener((queryDocumentSnapshots, firebaseFirestoreException) -> {
            if (firebaseFirestoreException != null) {
                showLoading(false);
                Toast.makeText(requireContext(), "알림 로드 실패: " + firebaseFirestoreException.getMessage(), Toast.LENGTH_SHORT).show();
                showEmptyView(true);
                return;
            }
            
            if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                notificationList.clear();
                notificationAdapter.notifyDataSetChanged();
                showEmptyView(true);
                showLoading(false);
                updateActionButtonsVisibility(false);
                return;
            }
            
            processNotifications(queryDocumentSnapshots);
        });
    }
    
    private void processNotifications(QuerySnapshot queryDocumentSnapshots) {
        notificationList.clear();
        
        if (queryDocumentSnapshots.isEmpty()) {
            showEmptyView(true);
            showLoading(false);
            return;
        }
        
        for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
            Notification notification = document.toObject(Notification.class);
            if (notification != null) {
                // Notification 모델을 NotificationItem으로 변환
                NotificationItem item = convertToNotificationItem(notification, document.getId());
                notificationList.add(item);
                
                // 읽지 않은 알림이 있으면 표시
                updateUnreadIndicator(notification.isRead());
            }
        }
        
        // 시간순으로 정렬 (최신순)
        notificationList.sort((n1, n2) -> Long.compare(n2.getTimestamp(), n1.getTimestamp()));
        
        notificationAdapter.notifyDataSetChanged();
        showEmptyView(notificationList.isEmpty());
        showLoading(false);
        
        // 액션 버튼 가시성 설정
        updateActionButtonsVisibility(!notificationList.isEmpty());
    }
    
    private NotificationItem convertToNotificationItem(Notification notification, String documentId) {
        int type;
        switch (notification.getNotificationType()) {
            case Notification.TYPE_LIKE:
                type = NotificationItem.TYPE_LIKE;
                break;
            case Notification.TYPE_COMMENT:
                type = NotificationItem.TYPE_COMMENT;
                break;
            case Notification.TYPE_FOLLOW:
                type = NotificationItem.TYPE_FOLLOW;
                break;
            case Notification.TYPE_TAG:
                type = NotificationItem.TYPE_TAG_LIKE;
                break;
            default:
                type = NotificationItem.TYPE_LIKE;
                break;
        }
        
        // 알림 타임스탬프
        long timestamp = notification.getCreationDate() != null 
                ? notification.getCreationDate().toDate().getTime() 
                : System.currentTimeMillis();
        
        // 새로운 생성자를 사용하여 NotificationItem 생성
        return new NotificationItem(
                documentId,                     // notification ID (Firestore 문서 ID)
                notification.getSenderId(),
                notification.getSenderName(),
                notification.getSenderProfilePic(),
                notification.getContent(),
                timestamp,
                type,
                notification.getTargetId(),
                null,                          // 콘텐츠 이미지 URL - 나중에 설정 가능
                notification.isRead()          // 읽음 상태
        );
    }
    
    private void updateUnreadIndicator(boolean isRead) {
        if (!isRead && getActivity() != null) {
            getActivity().findViewById(R.id.navigation_notifications).setSelected(true);
        }
    }
    
    private void updateActionButtonsVisibility(boolean hasNotifications) {
        binding.btnMarkAllAsRead.setVisibility(hasNotifications ? View.VISIBLE : View.GONE);
        binding.btnClearAll.setVisibility(hasNotifications ? View.VISIBLE : View.GONE);
    }
    
    private void markNotificationAsRead(NotificationItem item) {
        if (item.getNotificationId() != null && !item.isRead()) {
            notificationRepository.markNotificationAsRead(item.getNotificationId())
                    .addOnSuccessListener(aVoid -> {
                        item.setRead(true);
                        notificationAdapter.notifyDataSetChanged();
                    });
        }
    }
    
    private void markAllAsRead() {
        if (currentUserId != null) {
            notificationRepository.markAllNotificationsAsRead(currentUserId)
                    .addOnSuccessListener(aVoid -> {
                        // 모든 알림을 읽음으로 표시
                        for (NotificationItem item : notificationList) {
                            item.setRead(true);
                        }
                        notificationAdapter.notifyDataSetChanged();
                        Toast.makeText(requireContext(), "모든 알림을 읽음으로 표시했습니다", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(requireContext(), "오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }
    
    private void clearAllNotifications() {
        if (currentUserId != null) {
            notificationRepository.deleteAllNotificationsForUser(currentUserId)
                    .addOnSuccessListener(aVoid -> {
                        notificationList.clear();
                        notificationAdapter.notifyDataSetChanged();
                        showEmptyView(true);
                        updateActionButtonsVisibility(false);
                        Toast.makeText(requireContext(), "모든 알림을 삭제했습니다", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(requireContext(), "오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }
    
    private void navigateToNotificationTarget(NotificationItem notificationItem) {
        // 알림 유형에 따라 다른 화면으로 이동
        String targetId = notificationItem.getTargetId();
        if (targetId == null) return;
        
        switch (notificationItem.getType()) {
            case NotificationItem.TYPE_LIKE:
            case NotificationItem.TYPE_COMMENT:
                // 게시물 상세 화면으로 이동
                Intent postIntent = new Intent(requireContext(), PostDetailActivity.class);
                postIntent.putExtra(PostDetailActivity.EXTRA_POST_ID, targetId);
                startActivity(postIntent);
                break;
            case NotificationItem.TYPE_FOLLOW:
                // 사용자 프로필 화면으로 이동
                Intent userIntent = new Intent(requireContext(), ProfileActivity.class);
                userIntent.putExtra(ProfileActivity.EXTRA_USER_ID, targetId);
                startActivity(userIntent);
                break;
            case NotificationItem.TYPE_TAG_LIKE:
                // 태그 상세 화면으로 이동
                Intent tagIntent = new Intent(requireContext(), TagDetailActivity.class);
                tagIntent.putExtra(TagDetailActivity.EXTRA_TAG_ID, targetId);
                startActivity(tagIntent);
                break;
        }
    }
    
    private void showEmptyView(boolean isEmpty) {
        binding.tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.recyclerNotifications.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }
    
    private void showLoading(boolean isLoading) {
        binding.swipeRefreshLayout.setRefreshing(isLoading);
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 