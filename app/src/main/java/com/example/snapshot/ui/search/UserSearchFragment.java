package com.example.snapshot.ui.search;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.snapshot.R;
import com.example.snapshot.model.User;
import com.example.snapshot.repository.UserRepository;
import com.example.snapshot.ui.profile.ProfileActivity;
import com.example.snapshot.ui.profile.UserAdapter;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class UserSearchFragment extends Fragment {
    
    private UserRepository userRepository;
    private List<User> searchResults = new ArrayList<>();
    private UserAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private ProgressBar progressBar;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search_results, container, false);
        
        // 저장소 초기화
        userRepository = UserRepository.getInstance();
        
        // 뷰 찾기
        recyclerView = view.findViewById(R.id.recycler_search_results);
        emptyView = view.findViewById(R.id.tv_no_results);
        progressBar = view.findViewById(R.id.progress_bar);
        
        // 어댑터 설정
        adapter = new UserAdapter(requireContext(), searchResults, true);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        
        return view;
    }
    
    public void search(String query) {
        if (userRepository == null) {
            userRepository = UserRepository.getInstance();
        }
        
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        
        // 사용자 검색 - 이름으로 검색
        Query nameQuery = userRepository.searchUsersByName(query);
        
        nameQuery.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    searchResults.clear();
                    
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        User user = document.toObject(User.class);
                        if (user != null) {
                            searchResults.add(user);
                        }
                    }
                    
                    // 검색 결과가 없으면 이메일로도 검색
                    if (searchResults.isEmpty()) {
                        Query emailQuery = userRepository.searchUsersByEmail(query);
                        emailQuery.get()
                                .addOnSuccessListener(emailResults -> {
                                    for (DocumentSnapshot document : emailResults) {
                                        User user = document.toObject(User.class);
                                        if (user != null) {
                                            searchResults.add(user);
                                        }
                                    }
                                    
                                    updateSearchResultsView();
                                })
                                .addOnFailureListener(e -> {
                                    updateSearchResultsView();
                                    Toast.makeText(requireContext(), R.string.error_network, Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        updateSearchResultsView();
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    emptyView.setVisibility(View.VISIBLE);
                    Toast.makeText(requireContext(), R.string.error_network, Toast.LENGTH_SHORT).show();
                });
    }
    
    private void updateSearchResultsView() {
        progressBar.setVisibility(View.GONE);
        
        if (searchResults.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    // 검색 결과 지우기
    public void clearResults() {
        if (searchResults != null) {
            searchResults.clear();
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE); // '결과 없음' 텍스트 숨기기
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(View.GONE); // 리사이클러뷰 숨기기
        }
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE); // 로딩 숨기기
        }
        // 사용자 검색 프래그먼트는 초기 상태 로드가 필요 없을 수 있음
        // 필요하다면 여기에 초기 데이터 로드 로직 추가
    }
} 