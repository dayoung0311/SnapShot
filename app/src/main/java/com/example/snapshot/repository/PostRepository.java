package com.example.snapshot.repository;

import com.example.snapshot.model.Comment;
import com.example.snapshot.model.Post;
import com.example.snapshot.model.Tag;
import com.example.snapshot.model.User;
import com.example.snapshot.model.Notification;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PostRepository {
    private static final String POSTS_COLLECTION = "posts";
    private static final String COMMENTS_COLLECTION = "comments";
    private static final String TAGS_COLLECTION = "tags";
    private static final String POST_TAGS_COLLECTION = "post_tags";
    private static final String TAG = "PostRepository";
    
    private final FirebaseFirestore firestore;
    private final FirebaseStorage storage;
    private final FirebaseAuth auth;
    private final NotificationRepository notificationRepository;
    
    // 싱글톤 패턴
    private static PostRepository instance;
    
    public static PostRepository getInstance() {
        if (instance == null) {
            instance = new PostRepository();
        }
        return instance;
    }
    
    private PostRepository() {
        firestore = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        auth = FirebaseAuth.getInstance();
        notificationRepository = NotificationRepository.getInstance();
    }
    
    // 이미지 업로드 (파일 이름 지정)
    public UploadTask uploadPostImage(String imageFileName, byte[] imageData) {
        StorageReference storageRef = storage.getReference();
        StorageReference postImageRef = storageRef.child("post_images/" + imageFileName);
        return postImageRef.putBytes(imageData);
    }
    
    // 새 포스트 생성
    public Task<Void> createPost(Post post) {
        String postId = firestore.collection(POSTS_COLLECTION).document().getId();
        post.setPostId(postId);
        
        DocumentReference postRef = firestore.collection(POSTS_COLLECTION).document(postId);
        Task<Void> setTask = postRef.set(post);
        
        // 태그가 있으면 태그-포스트 관계 매핑
        if (post.getTags() != null && !post.getTags().isEmpty()) {
            for (Tag tag : post.getTags()) {
                // 태그-포스트 매핑 문서 생성
                String mappingId = tag.getTagId() + "_" + postId;
                DocumentReference mappingRef = firestore.collection(POST_TAGS_COLLECTION).document(mappingId);
                
                Map<String, Object> mappingData = new HashMap<>();
                mappingData.put("tagId", tag.getTagId());
                mappingData.put("postId", postId);
                mappingData.put("createdAt", FieldValue.serverTimestamp());
                
                mappingRef.set(mappingData);
                
                // 태그 사용 카운트 증가
                TagRepository.getInstance().incrementTagUseCount(tag.getTagId());
                
                // 태그 구독자에게 알림 전송
                UserRepository userRepo = UserRepository.getInstance();
                if (userRepo.getCurrentUser() != null) {
                    String userId = userRepo.getCurrentUser().getUid();
                    userRepo.getUserById(userId).addOnSuccessListener(documentSnapshot -> {
                        User currentUser = documentSnapshot.toObject(User.class);
                        if (currentUser != null) {
                            notificationRepository.sendNotificationToTagSubscribers(
                                    tag.getTagId(), 
                                    tag.getName(), 
                                    currentUser.getUserId(), 
                                    currentUser.getUsername(),
                                    currentUser.getProfilePicUrl());
                        }
                    });
                }
            }
        }
        
        return setTask;
    }
    
    // 특정 포스트 가져오기
    public Task<DocumentSnapshot> getPostById(String postId) {
        return firestore.collection(POSTS_COLLECTION).document(postId).get();
    }
    
    // 홈 피드용 포스트 가져오기 (팔로우 중인 사용자 + 인기 포스트)
    public Query getPostsForHomeFeed(List<String> followingIds) {
        if (followingIds == null || followingIds.isEmpty()) {
            followingIds = new ArrayList<>();
            followingIds.add("dummy_id"); // whereIn은 비어있는 리스트를 허용하지 않음
        }
        
        // hidden 필드가 false인 포스트만 가져옴 (신고로 숨겨지지 않은 것)
        return firestore.collection(POSTS_COLLECTION)
                .whereIn("userId", followingIds)
                .whereEqualTo("hidden", false)
                .orderBy("creationDate", Query.Direction.DESCENDING)
                .limit(20);
    }
    
    // 인기 포스트 가져오기
    public Query getPopularPosts() {
        // hidden 필드가 false인 포스트만 가져옴 (신고로 숨겨지지 않은 것)
        return firestore.collection(POSTS_COLLECTION)
                .whereEqualTo("hidden", false)
                .orderBy("likeCount", Query.Direction.DESCENDING)
                .limit(20);
    }
    
    /**
     * 신고된 사용자를 제외한 홈 피드용 포스트 가져오기
     * 홈 프래그먼트에서 사용할 메소드
     * @param followingIds 팔로우 중인 사용자 ID 목록
     * @param restrictedUsers 제한된 사용자 ID 목록 (신고 누적으로 제한된 사용자)
     * @return 필터링된 포스트 쿼리
     */
    public Query getFilteredPostsForHomeFeed(List<String> followingIds, List<String> restrictedUsers) {
        if (followingIds == null || followingIds.isEmpty()) {
            followingIds = new ArrayList<>();
            followingIds.add("dummy_id"); // whereIn은 비어있는 리스트를 허용하지 않음
        }
        
        // 기본 쿼리 - 팔로우 중인 사용자의 숨겨지지 않은 포스트
        Query baseQuery = firestore.collection(POSTS_COLLECTION)
                .whereIn("userId", followingIds)
                .whereEqualTo("hidden", false)
                .orderBy("creationDate", Query.Direction.DESCENDING);
        
        // 제한된 사용자가 있으면 필터링
        if (restrictedUsers != null && !restrictedUsers.isEmpty()) {
            // Firestore에서는 whereIn과 whereNotIn을 함께 사용할 수 없어 Java에서 필터링 처리
            // 이 경우, 클라이언트 측에서 제한된 사용자의 포스트를 필터링해야 함
            return baseQuery;
        }
        
        return baseQuery.limit(20);
    }
    
    // 특정 사용자의 모든 포스트 가져오기
    public Query getPostsByUser(String userId) {
        return firestore.collection(POSTS_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("creationDate", Query.Direction.DESCENDING);
    }
    
    // 최신 포스트 가져오기 (PlaceSearchFragment 초기 로드용)
    public Query getRecentPosts(int limit) {
        return firestore.collection(POSTS_COLLECTION)
                .orderBy("creationDate", Query.Direction.DESCENDING)
                .limit(limit);
    }
    
    // 포스트 업데이트 (부분 업데이트)
    public Task<Void> updatePost(String postId, Map<String, Object> updates) {
        DocumentReference postRef = firestore.collection(POSTS_COLLECTION).document(postId);
        return postRef.update(updates);
    }
    
    // 포스트에 태그 추가
    public Task<Void> addTagToPost(String postId, Tag tag) {
        // 1. 포스트 문서 참조 가져오기
        DocumentReference postRef = firestore.collection(POSTS_COLLECTION).document(postId);
        
        // 2. 트랜잭션으로 태그 추가 및 태그-포스트 매핑
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot postSnapshot = transaction.get(postRef);
            Post post = postSnapshot.toObject(Post.class);
            
            if (post != null) {
                // 태그가 이미 포스트에 있는지 확인
                boolean tagExists = false;
                if (post.getTags() != null) {
                    for (Tag existingTag : post.getTags()) {
                        if (existingTag.getTagId().equals(tag.getTagId())) {
                            tagExists = true;
                            break;
                        }
                    }
                }
                
                if (!tagExists) {
                    // 포스트의 태그 목록에 추가
                    post.addTag(tag);
                    transaction.set(postRef, post);
                    
                    // 태그-포스트 매핑 문서 생성
                    String mappingId = tag.getTagId() + "_" + postId;
                    DocumentReference mappingRef = firestore.collection(POST_TAGS_COLLECTION).document(mappingId);
                    
                    Map<String, Object> mappingData = new HashMap<>();
                    mappingData.put("tagId", tag.getTagId());
                    mappingData.put("postId", postId);
                    mappingData.put("createdAt", FieldValue.serverTimestamp());
                    
                    transaction.set(mappingRef, mappingData);
                    
                    // 태그 사용 카운트 업데이트는 트랜잭션 외부에서 수행
                }
            }
            
            return null;
        }).continueWithTask(task -> {
            if (task.isSuccessful()) {
                // 태그 사용 카운트 증가
                return TagRepository.getInstance().incrementTagUseCount(tag.getTagId());
            } else {
                return Tasks.forException(task.getException());
            }
        });
    }
    
    // 태그 ID로 포스트 검색
    public Task<QuerySnapshot> getPostsByTagId(String tagId) {
        // 1. 태그-포스트 매핑에서 해당 태그가 포함된 포스트 ID 목록을 가져옴
        return firestore.collection(POST_TAGS_COLLECTION)
                .whereEqualTo("tagId", tagId)
                .get()
                .continueWithTask(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<String> postIds = new ArrayList<>();
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            String postId = doc.getString("postId");
                            if (postId != null) {
                                postIds.add(postId);
                            }
                        }
                        
                        if (postIds.isEmpty()) {
                            // 포스트 ID가 없으면 빈 결과 반환
                            return Tasks.forResult(task.getResult());
                        }
                        
                        // 2. 포스트 ID 목록으로 포스트 가져오기
                        return firestore.collection(POSTS_COLLECTION)
                                .whereIn("postId", postIds)
                                .orderBy("creationDate", Query.Direction.DESCENDING)
                                .get();
                    } else {
                        return Tasks.forException(
                                task.getException() != null ? task.getException() : 
                                new RuntimeException("Failed to get posts by tag ID"));
                    }
                });
    }
    
    // 태그 이름으로 포스트 검색
    public Query searchPostsByTag(String tagName) {
        return firestore.collection(POSTS_COLLECTION)
                .whereArrayContains("tagNames", tagName)
                .orderBy("creationDate", Query.Direction.DESCENDING);
    }
    
    // 복합 태그 검색 (여러 태그 조합)
    public Task<List<Post>> searchPostsByMultipleTags(List<String> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return Tasks.forResult(new ArrayList<>());
        }
        
        // 1. 첫 번째 태그 ID로 포스트 ID 목록을 가져옴
        return firestore.collection(POST_TAGS_COLLECTION)
                .whereEqualTo("tagId", tagIds.get(0))
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        return Tasks.forResult(new ArrayList<Post>());
                    }
                    
                    List<String> commonPostIds = new ArrayList<>();
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        String postId = doc.getString("postId");
                        if (postId != null) {
                            commonPostIds.add(postId);
                        }
                    }
                    
                    // 2. 다른 태그 ID들과 교집합 계산
                    List<Task<QuerySnapshot>> tagQueries = new ArrayList<>();
                    for (int i = 1; i < tagIds.size(); i++) {
                        String tagId = tagIds.get(i);
                        tagQueries.add(firestore.collection(POST_TAGS_COLLECTION)
                                .whereEqualTo("tagId", tagId)
                                .get());
                    }
                    
                    if (tagQueries.isEmpty()) {
                        // 태그가 하나뿐이면 바로 결과 반환
                        if (commonPostIds.isEmpty()) {
                            return Tasks.forResult(new ArrayList<Post>());
                        } else {
                            return getPostsByIds(commonPostIds);
                        }
                    }
                    
                    // 3. 모든 태그 쿼리 결과를 기다림
                    return Tasks.whenAllSuccess(tagQueries).continueWithTask(tagTask -> {
                        if (!tagTask.isSuccessful()) {
                            return Tasks.forResult(new ArrayList<Post>());
                        }
                        
                        List<Object> results = tagTask.getResult();
                        for (Object result : results) {
                            QuerySnapshot querySnapshot = (QuerySnapshot) result;
                            List<String> currentTagPostIds = new ArrayList<>();
                            
                            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                String postId = doc.getString("postId");
                                if (postId != null) {
                                    currentTagPostIds.add(postId);
                                }
                            }
                            
                            // 교집합 계산
                            commonPostIds.retainAll(currentTagPostIds);
                            
                            if (commonPostIds.isEmpty()) {
                                // 교집합이 없으면 빈 결과 반환
                                return Tasks.forResult(new ArrayList<Post>());
                            }
                        }
                        
                        // 4. 공통 포스트 ID로 포스트 가져오기
                        return getPostsByIds(commonPostIds);
                    });
                });
    }
    
    // 포스트 ID 목록으로 포스트 가져오기
    private Task<List<Post>> getPostsByIds(List<String> postIds) {
        if (postIds.isEmpty()) {
            return Tasks.forResult(new ArrayList<>());
        }
        
        return firestore.collection(POSTS_COLLECTION)
                .whereIn("postId", postIds)
                .orderBy("creationDate", Query.Direction.DESCENDING)
                .get()
                .continueWith(task -> {
                    List<Post> posts = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            Post post = doc.toObject(Post.class);
                            if (post != null) {
                                posts.add(post);
                            }
                        }
                    }
                    return posts;
                });
    }
    
    // 기존 likePost, unlikePost 메소드를 대체하는 toggleLike 메소드
    public Task<Void> toggleLike(String postId, String userId) {
        DocumentReference postRef = firestore.collection(POSTS_COLLECTION).document(postId);
        
        return firestore.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot postSnapshot = transaction.get(postRef);
                Post post = postSnapshot.toObject(Post.class);
            
            if (post == null) {
                throw new FirebaseFirestoreException("Post not found", FirebaseFirestoreException.Code.NOT_FOUND);
            }
            
            boolean currentlyLiked = post.getUserLikes().contains(userId);
            
            if (currentlyLiked) {
                // 좋아요 취소
                transaction.update(postRef, "userLikes", FieldValue.arrayRemove(userId));
                transaction.update(postRef, "likeCount", FieldValue.increment(-1));
                
                // 좋아요 알림 제거 (필요한 경우 구현)
                // notificationRepository.removeLikeNotification(...);
            } else {
                // 좋아요 추가
                transaction.update(postRef, "userLikes", FieldValue.arrayUnion(userId));
                transaction.update(postRef, "likeCount", FieldValue.increment(1));
                
                // 좋아요 알림 추가 (자신의 게시물에는 보내지 않음)
                if (!post.getUserId().equals(userId)) {
                    UserRepository userRepo = UserRepository.getInstance();
                    userRepo.getUserById(userId).addOnSuccessListener(likerSnapshot -> {
                        User liker = likerSnapshot.toObject(User.class);
                        if (liker != null) {
                            // Notification 객체 생성
                            Notification likeNotification = Notification.createLikeNotification(
                                    post.getUserId(),      // 알림 받는 사람 (포스트 작성자)
                                    liker.getUserId(),     // 알림 보낸 사람 (좋아요 누른 사람)
                                    liker.getUsername(),   // 보낸 사람 이름
                                    liker.getProfilePicUrl(), // 보낸 사람 프로필 사진
                                    postId                 // 관련 포스트 ID
                            );
                            // sendNotificationToUser 호출
                            notificationRepository.sendNotificationToUser(post.getUserId(), likeNotification);
                    }
                    });
                   
            }
            }
            
            return null;
        });
    }
    
    // 댓글 추가
    public Task<DocumentReference> addComment(Comment comment) {
        DocumentReference postRef = firestore.collection(POSTS_COLLECTION).document(comment.getPostId());
        
        // 트랜잭션으로 댓글 추가 및 댓글 카운트 업데이트
        return firestore.runTransaction(transaction -> {
            // 포스트의 댓글 컬렉션에 댓글 추가
            CollectionReference commentsRef = firestore.collection(POSTS_COLLECTION)
                    .document(comment.getPostId())
                    .collection(COMMENTS_COLLECTION);
            
            // 포스트의 댓글 수 업데이트
            DocumentSnapshot postSnapshot = transaction.get(postRef);
            Post post = postSnapshot.toObject(Post.class);
            if (post != null) {
                post.setCommentCount(post.getCommentCount() + 1);
                transaction.set(postRef, post);
            }
            
            // 댓글 추가하고 해당 참조 반환
            DocumentReference newCommentRef = commentsRef.document();
            comment.setCommentId(newCommentRef.getId());
            transaction.set(newCommentRef, comment);
            
            return newCommentRef;
        }).continueWithTask(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                // 포스트 작성자에게 댓글 알림 전송
                return firestore.collection(POSTS_COLLECTION).document(comment.getPostId())
                       .get().continueWithTask(postTask -> {
                    if (postTask.isSuccessful() && postTask.getResult() != null) {
                        Post post = postTask.getResult().toObject(Post.class);
                        if (post != null && !post.getUserId().equals(comment.getUserId())) {
                            // 자신의 포스트가 아닌 경우에만 알림 전송
                            com.example.snapshot.model.Notification notification = com.example.snapshot.model.Notification
                                    .createCommentNotification(
                                            post.getUserId(),
                                            comment.getUserId(),
                                            comment.getUserName(),
                                            comment.getUserProfileImageUrl(),
                                            post.getPostId(),
                                            comment.getText());
                            
                            notificationRepository.sendNotificationToUser(post.getUserId(), notification);
                        }
                    }
                    return Tasks.forResult(task.getResult());
                });
            } else {
                return task;
            }
        });
    }
    
    // 포스트의 댓글 가져오기
    public Query getCommentsForPost(String postId) {
        return firestore.collection(POSTS_COLLECTION)
                .document(postId)
                .collection(COMMENTS_COLLECTION)
                .orderBy("creationDate", Query.Direction.ASCENDING);
    }
    
    // 포스트 삭제
    public Task<Void> deletePost(String postId) {
        DocumentReference postRef = firestore.collection(POSTS_COLLECTION).document(postId);
        
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot postSnapshot = transaction.get(postRef);
            Post post = postSnapshot.toObject(Post.class);
            
            if (post != null) {
                // 1. 포스트에 연결된 태그-포스트 매핑 삭제
                List<Tag> tags = post.getTags();
                if (tags != null) {
                    for (Tag tag : tags) {
                        String mappingId = tag.getTagId() + "_" + postId;
                        DocumentReference mappingRef = firestore.collection(POST_TAGS_COLLECTION).document(mappingId);
                        transaction.delete(mappingRef);
                    }
                }
                
                // 2. 포스트 문서 삭제
                transaction.delete(postRef);
            }
            
            return null;
        }).continueWithTask(task -> {
            if (task.isSuccessful()) {
                // 3. 포스트 이미지 삭제 (Storage)
                // 실제 구현에서는 포스트의 이미지 URL에서 파일 경로를 추출하여 삭제해야 합니다.
                
                // 4. 포스트의 댓글 삭제 (Firestore 제한으로 클라이언트에서 하위 컬렉션을 직접 삭제할 수 없습니다)
                // 이는 Firebase Cloud Functions을 통해 구현하는 것이 좋습니다.
                
                return Tasks.forResult(null);
            } else {
                return Tasks.forException(task.getException());
            }
        });
    }
    
    // 장소 이름(위치 태그 이름)으로 포스트 검색 (PlaceSearchFragment 검색용)
    public Task<List<Post>> searchPostsByLocationTagName(String placeName) {
        TagRepository tagRepository = TagRepository.getInstance();
        
        // 1. TagRepository를 사용하여 장소 이름으로 location 타입 태그 검색
        return tagRepository.searchTagsByTypeAndName(Tag.TYPE_LOCATION, placeName)
            .get()
            .continueWithTask(task -> {
                if (task.isSuccessful() && task.getResult() != null) {
                    List<String> locationTagIds = new ArrayList<>();
                    for (DocumentSnapshot tagDoc : task.getResult().getDocuments()) {
                        Tag tag = tagDoc.toObject(Tag.class);
                        if (tag != null) {
                            locationTagIds.add(tag.getTagId());
                        }
                    }
                    
                    if (locationTagIds.isEmpty()) {
                        // 해당 이름을 가진 위치 태그가 없으면 빈 리스트 반환
                        return Tasks.forResult(new ArrayList<>());
                    }
                    
                    // 2. 찾은 위치 태그 ID들로 게시글 검색 (기존 메소드 활용)
                    return searchPostsByMultipleTags(locationTagIds);
                } else {
                    // 태그 검색 실패 시 빈 리스트 반환
                    android.util.Log.e(TAG, "Error searching location tags by name", task.getException());
                    return Tasks.forResult(new ArrayList<>());
            }
        });
    }
} 