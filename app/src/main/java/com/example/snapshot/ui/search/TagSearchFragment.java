package com.example.snapshot.ui.search;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.snapshot.R;
import com.example.snapshot.model.Post;
import com.example.snapshot.repository.PostRepository;
import com.example.snapshot.databinding.DialogEditTagBinding;
import com.example.snapshot.ui.home.PostAdapter;
import com.example.snapshot.ui.post.PostDetailActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class TagSearchFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvNoResults;
    private PostAdapter adapter;
    private List<Post> postList = new ArrayList<>();
    private PostRepository postRepository;

    public TagSearchFragment() {
        // 필수 빈 생성자
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tag_search, container, false);
        
        // 뷰 초기화
        recyclerView = view.findViewById(R.id.recycler_search_results);
        progressBar = view.findViewById(R.id.progress_bar);
        tvNoResults = view.findViewById(R.id.tv_no_results);
        
        // 저장소 초기화
        postRepository = PostRepository.getInstance();
        
        // 리사이클러뷰 설정
        setupRecyclerView();
        
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 초기에 인기 태그 로드 제거 -> 검색 전에는 아무것도 표시하지 않음
        // loadTrendingTags(); 
        showInitialOrNoResultsState(true); // 초기 상태: "검색어를 입력해주세요" 또는 아무것도 표시 안함
    }

    private void setupRecyclerView() {
        adapter = new PostAdapter(postList, requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        recyclerView.setAdapter(adapter);
        
        // 게시글 클릭 리스너 설정
        adapter.setOnPostInteractionListener(new PostAdapter.OnPostInteractionListener() {
            @Override
            public void onLikeClicked(int position) { /* 검색 결과에서는 보통 미구현 */ }

            @Override
            public void onCommentClicked(int position) { /* 검색 결과에서는 보통 미구현, 또는 상세 화면 이동 */ 
                navigateToPostDetail(postList.get(position));
            }
            
            @Override
            public void onShareClicked(int position) { /* 검색 결과에서는 보통 미구현 */ }

            @Override
            public void onTagClicked(int postPosition, int tagPosition) { /* 태그 클릭 시 동작 정의 (예: 해당 태그로 재검색?) */ }
            
            @Override
            public void onUserProfileClicked(int position) { /* 프로필 클릭 시 동작 정의 (예: 프로필 화면 이동) */ }

            @Override
            public void onReportClicked(int position) { /* 필요시 구현 */ }
        });
        
        // 게시글 자체 클릭 리스너 (PostViewHolder 내부에서 처리될 수도 있음, PostAdapter 구현 확인 필요)
        // 필요하다면 여기에 추가 구현
        recyclerView.setOnClickListener( v -> {
             int itemPosition = recyclerView.getChildLayoutPosition(v);
             if (itemPosition != RecyclerView.NO_POSITION) {
                  Post clickedPost = postList.get(itemPosition);
                  navigateToPostDetail(clickedPost);
            }
        });
    }

    public void search(String query) {
        if (query == null || query.trim().isEmpty()) {
            clearListAndShowInitialMessage();
            return;
        }
        
        String trimmedQuery = query.trim(); // Firestore 검색어는 대소문자 구분 가능성 있음, 정책 확인 필요
        showLoading(true);
        
        try {
            // PostRepository의 태그 이름 검색 메소드 사용
            postRepository.searchPostsByTag(trimmedQuery)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        postList.clear();
                        
                        for (DocumentSnapshot document : queryDocumentSnapshots) {
                            Post post = document.toObject(Post.class);
                            if (post != null) {
                                postList.add(post);
                            }
                        }
                        
                        adapter.notifyDataSetChanged();
                        
                        // 검색 결과 유무에 따라 표시
                        showInitialOrNoResultsState(postList.isEmpty());
                        
                        showLoading(false);
                    })
                    .addOnFailureListener(e -> {
                        showLoading(false);
                        Toast.makeText(requireContext(), "게시글 검색 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        showInitialOrNoResultsState(true); // 오류 시에도 초기 상태 표시
                    });
        } catch (Exception e) { // 예를 들어 Repository 메소드가 없을 경우 등
            showLoading(false);
            Toast.makeText(requireContext(), "게시글 검색 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            showInitialOrNoResultsState(true);
        }
    }

    private void navigateToPostDetail(Post post) {
        if (getContext() == null || post == null || post.getPostId() == null) return;
        Intent intent = new Intent(requireContext(), PostDetailActivity.class);
        intent.putExtra(PostDetailActivity.EXTRA_POST_ID, post.getPostId());
        startActivity(intent);
    }

    private void showLoading(boolean isLoading) {
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * 게시글 검색 결과를 직접 표시하는 메서드
     * @param posts 표시할 게시글 목록
     */
    public void displayPostResults(List<Post> posts) {
        if (posts == null) {
            return;
        }
        
        postList.clear();
        postList.addAll(posts);
        adapter.notifyDataSetChanged();
        
        // 검색 결과 유무에 따라 표시
        if (postList.isEmpty()) {
            tvNoResults.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvNoResults.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
        
        // 로딩 상태 숨기기
        showLoading(false);
    }

    // 검색 결과 지우기 및 초기 메시지 표시 (검색어가 없을 때 호출됨)
    public void clearListAndShowInitialMessage() {
        if (postList != null) {
            postList.clear();
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        showInitialOrNoResultsState(true); // true는 목록이 비어있음을 의미
        showLoading(false);
    }
    
    // 검색 결과 지우기 (SearchFragment에서 호출됨)
    public void clearResults() {
        if (postList != null) {
            postList.clear();
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        // clearResults는 검색창의 X 버튼 등으로 외부에서 호출될 수 있으므로, 
        // 여기서는 "검색어를 입력하세요" 같은 초기 메시지보다는 '결과 없음'을 명확히 하거나 아무것도 표시 안하는게 나을 수 있습니다.
        // 사용자의 요구는 "검색 전엔 아무것도 안뜨게" 이므로, tvNoResults도 숨깁니다.
        if (tvNoResults != null) {
            // tvNoResults.setText("검색 결과가 여기에 표시됩니다."); // 또는 다른 적절한 메시지
            // tvNoResults.setVisibility(View.VISIBLE);
            tvNoResults.setVisibility(View.GONE); 
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(View.GONE); 
        }
        showLoading(false);
        // loadTrendingTags(); // 인기 태그 로드 제거
    }

    // 목록 상태에 따라 UI 업데이트 (결과 없음 텍스트 등)
    // isListEmptyOrInitial: 목록이 비어있거나 초기 상태일 때 true
    private void showInitialOrNoResultsState(boolean isListEmptyOrInitial) {
        if (tvNoResults != null) {
            if (isListEmptyOrInitial && recyclerView.getVisibility() == View.GONE) { // 초기 상태거나 검색 결과가 0개일 때
                // tvNoResults.setText("검색어를 입력하여 관련 게시글을 찾아보세요."); // 초기 안내 메시지
                tvNoResults.setVisibility(View.GONE); // 요청사항: 검색 전엔 아무것도 안뜨게
                recyclerView.setVisibility(View.GONE);
            } else if (!isListEmptyOrInitial) { // 검색 결과가 있을 때
                 tvNoResults.setVisibility(View.GONE);
                 recyclerView.setVisibility(View.VISIBLE);
             } else { // 검색 결과가 없을 때 (isListEmptyOrInitial == true)
                 tvNoResults.setText(R.string.no_search_results);
                 tvNoResults.setVisibility(View.VISIBLE);
                 recyclerView.setVisibility(View.GONE);
             }
        }
        if (recyclerView != null) {
            if (!isListEmptyOrInitial) {
                 recyclerView.setVisibility(View.VISIBLE);
                    } else {
                 recyclerView.setVisibility(View.GONE);
             }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 검색어가 없고, 게시글 목록도 비어있다면 초기 상태로.
        // 이 부분은 SearchFragment에서 탭 변경 시 또는 검색어 없을 시 clearResults를 호출해주는 것으로 대체 가능
        // if (currentQuery == null || currentQuery.isEmpty()) { // currentQuery가 없으므로 SearchFragment에서 관리
        //    if (postList.isEmpty()) {
        //        showInitialOrNoResultsState(true);
        //    }
        // }
    }
} 