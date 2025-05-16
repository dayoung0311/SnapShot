package com.example.snapshot.repository;

import com.example.snapshot.model.User;
import com.example.snapshot.model.Notification;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import android.util.Log;

public class UserRepository {
    private static final String USERS_COLLECTION = "users";
    private static final String TAG = "UserRepository";
    
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final FirebaseStorage storage;
    private final Executor executor;
    
    // 싱글톤 패턴
    private static UserRepository instance;
    
    public static UserRepository getInstance() {
        if (instance == null) {
            instance = new UserRepository();
        }
        return instance;
    }
    
    private UserRepository() {
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        executor = Executors.newFixedThreadPool(4);
    }
    
    // 현재 로그인된 사용자 확인
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }
    
    // 이메일/비밀번호 회원가입
    public Task<AuthResult> registerUser(String email, String password) {
        return auth.createUserWithEmailAndPassword(email, password);
    }
    
    // 이메일/비밀번호 로그인
    public Task<AuthResult> loginUser(String email, String password) {
        return auth.signInWithEmailAndPassword(email, password);
    }
    
    // 로그아웃
    public void logoutUser() {
        auth.signOut();
    }
    
    // 사용자 프로필 데이터 생성/업데이트
    public Task<Void> createOrUpdateUser(User user) {
        DocumentReference userRef = firestore.collection(USERS_COLLECTION).document(user.getUserId());
        return userRef.set(user);
    }
    
    // 사용자 정보 가져오기
    public Task<DocumentSnapshot> getUserById(String userId) {
        return firestore.collection(USERS_COLLECTION).document(userId).get();
    }
    
    // 프로필 이미지 업로드
    public UploadTask uploadProfileImage(String userId, byte[] imageData) {
        StorageReference storageRef = storage.getReference();
        StorageReference profileImageRef = storageRef.child("profile_images/" + userId + ".jpg");
        return profileImageRef.putBytes(imageData);
    }
    
    // 사용자 검색 (이름으로)
    public Query searchUsersByName(String name) {
        return firestore.collection(USERS_COLLECTION)
                .orderBy("name")
                .startAt(name)
                .endAt(name + "\uf8ff");
    }
    
    // 사용자 검색 (이메일로)
    public Query searchUsersByEmail(String email) {
        return firestore.collection(USERS_COLLECTION)
                .orderBy("email")
                .startAt(email)
                .endAt(email + "\uf8ff");
    }
    
    // 사용자 팔로우
    public Task<Void> followUser(String currentUserId, String targetUserId) {
        // 현재 사용자의 팔로잉 목록에 타겟 사용자 추가
        DocumentReference currentUserRef = firestore.collection(USERS_COLLECTION).document(currentUserId);
        
        // 타겟 사용자의 팔로워 목록에 현재 사용자 추가
        DocumentReference targetUserRef = firestore.collection(USERS_COLLECTION).document(targetUserId);
        
        // 트랜잭션으로 두 작업 동시에 처리
        Task<Void> transactionTask = firestore.runTransaction(transaction -> {
            // 현재 사용자 문서 가져오기
            DocumentSnapshot currentUserSnapshot = transaction.get(currentUserRef);
            DocumentSnapshot targetUserSnapshot = transaction.get(targetUserRef);
            
            // 사용자 객체로 변환
            User currentUser = currentUserSnapshot.toObject(User.class);
            User targetUser = targetUserSnapshot.toObject(User.class);
            
            if (currentUser != null && targetUser != null) {
                // 팔로잉/팔로워 리스트 업데이트
                currentUser.getFollowing().add(targetUserId);
                currentUser.setFollowingCount(currentUser.getFollowing().size());
                
                targetUser.getFollowers().add(currentUserId);
                targetUser.setFollowerCount(targetUser.getFollowers().size());
                
                // 트랜잭션에 업데이트 추가
                transaction.set(currentUserRef, currentUser);
                transaction.set(targetUserRef, targetUser);
                
                // 알림 생성 코드는 트랜잭션 *외부*의 성공 리스너로 이동
            }
            
            return (Void) null; // 명시적으로 Void 타입 반환
        });

        transactionTask.addOnSuccessListener(aVoid -> {
            // 트랜잭션 성공 시 알림 전송
            // currentUser와 targetUser 정보가 필요한데, 트랜잭션 외부에서는 직접 접근 불가.
            // followUser 호출 시점에서 사용자 정보를 가져와서 사용하거나,
            // targetUserId로 다시 조회해야 함. 여기서는 targetUserId로 User 객체를 다시 조회.
            getUserById(currentUserId).addOnSuccessListener(currentUserSnapshot -> {
                User currentUserData = currentUserSnapshot.toObject(User.class);
                if (currentUserData != null) {
                    Notification followNotification = Notification.createFollowNotification(
                           targetUserId,          // 알림 받는 사람 (팔로우 당한 사람)
                           currentUserId,         // 알림 보낸 사람 (팔로우 한 사람)
                           currentUserData.getUsername(), // 보낸 사람 이름 (다시 조회)
                           currentUserData.getProfilePicUrl() // 보낸 사람 프로필 사진 (다시 조회)
                    );
                    NotificationRepository.getInstance().sendNotificationToUser(targetUserId, followNotification);
                }
            });
        });
        
        return transactionTask; // Return the Task<Void>
    }
    
    // 사용자 언팔로우
    public Task<Void> unfollowUser(String currentUserId, String targetUserId) {
        // 현재 사용자의 팔로잉 목록에서 타겟 사용자 제거
        DocumentReference currentUserRef = firestore.collection(USERS_COLLECTION).document(currentUserId);
        
        // 타겟 사용자의 팔로워 목록에서 현재 사용자 제거
        DocumentReference targetUserRef = firestore.collection(USERS_COLLECTION).document(targetUserId);
        
        // 트랜잭션으로 두 작업 동시에 처리
        Task<Void> transactionTask = firestore.runTransaction(transaction -> {
            // 현재 사용자 문서 가져오기
            DocumentSnapshot currentUserSnapshot = transaction.get(currentUserRef);
            DocumentSnapshot targetUserSnapshot = transaction.get(targetUserRef);
            
            // 사용자 객체로 변환
            User currentUser = currentUserSnapshot.toObject(User.class);
            User targetUser = targetUserSnapshot.toObject(User.class);
            
            if (currentUser != null && targetUser != null) {
                // 팔로잉/팔로워 리스트 업데이트
                currentUser.getFollowing().remove(targetUserId);
                currentUser.setFollowingCount(currentUser.getFollowing().size());
                
                targetUser.getFollowers().remove(currentUserId);
                targetUser.setFollowerCount(targetUser.getFollowers().size());
                
                // 트랜잭션에 업데이트 추가
                transaction.set(currentUserRef, currentUser);
                transaction.set(targetUserRef, targetUser);
            }
            
            return (Void) null; // 명시적으로 Void 타입 반환
        });

        return transactionTask; // Return the Task<Void>
    }

    // 팔로우 상태 확인
    public Task<Boolean> isFollowing(String currentUserId, String targetUserId) {
        return getUserById(currentUserId)
                .continueWith(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        User currentUser = task.getResult().toObject(User.class);
                        if (currentUser != null && currentUser.getFollowing() != null) {
                            return currentUser.getFollowing().contains(targetUserId);
                        }
                    }
                    return false;
                });
    }

    // 여러 사용자 ID로 사용자 정보 목록을 가져오는 메소드 (최대 30개씩 분할 처리)
    public Task<List<User>> getUsersByIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Tasks.forResult(new ArrayList<>());
        }

        List<Task<com.google.firebase.firestore.QuerySnapshot>> tasks = new ArrayList<>();
        List<String> idChunk = new ArrayList<>();

        for (int i = 0; i < userIds.size(); i++) {
            idChunk.add(userIds.get(i));
            // Firestore 'in' 쿼리는 최대 30개의 비교 값을 지원
            if (idChunk.size() == 30 || i == userIds.size() - 1) {
                tasks.add(firestore.collection(USERS_COLLECTION)
                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), new ArrayList<>(idChunk))
                        .get());
                idChunk.clear();
            }
        }

        return Tasks.whenAllSuccess(tasks).continueWith(task -> {
            List<User> users = new ArrayList<>();
            if (task.isSuccessful()) {
                List<Object> snapshots = task.getResult();
                for (Object snapshot : snapshots) {
                    if (snapshot instanceof com.google.firebase.firestore.QuerySnapshot) {
                        for (DocumentSnapshot document : ((com.google.firebase.firestore.QuerySnapshot) snapshot).getDocuments()) {
                            User user = document.toObject(User.class);
                            if (user != null) {
                                users.add(user);
                            }
                        }
                    }
                }
            }
            return users;
        });
    }

    // 사용자 이름으로 사용자 검색 (첫 번째 결과만)
    public Task<QuerySnapshot> findUserByUsername(String username) {
        Log.d(TAG, "Searching for user with username: " + username);
        return firestore.collection(USERS_COLLECTION)
                .whereEqualTo("name", username) // Check if the field name is correct ('name' or 'userName' etc.)
                .limit(1)
                .get();
    }

    // QuerySnapshot에서 UserId 추출 (Activity에서 호출)
    public String getUserIdFromSnapshot(QuerySnapshot snapshots) {
        if (snapshots != null && !snapshots.isEmpty()) {
            String userId = snapshots.getDocuments().get(0).getId();
            Log.d(TAG, "User found with ID: " + userId);
            return userId;
        } else {
            Log.d(TAG, "User not found from snapshot.");
            return null;
        }
    }
} 