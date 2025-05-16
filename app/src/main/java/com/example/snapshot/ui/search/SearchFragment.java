package com.example.snapshot.ui.search;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.snapshot.R;
import com.example.snapshot.databinding.FragmentSearchBinding;
import com.example.snapshot.model.Post;
import com.example.snapshot.model.Tag;
import com.example.snapshot.repository.PostRepository;
import com.example.snapshot.repository.TagRepository;
import com.example.snapshot.repository.UserRepository;
import com.example.snapshot.ui.home.TagAdapter;
import com.example.snapshot.ui.tag.TagDetailActivity;
import com.google.android.material.chip.Chip;
import com.example.snapshot.ui.search.UserSearchFragment;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SearchFragment extends Fragment {

    private FragmentSearchBinding binding;
    private TagRepository tagRepository;
    private UserRepository userRepository;
    private TagAdapter trendingTagsAdapter;
    private List<Tag> trendingTags = new ArrayList<>();
    
    // 검색 결과 프래그먼트 인스턴스
    private com.example.snapshot.ui.search.TagSearchFragment tagSearchFragment;
    private UserSearchFragment userSearchFragment;
    private PlaceSearchFragment placeSearchFragment;
    
    // 현재 검색어 저장
    private String currentQuery = "";
    private boolean shouldShowTrendingOnStart = false; // 시작 시 인기 태그 표시 여부 플래그
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Argument에서 이전 화면 ID 확인
        if (getArguments() != null) {
            int previousDestinationId = getArguments().getInt("previousDestinationId", 0);
            // 이전 화면이 홈(태그) 탭이었는지 확인 (ID는 실제 네비게이션 그래프에 맞게 수정 필요)
            shouldShowTrendingOnStart = (previousDestinationId == R.id.navigation_home);
        }
    }
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 저장소 초기화
        tagRepository = TagRepository.getInstance();
        userRepository = UserRepository.getInstance();
        
        // 검색 결과 프래그먼트 초기화
        tagSearchFragment = new com.example.snapshot.ui.search.TagSearchFragment();
        userSearchFragment = new UserSearchFragment();
        placeSearchFragment = new PlaceSearchFragment();
        
        // 리사이클러뷰 초기화
        setupRecyclerView();
        
        // 탭 레이아웃 및 뷰페이저 설정
        setupViewPager();
        
        // 검색 뷰 설정
        setupSearchView();
        
        // 다중 태그 검색 버튼 설정
        setupMultiTagSearch();
        
        // 초기 데이터 로드
        // shouldShowTrendingOnStart가 false이면 (홈 탭 외에서 왔으면) 우선 숨김
        if (!shouldShowTrendingOnStart) {
            binding.tvTrendingTitle.setVisibility(View.GONE);
            binding.recyclerTrendingTags.setVisibility(View.GONE);
        }
        // 태그 데이터는 항상 로드
        loadTrendingTags();
    }
    
    private void setupRecyclerView() {
        trendingTagsAdapter = new TagAdapter(requireContext(), trendingTags);
        binding.recyclerTrendingTags.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.recyclerTrendingTags.setAdapter(trendingTagsAdapter);
        
        // 태그 클릭 리스너 설정
        trendingTagsAdapter.setOnTagClickListener(position -> {
            if (position >= 0 && position < trendingTags.size()) {
                navigateToTagDetail(trendingTags.get(position));
            }
        });
    }
    
    private void setupViewPager() {
        // 탭 레이아웃과 뷰페이저 연결
        binding.viewPager.setAdapter(new SearchPagerAdapter(this));
        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.search_tags);
                    break;
                case 1:
                    tab.setText(R.string.search_users);
                    break;
                case 2:
                    tab.setText(R.string.search_places);
                    break;
            }
        }).attach();
        
        // 탭 변경 리스너 추가
        binding.viewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                // 탭이 변경될 때, 현재 검색어가 없다면 인기 태그 상태 업데이트
                if (currentQuery.isEmpty()) {
                    showTrendingTags(true); 
                }
            }
        });
    }
    
    private void setupSearchView() {
        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (!query.isEmpty()) {
                    currentQuery = query;
                    performSearch(query);
                }
                return true;
            }
            
            @Override
            public boolean onQueryTextChange(String newText) {
                // 검색어가 비어있으면 인기 태그 다시 표시
                if (newText.isEmpty()) {
                    currentQuery = "";
                    showTrendingTags(true); // 인기 태그 표시
                    clearSearchResults(); // 이전 검색 결과 지우기 (선택적)
                }
                // 실시간 검색은 구현하지 않음
                return false;
            }
        });
    }
    
    private void loadTrendingTags() {
        showLoading(true);
        
        // 인기 태그 가져오기
        Query query = tagRepository.getTrendingTags(10);
        
        query.get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    trendingTags.clear();
                    
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        Tag tag = document.toObject(Tag.class);
                        if (tag != null) {
                            trendingTags.add(tag);
                        }
                    }
                    
                    trendingTagsAdapter.notifyDataSetChanged();
                    
                    // 데이터 로드 완료 후, 현재 상태에 맞게 가시성 업데이트
                    if (!trendingTags.isEmpty()) {
                         showTrendingTags(true);
                    } else {
                         // 인기 태그가 없으면 무조건 숨김
                         binding.tvTrendingTitle.setVisibility(View.GONE);
                         binding.recyclerTrendingTags.setVisibility(View.GONE);
                     }
                    
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(requireContext(), "인기 태그 로드 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
    
    private void performSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            // 검색어가 없으면 인기 태그 표시
            showTrendingTags(true);
            return;
        }
        
        // 검색 시작 시 인기 태그 숨기기
        showTrendingTags(false);
        
        currentQuery = query.trim();
        int currentTab = binding.viewPager.getCurrentItem();
        showLoading(true);
        
        try {
            // 각 탭에 따라 저장된 프래그먼트 인스턴스를 사용
            switch (currentTab) {
                case 0: // 태그 검색
                    if (tagSearchFragment != null) {
                        tagSearchFragment.search(currentQuery);
                    }
                    break;
                case 1: // 사용자 검색
                    if (userSearchFragment != null) {
                        userSearchFragment.search(currentQuery);
                    }
                    break;
                case 2: // 장소 검색
                    if (placeSearchFragment != null) {
                        placeSearchFragment.search(currentQuery);
                    }
                    break;
            }
            
            // 다중 태그 검색 버튼 표시
            binding.btnMultiTagSearch.setVisibility(View.VISIBLE);
            
        } catch (Exception e) {
            Toast.makeText(requireContext(), "검색 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            showLoading(false);
        }
    }
    
    private void navigateToTagDetail(Tag tag) {
        // 태그 상세 화면으로 이동
        Intent intent = new Intent(requireContext(), TagDetailActivity.class);
        intent.putExtra(TagDetailActivity.EXTRA_TAG_ID, tag.getTagId());
        startActivity(intent);
    }
    
    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
    
    // 검색 결과를 위한 ViewPager 어댑터
    private class SearchPagerAdapter extends FragmentStateAdapter {
        
        public SearchPagerAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }
        
        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return tagSearchFragment;
                case 1:
                    return userSearchFragment;
                case 2:
                    return placeSearchFragment;
                default:
                    return tagSearchFragment;
            }
        }
        
        @Override
        public int getItemCount() {
            return 3;
        }
    }
    
    private void setupMultiTagSearch() {
        binding.btnMultiTagSearch.setOnClickListener(v -> {
            // 다중 태그 검색 다이얼로그 표시
            showMultiTagSearchDialog();
        });
    }
    
    private void showMultiTagSearchDialog() {
        // 다이얼로그 레이아웃을 동적으로 생성
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_multi_tag_search, null);
        
        // 다이얼로그 빌더 생성
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(R.string.search_multiple_tags);
        builder.setView(dialogView);
        
        // 태그 타입 칩 그룹 찾기
        ChipGroup tagTypeChipGroup = dialogView.findViewById(R.id.chip_group_tag_types);
        RecyclerView selectedTagsRecyclerView = dialogView.findViewById(R.id.recycler_selected_tags);
        Button btnAddTag = dialogView.findViewById(R.id.btn_add_tag);
        Button btnSearch = dialogView.findViewById(R.id.btn_search);
        
        // 선택된 태그 목록
        List<Tag> selectedTags = new ArrayList<>();
        TagAdapter selectedTagsAdapter = new TagAdapter(requireContext(), selectedTags);
        selectedTagsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        selectedTagsRecyclerView.setAdapter(selectedTagsAdapter);
        
        // 태그 제거 기능 설정
        selectedTagsAdapter.setOnTagClickListener(position -> {
            if (position >= 0 && position < selectedTags.size()) {
                selectedTags.remove(position);
                selectedTagsAdapter.notifyDataSetChanged();
            }
        });
        
        // 태그 타입 칩 추가
        addTagTypeChips(tagTypeChipGroup);
        
        // 다이얼로그 생성
        AlertDialog dialog = builder.create();
        
        // 태그 추가 버튼 클릭 이벤트
        btnAddTag.setOnClickListener(v -> {
            // 선택된 태그 타입 가져오기
            int checkedChipId = tagTypeChipGroup.getCheckedChipId();
            if (checkedChipId == View.NO_ID) {
                Toast.makeText(requireContext(), "태그 유형을 선택해주세요", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Chip selectedChip = tagTypeChipGroup.findViewById(checkedChipId);
            String tagType = selectedChip.getTag().toString();
            
            // 태그 검색 다이얼로그 표시
            showTagSearchDialog(tagType, tag -> {
                // 선택된 태그를 목록에 추가
                if (!selectedTags.contains(tag)) {
                    selectedTags.add(tag);
                    selectedTagsAdapter.notifyDataSetChanged();
                }
            });
        });
        
        // 검색 버튼 클릭 이벤트
        btnSearch.setOnClickListener(v -> {
            if (selectedTags.isEmpty()) {
                Toast.makeText(requireContext(), "검색할 태그를 선택해주세요", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // 선택된 태그로 검색 수행
            performMultiTagSearch(selectedTags);
            dialog.dismiss();
        });
        
        dialog.show();
    }
    
    private void addTagTypeChips(ChipGroup chipGroup) {
        // 태그 타입 별 칩 추가
        String[] tagTypes = {
                Tag.TYPE_LOCATION,
                Tag.TYPE_PRODUCT,
                Tag.TYPE_BRAND,
                Tag.TYPE_PRICE,
                Tag.TYPE_EVENT
        };
        
        String[] tagTypeLabels = {
                getString(R.string.tag_type_location),
                getString(R.string.tag_type_product),
                getString(R.string.tag_type_brand),
                getString(R.string.tag_type_price),
                getString(R.string.tag_type_event)
        };
        
        for (int i = 0; i < tagTypes.length; i++) {
            Chip chip = new Chip(requireContext());
            chip.setText(tagTypeLabels[i]);
            chip.setTag(tagTypes[i]);
            chip.setCheckable(true);
            chip.setCheckedIconVisible(true);
            
            chipGroup.addView(chip);
        }
    }
    
    private void showTagSearchDialog(String tagType, TagSelectionListener listener) {
        // 다이얼로그 레이아웃 동적 생성
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_tag_search, null);
        
        // 다이얼로그 빌더 생성
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        
        // 태그 타입에 따른 타이틀 설정
        String tagTypeTitle = "";
        if (tagType.equals(Tag.TYPE_LOCATION)) {
            tagTypeTitle = getString(R.string.tag_type_location);
        } else if (tagType.equals(Tag.TYPE_PRODUCT)) {
            tagTypeTitle = getString(R.string.tag_type_product);
        } else if (tagType.equals(Tag.TYPE_BRAND)) {
            tagTypeTitle = getString(R.string.tag_type_brand);
        } else if (tagType.equals(Tag.TYPE_PRICE)) {
            tagTypeTitle = getString(R.string.tag_type_price);
        } else if (tagType.equals(Tag.TYPE_EVENT)) {
            tagTypeTitle = getString(R.string.tag_type_event);
        }
        
        builder.setTitle(tagTypeTitle + " 태그 선택");
        builder.setView(dialogView);
        
        // 검색 뷰 및 결과 리사이클러뷰 찾기
        SearchView searchView = dialogView.findViewById(R.id.search_view);
        RecyclerView recyclerView = dialogView.findViewById(R.id.recycler_tags);
        TextView emptyView = dialogView.findViewById(R.id.tv_no_results);
        ProgressBar progressBar = dialogView.findViewById(R.id.progress_bar);
        
        // 검색 결과 어댑터 설정
        List<Tag> searchResults = new ArrayList<>();
        TagAdapter adapter = new TagAdapter(requireContext(), searchResults);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        
        // 다이얼로그 생성
        AlertDialog dialog = builder.create();
        
        // 태그 클릭 리스너 설정
        adapter.setOnTagClickListener(position -> {
            if (position >= 0 && position < searchResults.size()) {
                try {
                    // 선택된 태그 반환 및 다이얼로그 닫기
                    Tag selectedTag = searchResults.get(position);
                    listener.onTagSelected(selectedTag);
                    dialog.dismiss();
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "태그 선택 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        // 검색 리스너 설정
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (!query.isEmpty()) {
                    try {
                        // 태그 검색 수행
                        performTagTypeSearch(query, tagType, searchResults, adapter, emptyView, progressBar);
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "검색 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            }
            
            @Override
            public boolean onQueryTextChange(String newText) {
                // 실시간 검색은 구현하지 않음
                return false;
            }
        });
        
        dialog.show();
        
        // 초기에 인기 태그 로드
        progressBar.setVisibility(View.VISIBLE);
        
        try {
            Query query = tagRepository.getTagsByType(tagType);
            query.limit(10).get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        searchResults.clear();
                        for (DocumentSnapshot document : queryDocumentSnapshots) {
                            Tag tag = document.toObject(Tag.class);
                            if (tag != null) {
                                searchResults.add(tag);
                            }
                        }
                        
                        adapter.notifyDataSetChanged();
                        
                        if (searchResults.isEmpty()) {
                            emptyView.setVisibility(View.VISIBLE);
                        } else {
                            emptyView.setVisibility(View.GONE);
                        }
                        
                        progressBar.setVisibility(View.GONE);
                    })
                    .addOnFailureListener(e -> {
                        progressBar.setVisibility(View.GONE);
                        emptyView.setVisibility(View.VISIBLE);
                        Toast.makeText(requireContext(), "태그 로드 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            Toast.makeText(requireContext(), "태그 로드 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void performTagTypeSearch(String query, String tagType, List<Tag> results, 
                                    TagAdapter adapter, TextView emptyView, View progressBar) {
        progressBar.setVisibility(View.VISIBLE);
        
        // 태그 유형별 검색 수행
        Query searchQuery = tagRepository.getTagsByType(tagType);
        
        // 이름으로 검색
        searchQuery.orderBy("name")
                .startAt(query.toLowerCase())
                .endAt(query.toLowerCase() + "\uf8ff")
                .limit(20)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    results.clear();
                    
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        Tag tag = document.toObject(Tag.class);
                        if (tag != null) {
                            results.add(tag);
                        }
                    }
                    
                    // 설명으로도 검색 (두 번째 쿼리)
                    if (results.size() < 20) {
                        searchQuery.orderBy("description")
                                .startAt(query.toLowerCase())
                                .endAt(query.toLowerCase() + "\uf8ff")
                                .limit(20 - results.size())
                                .get()
                                .addOnSuccessListener(descriptionResults -> {
                                    for (DocumentSnapshot document : descriptionResults) {
                                        Tag tag = document.toObject(Tag.class);
                                        if (tag != null && !results.contains(tag)) {
                                            results.add(tag);
                                        }
                                    }
                                    
                                    // 결과 표시
                                    updateSearchResults(results, adapter, emptyView, progressBar);
                                })
                                .addOnFailureListener(e -> {
                                    // 첫 번째 쿼리 결과만 표시
                                    updateSearchResults(results, adapter, emptyView, progressBar);
                                });
                    } else {
                        // 첫 번째 쿼리로 충분한 결과를 얻은 경우
                        updateSearchResults(results, adapter, emptyView, progressBar);
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    emptyView.setVisibility(View.VISIBLE);
                    Toast.makeText(requireContext(), R.string.error_network, Toast.LENGTH_SHORT).show();
                });
    }
    
    private void updateSearchResults(List<Tag> results, TagAdapter adapter, TextView emptyView, View progressBar) {
        adapter.notifyDataSetChanged();
        
        if (results.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
        }
        
        progressBar.setVisibility(View.GONE);
    }
    
    private void performMultiTagSearch(List<Tag> selectedTags) {
        if (selectedTags.isEmpty()) {
            Toast.makeText(requireContext(), "검색할 태그를 선택해주세요", Toast.LENGTH_SHORT).show();
            return;
        }
        
        showLoading(true);
        
        try {
            // 다중 태그 ID 추출
            List<String> tagIds = new ArrayList<>();
            for (Tag tag : selectedTags) {
                tagIds.add(tag.getTagId());
            }
            
            // PostRepository를 사용하여 해당 태그 ID들을 포함하는 게시글 검색
            PostRepository postRepository = PostRepository.getInstance(); // PostRepository 인스턴스 가져오기
            postRepository.searchPostsByMultipleTags(tagIds)
                .addOnSuccessListener(posts -> { // 결과 타입이 List<Post>라고 가정
                    // 태그 탭으로 이동하여 결과 표시
                    binding.viewPager.setCurrentItem(0); // 태그 탭으로 이동
                    
                    // 다중 태그 검색 시에는 인기 태그 숨기기
                    showTrendingTags(false);
                    
                    // TagSearchFragment로 게시글 검색 결과 전달
                    if (tagSearchFragment != null) {
                        tagSearchFragment.displayPostResults(posts); // displayTagResults -> displayPostResults
                        if (!posts.isEmpty()){
                            Toast.makeText(requireContext(), posts.size() + "개의 게시글을 찾았습니다", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), "검색 결과가 없습니다", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(requireContext(), "검색 결과를 표시할 수 없습니다", Toast.LENGTH_SHORT).show();
                    }
                    
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(requireContext(), "게시글 검색 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        } catch (Exception e) {
            showLoading(false);
            Toast.makeText(requireContext(), "다중 태그 검색 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    // 인기 태그 보이기/숨기기 처리
    private void showTrendingTags(boolean show) {
        // 현재 탭이 태그 탭(0번)이고, show가 true일 때만 인기 태그 표시/숨김 로직 적용
        // + 검색어가 없을 때만 인기 태그 표시 (기존 로직 유지)
        boolean isTagTab = binding != null && binding.viewPager.getCurrentItem() == 0;
        boolean isEmptyQuery = currentQuery.isEmpty();
        
        boolean shouldShow = show && isTagTab && isEmptyQuery && !trendingTags.isEmpty();
        int visibility = shouldShow ? View.VISIBLE : View.GONE;

        if (binding != null) {
            binding.tvTrendingTitle.setVisibility(visibility);
            binding.recyclerTrendingTags.setVisibility(visibility);
        }
    }
    
    // 현재 탭의 검색 결과 지우기
    private void clearSearchResults() {
        int currentTab = binding.viewPager.getCurrentItem();
        try {
            switch (currentTab) {
                case 0: // 태그 검색
                    if (tagSearchFragment != null) {
                        tagSearchFragment.clearResults(); // TagSearchFragment에 clearResults 메소드 추가 필요
                    }
                    break;
                case 1: // 사용자 검색
                    if (userSearchFragment != null) {
                        userSearchFragment.clearResults(); // UserSearchFragment에 clearResults 메소드 추가 필요
                    }
                    break;
                case 2: // 장소 검색
                    if (placeSearchFragment != null) {
                        placeSearchFragment.clearResults(); // PlaceSearchFragment에 clearResults 메소드 추가 필요
                    }
                    break;
            }
            // 다중 태그 검색 버튼은 숨기지 않음 (필요에 따라 숨길 수 있음)
            // binding.btnMultiTagSearch.setVisibility(View.GONE);
        } catch (Exception e) {
            // 오류 처리
        }
    }
    
    // 태그 선택 리스너 인터페이스
    interface TagSelectionListener {
        void onTagSelected(Tag tag);
    }
} 