package com.example.snapshot.ui.search;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.snapshot.R;
import com.example.snapshot.ui.search.TouchableMapView;
import com.example.snapshot.model.Post;
import com.example.snapshot.model.Tag;
import com.example.snapshot.repository.PostRepository;
import com.example.snapshot.ui.home.PostAdapter;
import com.example.snapshot.ui.post.PostDetailActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaceSearchFragment extends Fragment implements OnMapReadyCallback {
    
    private static final String TAG_LOG = "PlaceSearchFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private static final float DEFAULT_ZOOM = 14f;
    
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvNoResults;
    private View mapViewContainer;
    private View listViewContainer;
    private TouchableMapView mapView;
    private GoogleMap googleMap;
    private PostAdapter listAdapter;
    private List<Post> postListForMap = new ArrayList<>();
    private List<Post> sortedPostListForList = new ArrayList<>();
    private PostRepository postRepository;
    private FusedLocationProviderClient fusedLocationClient;
    private Location lastKnownLocation;
    private com.google.android.material.button.MaterialButtonToggleGroup toggleGroup;
    
    private ClusterManager<PostClusterItem> clusterManager;
    
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    
    public static class PostClusterItem implements ClusterItem {
        private final LatLng position;
        private final String title;
        private final String snippet;
        private final Post post;

        public PostClusterItem(double lat, double lng, String title, String snippet, Post post) {
            this.position = new LatLng(lat, lng);
            this.title = title;
            this.snippet = snippet;
            this.post = post;
        }

        @NonNull
        @Override
        public LatLng getPosition() {
            return position;
        }

        @Nullable
        @Override
        public String getTitle() {
            return title;
        }

        @Nullable
        @Override
        public String getSnippet() {
            return snippet;
        }
        
        public Post getPost() {
            return post;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PostClusterItem that = (PostClusterItem) o;
            return post != null && that.post != null && post.getPostId().equals(that.post.getPostId()); 
        }

        @Override
        public int hashCode() {
            return post != null ? post.getPostId().hashCode() : 0;
        }
    }
    
    private class PostMarkerRenderer extends DefaultClusterRenderer<PostClusterItem> {
        
        private final android.content.Context context;
        private final Bitmap placeholderBitmap;
        private final int markerSize = 100; // 마커 크기 (px)
        private final int borderSize = 5;  // 테두리 두께 (px)
        private final int borderColor = Color.WHITE; // 테두리 색상
        private final int clusterIconSize = 120; // 클러스터 아이콘 크기 (px)
        private final Paint textPaint;

        public PostMarkerRenderer(android.content.Context context, GoogleMap map, ClusterManager<PostClusterItem> clusterManager) {
            super(context, map, clusterManager);
            this.context = context.getApplicationContext();
            // 원형 + 테두리 플레이스홀더 생성
            Bitmap tempPlaceholder = createPlaceholderBitmap(markerSize);
            this.placeholderBitmap = addBorderToCircularBitmap(tempPlaceholder, borderSize, borderColor);

            // 클러스터 텍스트용 Paint 초기화
            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(40f); // 텍스트 크기
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFakeBoldText(true);
        }

        // 개별 마커 아이콘 설정 (원형 + 테두리)
        @Override
        protected void onBeforeClusterItemRendered(@NonNull PostClusterItem item, @NonNull MarkerOptions markerOptions) {
            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(placeholderBitmap))
                         .title(item.getTitle())
                         .snippet(item.getSnippet());

            if (item.getPost() != null && item.getPost().getImageUrl() != null && !item.getPost().getImageUrl().isEmpty()) {
                Glide.with(context)
                    .asBitmap()
                    .load(item.getPost().getImageUrl())
                    .apply(RequestOptions.bitmapTransform(new CircleCrop())) // 원형으로 만들기
                    .override(markerSize - (borderSize * 2)) // 테두리 두께만큼 작게 로드
                    .error(placeholderBitmap) // 에러 시에도 원형+테두리 플레이스홀더 사용
                    .into(new CustomTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                            Marker marker = getMarker(item);
                            if (marker != null) {
                                try {
                                    Bitmap borderedBitmap = addBorderToCircularBitmap(resource, borderSize, borderColor);
                                    marker.setIcon(BitmapDescriptorFactory.fromBitmap(borderedBitmap));
                                } catch (IllegalArgumentException e) {
                                    Log.e(TAG_LOG, "Error setting loaded marker icon: " + e.getMessage());
                                }
                            } else {
                                Log.w(TAG_LOG, "Marker not found when resource ready: " + item.getTitle());
                            }
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            // 필요시 플레이스홀더 다시 설정 (이미 설정됨)
                            Marker marker = getMarker(item);
                            if (marker != null) {
                                marker.setIcon(BitmapDescriptorFactory.fromBitmap(placeholderBitmap));
                            }
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            // 로드 실패 시 플레이스홀더 사용 (이미 설정됨)
                            Marker marker = getMarker(item);
                             if (marker != null) {
                                 marker.setIcon(BitmapDescriptorFactory.fromBitmap(placeholderBitmap));
                            }
                            Log.e(TAG_LOG, "Failed to load marker image: " + item.getTitle());
                        }
                    });
            }
        }

        // 클러스터 아이콘 설정 (파란 원 + 개수 텍스트)
        @Override
        protected void onBeforeClusterRendered(@NonNull Cluster<PostClusterItem> cluster, @NonNull MarkerOptions markerOptions) {
            Bitmap clusterIcon = createClusterIconBitmap(cluster.getSize(), clusterIconSize);
            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(clusterIcon))
                         .anchor(0.5f, 0.5f); // 아이콘 중심을 기준으로 위치 설정
        }

        // 클러스터 개수가 갱신될 때 아이콘 업데이트 (예: 지도 축소/확대 시)
        @Override
        protected void onClusterUpdated(@NonNull Cluster<PostClusterItem> cluster, @NonNull Marker marker) {
             Bitmap clusterIcon = createClusterIconBitmap(cluster.getSize(), clusterIconSize);
             marker.setIcon(BitmapDescriptorFactory.fromBitmap(clusterIcon));
        }

        // 원형 비트맵에 테두리 추가 헬퍼
        private Bitmap addBorderToCircularBitmap(Bitmap srcBitmap, int borderSize, int borderColor) {
            if (srcBitmap == null) return null;
            int diameter = srcBitmap.getWidth(); // Assume width and height are same for circle
            int targetDiameter = diameter + borderSize * 2;

            Bitmap output = Bitmap.createBitmap(targetDiameter, targetDiameter, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);

            Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setColor(borderColor);
            borderPaint.setStyle(Paint.Style.FILL);

            // Draw the border circle
            canvas.drawCircle(targetDiameter / 2f, targetDiameter / 2f, targetDiameter / 2f, borderPaint);

            // Draw the source bitmap in the center
            canvas.drawBitmap(srcBitmap, borderSize, borderSize, null);

            return output;
        }

        // 클러스터 아이콘 생성 헬퍼 (파란 원 + 흰색 텍스트)
        private Bitmap createClusterIconBitmap(int clusterSize, int iconSize) {
            Bitmap bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            backgroundPaint.setColor(ContextCompat.getColor(context, R.color.blue)); // 파란색 배경
            backgroundPaint.setStyle(Paint.Style.FILL);

            // Draw background circle
            canvas.drawCircle(iconSize / 2f, iconSize / 2f, iconSize / 2f, backgroundPaint);

            // Draw cluster size text
            String text = String.valueOf(clusterSize);
            float textHeight = textPaint.descent() - textPaint.ascent();
            float textOffset = (textHeight / 2) - textPaint.descent();
            canvas.drawText(text, iconSize / 2f, iconSize / 2f + textOffset, textPaint);

            return bitmap;
        }

        // 임시 플레이스홀더 생성 (회색 원)
        private Bitmap createPlaceholderBitmap(int size) {
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(ContextCompat.getColor(context, R.color.grey_light)); // 밝은 회색
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
            return bitmap;
        }
    }
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_place_search, container, false);
        Log.d(TAG_LOG, "onCreateView: Initializing view components.");
        
        recyclerView = view.findViewById(R.id.recycler_search_results);
        progressBar = view.findViewById(R.id.progress_bar);
        tvNoResults = view.findViewById(R.id.tv_no_results);
        mapViewContainer = view.findViewById(R.id.map_view_container);
        listViewContainer = view.findViewById(R.id.list_view_container);
        mapView = view.findViewById(R.id.map_view);
        toggleGroup = view.findViewById(R.id.toggle_view);
        
        view.findViewById(R.id.btn_my_location).setOnClickListener(v -> moveToMyLocation());
        
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                Log.d(TAG_LOG, "Toggle button checked: " + (checkedId == R.id.btn_map_view ? "Map" : "List"));
                toggleMapView(checkedId == R.id.btn_map_view);
            }
        });
        
        postRepository = PostRepository.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        
        mapView.onCreate(savedInstanceState);
        Log.d(TAG_LOG, "onCreateView: Requesting map initialization.");
        mapView.getMapAsync(this);
        
        setupRecyclerView();
        
        boolean showMapInitially = toggleGroup.getCheckedButtonId() == R.id.btn_map_view;
        Log.d(TAG_LOG, "onCreateView: Initial view state - showMap=" + showMapInitially);
        mapViewContainer.setVisibility(showMapInitially ? View.VISIBLE : View.GONE);
        listViewContainer.setVisibility(showMapInitially ? View.GONE : View.VISIBLE);
        
        if (!showMapInitially) {
            Log.d(TAG_LOG, "onCreateView: List view is initial. Triggering nearby posts load.");
            getDeviceLocationAndLoadNearbyPosts();
        }
        
        return view;
    }
    
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        Log.d(TAG_LOG, "onMapReady: Map is ready. Setting up map components.");
        googleMap = map;
        
        clusterManager = new ClusterManager<>(requireContext(), googleMap);
        
        clusterManager.setRenderer(new PostMarkerRenderer(requireContext(), googleMap, clusterManager));
        
        googleMap.setOnCameraIdleListener(clusterManager);
        googleMap.setOnMarkerClickListener(clusterManager);
        
        clusterManager.setOnClusterItemClickListener(item -> {
            Log.d(TAG_LOG, "Cluster item clicked: " + item.getTitle());
            if (item.getPost() != null) {
                navigateToPostDetail(item.getPost());
            }
            return true;
        });
        
        clusterManager.setOnClusterClickListener(cluster -> {
            Log.d(TAG_LOG, "Cluster clicked. Size: " + cluster.getSize());
            
            LatLngBounds.Builder builder = LatLngBounds.builder();
            for (PostClusterItem item : cluster.getItems()) {
                builder.include(item.getPosition());
            }
            LatLngBounds bounds = builder.build();
            
            int padding = 100;
            try {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
            } catch (IllegalStateException e) {
                 Log.e(TAG_LOG, "Error animating camera to cluster bounds: " + e.getMessage());
            }

            return true;
        });
        
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
            googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        }
        
        getDeviceLocationAndLoadMapMarkers();
    }
    
    private void setupRecyclerView() {
        listAdapter = new PostAdapter(sortedPostListForList, requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(listAdapter);
        
        listAdapter.setOnPostInteractionListener(new PostAdapter.OnPostInteractionListener() {
            @Override
            public void onLikeClicked(int position) {}
            @Override
            public void onCommentClicked(int position) { if (position >= 0 && position < sortedPostListForList.size()) navigateToPostDetail(sortedPostListForList.get(position)); }
            @Override
            public void onShareClicked(int position) {}
            @Override
            public void onTagClicked(int postPosition, int tagPosition) { 
                 // TODO: 태그 클릭 시 동작 (예: 태그 검색 화면으로 이동?)
            }
            @Override
            public void onUserProfileClicked(int position) {
                // TODO: 사용자 프로필 클릭 시 동작 (예: 프로필 화면 이동?)
            }
        });
    }
    
    private void getDeviceLocationAndLoadMapMarkers() {
        Log.d(TAG_LOG, "getDeviceLocationAndLoadMapMarkers: Starting process.");
        if (googleMap == null) {
            Log.w(TAG_LOG, "getDeviceLocationAndLoadMapMarkers: GoogleMap not ready yet.");
            return;
        }
        try {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG_LOG, "getDeviceLocationAndLoadMapMarkers: Permission granted. Getting location.");
                googleMap.setMyLocationEnabled(true);
                googleMap.getUiSettings().setMyLocationButtonEnabled(false);
                fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(requireActivity(), location -> {
                        if (location != null) {
                            lastKnownLocation = location;
                            Log.d(TAG_LOG, "getDeviceLocationAndLoadMapMarkers: Location success (" + location.getLatitude() + "," + location.getLongitude() + "). Loading posts for map.");
                            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), DEFAULT_ZOOM));
                        } else {
                            Log.w(TAG_LOG, "getDeviceLocationAndLoadMapMarkers: Location is null. Using default.");
                            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(37.5665, 126.9780), DEFAULT_ZOOM));
                        }
                        loadPostsForMap();
                                })
                    .addOnFailureListener(requireActivity(), e -> {
                        Log.e(TAG_LOG, "getDeviceLocationAndLoadMapMarkers: Location failure.", e);
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(37.5665, 126.9780), DEFAULT_ZOOM));
                        loadPostsForMap();
                                });
                    } else {
                Log.d(TAG_LOG, "getDeviceLocationAndLoadMapMarkers: Permission not granted. Requesting.");
                requestLocationPermission();
            }
        } catch (SecurityException e) {
            Log.e(TAG_LOG, "getDeviceLocationAndLoadMapMarkers: SecurityException.", e);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(37.5665, 126.9780), DEFAULT_ZOOM));
            loadPostsForMap();
        }
    }
    
    private void getDeviceLocationAndLoadNearbyPosts() {
        Log.d(TAG_LOG, "getDeviceLocationAndLoadNearbyPosts: Starting process.");
        try {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG_LOG, "getDeviceLocationAndLoadNearbyPosts: Permission granted. Getting location.");
                fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(requireActivity(), location -> {
                        if (location != null) {
                            lastKnownLocation = location;
                            Log.d(TAG_LOG, "getDeviceLocationAndLoadNearbyPosts: Location success (" + location.getLatitude() + "," + location.getLongitude() + "). Loading nearby posts for list.");
                            loadNearbyPostsForList();
                        } else {
                            Log.w(TAG_LOG, "getDeviceLocationAndLoadNearbyPosts: Location is null.");
                            mainThreadHandler.post(() -> Toast.makeText(getContext(), "현재 위치를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show());
                            showLoading(false); updateResultsVisibility(false);
                    }
                })
                     .addOnFailureListener(requireActivity(), e -> {
                         Log.e(TAG_LOG, "getDeviceLocationAndLoadNearbyPosts: Location failure.", e);
                         mainThreadHandler.post(() -> Toast.makeText(getContext(), "위치 정보 가져오기 실패", Toast.LENGTH_SHORT).show());
                         showLoading(false); updateResultsVisibility(false);
                    });
            } else {
                Log.d(TAG_LOG, "getDeviceLocationAndLoadNearbyPosts: Permission not granted. Requesting.");
                requestLocationPermission();
                showLoading(false); updateResultsVisibility(false);
            }
        } catch (SecurityException e) {
            Log.e(TAG_LOG, "getDeviceLocationAndLoadNearbyPosts: SecurityException.", e);
            showLoading(false); updateResultsVisibility(false);
        }
    }
    
    private void loadPostsForMap() {
        Log.d(TAG_LOG, "loadPostsForMap: Loading posts for map markers...");
        showLoading(true);
        postRepository.getRecentPosts(50)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                Log.d(TAG_LOG, "loadPostsForMap: Firestore success. Found " + queryDocumentSnapshots.size() + " documents.");
                postListForMap.clear();
                        for (DocumentSnapshot document : queryDocumentSnapshots) {
                    Post post = document.toObject(Post.class);
                    if (post != null) {
                        postListForMap.add(post);
                            }
                        }
                Log.d(TAG_LOG, "loadPostsForMap: Parsed " + postListForMap.size() + " posts. Adding markers.");
                            addMarkersToMap();
                        showLoading(false);
                    })
                    .addOnFailureListener(e -> {
                Log.e(TAG_LOG, "loadPostsForMap: Error loading posts for map", e);
                        showLoading(false);
                mainThreadHandler.post(() -> Toast.makeText(requireContext(), "지도 데이터 로드 오류", Toast.LENGTH_SHORT).show());
                    });
    }
    
    private void loadNearbyPostsForList() {
        if (lastKnownLocation == null) {
            Log.w(TAG_LOG, "loadNearbyPostsForList: Current location is unknown. Cannot load.");
            mainThreadHandler.post(() -> Toast.makeText(getContext(), "현재 위치를 알 수 없어 주변 게시물을 표시할 수 없습니다.", Toast.LENGTH_LONG).show());
            updateResultsVisibility(false);
            showLoading(false);
            return;
        }
        Log.d(TAG_LOG, "loadNearbyPostsForList: Starting background task to load and sort nearby posts near (" + lastKnownLocation.getLatitude() + "," + lastKnownLocation.getLongitude() + ").");
        showLoading(true);
        
        executorService.submit(() -> {
            Log.d(TAG_LOG, "[BG] loadNearbyPostsForList: Background task started.");
            // 실제 Firestore 쿼리 (예: 최근 100개)
            // Geo-query 최적화가 필요할 수 있음
            Task<QuerySnapshot> fetchTask = postRepository.getRecentPosts(100).get(); 

            try {
                // Firebase Task가 완료될 때까지 대기 (실제 프로덕션에서는 addOnSuccessListener 사용 권장)
                // 이 방식은 워커 스레드를 블로킹하지만, ExecutorService 내에서는 괜찮을 수 있음.
                // 더 나은 방식은 Task의 결과를 다음 단계로 넘기는 것임.
                QuerySnapshot queryDocumentSnapshots = com.google.android.gms.tasks.Tasks.await(fetchTask);

                Log.d(TAG_LOG, "[BG] Firestore fetch completed. Found " + queryDocumentSnapshots.size() + " documents.");
                List<Post> fetchedPosts = new ArrayList<>();
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                    Post post = document.toObject(Post.class);
                    if (post != null) {
                        fetchedPosts.add(post);
                        }
                    }
                Log.d(TAG_LOG, "[BG] Parsed " + fetchedPosts.size() + " posts.");

                List<Map.Entry<Post, Float>> postsWithDistance = new ArrayList<>();
                for (Post post : fetchedPosts) {
                    LatLng postLatLng = getLatLngFromPost(post);
                    if (postLatLng != null) {
                        float[] results = new float[1];
                        Location.distanceBetween(
                                lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude(),
                                postLatLng.latitude, postLatLng.longitude,
                                results);
                        postsWithDistance.add(new HashMap.SimpleEntry<>(post, results[0]));
                    }
                }
                Log.d(TAG_LOG, "[BG] Calculated distances for " + postsWithDistance.size() + " posts.");

                Collections.sort(postsWithDistance, Comparator.comparing(Map.Entry::getValue));
                Log.d(TAG_LOG, "[BG] Sorted posts by distance.");

                List<Post> sortedPosts = new ArrayList<>();
                for (Map.Entry<Post, Float> entry : postsWithDistance) {
                    sortedPosts.add(entry.getKey());
                }

                mainThreadHandler.post(() -> {
                    sortedPostListForList.clear();
                    sortedPostListForList.addAll(sortedPosts);
                    listAdapter.notifyDataSetChanged();
                    updateResultsVisibility(!sortedPostListForList.isEmpty());
                    showLoading(false);
                    Log.d(TAG_LOG, "[Main] Updated list view with " + sortedPostListForList.size() + " sorted posts.");
                });

            } catch (Exception e) { // Tasks.await() 또는 기타 예외 처리
                Log.e(TAG_LOG, "[BG] Error during background processing or Firestore fetch", e);
                mainThreadHandler.post(() -> {
                    Toast.makeText(getContext(), "주변 게시글 처리 오류", Toast.LENGTH_SHORT).show();
                    showLoading(false);
                    updateResultsVisibility(false);
                });
            }
                });
    }
    
    private void updateResultsVisibility(boolean hasResults) {
        if (tvNoResults == null || recyclerView == null) return;
        mainThreadHandler.post(() -> {
            tvNoResults.setText(hasResults ? "" : "주변 게시물이 없습니다.");
            tvNoResults.setVisibility(hasResults ? View.GONE : View.VISIBLE);
            recyclerView.setVisibility(hasResults ? View.VISIBLE : View.GONE);
        });
    }
    
    private void toggleMapView(boolean showMap) {
        if (mapViewContainer == null || listViewContainer == null) return;
        mainThreadHandler.post(() -> {
             mapViewContainer.setVisibility(showMap ? View.VISIBLE : View.GONE);
             listViewContainer.setVisibility(showMap ? View.GONE : View.VISIBLE);
        });

        if (showMap) {
             Log.d(TAG_LOG, "toggleMapView: Switched to Map view.");
             // 지도 데이터 로드가 필요한 경우 (예: 첫 로드 이후 다시 지도 탭 클릭 시)
             if (googleMap != null && postListForMap.isEmpty()) { // 지도는 준비됐지만 마커가 없다면 로드 시도
                 Log.d(TAG_LOG, "toggleMapView: Map view selected and markers are empty, trying to load map markers.");
                 getDeviceLocationAndLoadMapMarkers();
             }
        } else {
             Log.d(TAG_LOG, "toggleMapView: Switched to List view. Triggering nearby posts load.");
             getDeviceLocationAndLoadNearbyPosts();
        }
    }
    
    private void showLoading(boolean isLoading) {
        if (progressBar != null) {
             mainThreadHandler.post(() -> progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE));
        }
    }
    
    private void addMarkersToMap() {
        Log.d(TAG_LOG, "addMarkersToMap: Starting. Posts available for map: " + postListForMap.size());
        if (googleMap == null || clusterManager == null) {
            Log.w(TAG_LOG, "addMarkersToMap: GoogleMap or ClusterManager is null. Cannot add markers.");
            return;
        }
        clusterManager.clearItems();
        int markerCount = 0;
        for (Post post : postListForMap) {
            LatLng position = getLatLngFromPost(post);
            if (position != null) {
                // Log.d(TAG_LOG, "addMarkersToMap: Adding marker for post " + post.getPostId() + " at " + position);
                clusterManager.addItem(new PostClusterItem(position.latitude, position.longitude, post.getUserName(), post.getCaption(), post));
                markerCount++;
            } else {
                 Log.d(TAG_LOG, "addMarkersToMap: Skipped post " + post.getPostId() + " due to invalid/null location.");
            }
        }
        Log.d(TAG_LOG, "addMarkersToMap: Finished. Added " + markerCount + " markers.");
        clusterManager.cluster();
    }
    
    private LatLng getLatLngFromPost(Post post) {
        if (post == null) { /*Log.d(TAG_LOG, "getLatLngFromPost: Post is null.");*/ return null; }
        if (post.getTags() == null || post.getTags().isEmpty()) { /*Log.d(TAG_LOG, "getLatLngFromPost: Post " + post.getPostId() + " has no tags.");*/ return null; }
        for (Tag tag : post.getTags()) {
            if (Tag.TYPE_LOCATION.equals(tag.getTagType()) && tag.getTagData() != null) {
                Map<String, Object> data = tag.getTagData();
                if (data.containsKey("coordinates") && data.get("coordinates") instanceof GeoPoint) {
                try {
                        GeoPoint geoPoint = (GeoPoint) data.get("coordinates");
                        double latitude = geoPoint.getLatitude();
                        double longitude = geoPoint.getLongitude();
                        if (latitude == 0.0 && longitude == 0.0) {
                             Log.w(TAG_LOG, "getLatLngFromPost: Invalid coordinates (0.0, 0.0) for post " + post.getPostId() + ". Skipping.");
                             return null;
                        }
                        return new LatLng(latitude, longitude);
                    } catch (ClassCastException | NullPointerException e) {
                        Log.e(TAG_LOG, "getLatLngFromPost: Error processing GeoPoint for post " + post.getPostId(), e);
                }
            }
        }
        }
        // Log.d(TAG_LOG, "getLatLngFromPost: No valid location tag found for post " + post.getPostId());
        return null;
    }
    
    private void requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
            Toast.makeText(requireContext(), "위치 기능을 사용하기 위해 권한이 필요합니다", Toast.LENGTH_LONG).show();
        }
        
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG_LOG, "onRequestPermissionsResult: Received result for code " + requestCode);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG_LOG, "onRequestPermissionsResult: Permission GRANTED.");
                if (mapViewContainer != null && mapViewContainer.getVisibility() == View.VISIBLE) {
                    Log.d(TAG_LOG, "onRequestPermissionsResult: Map view active. Reloading map markers.");
                    getDeviceLocationAndLoadMapMarkers();
                } else if (listViewContainer != null && listViewContainer.getVisibility() == View.VISIBLE) {
                    Log.d(TAG_LOG, "onRequestPermissionsResult: List view active. Reloading nearby posts.");
                    getDeviceLocationAndLoadNearbyPosts();
                } else {
                    Log.d(TAG_LOG, "onRequestPermissionsResult: No specific view visible after permission grant. Defaulting to load list.");
                     // 뷰 상태가 불명확하면 기본적으로 목록 로드 시도 (혹은 지도)
                     getDeviceLocationAndLoadNearbyPosts(); 
                }
            } else {
                Log.w(TAG_LOG, "onRequestPermissionsResult: Permission DENIED.");
                mainThreadHandler.post(() -> Toast.makeText(requireContext(), "위치 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show());
                updateResultsVisibility(false);
                if (googleMap != null) { // 기본 위치로 지도 로드
                     googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(37.5665, 126.9780), DEFAULT_ZOOM));
                     loadPostsForMap(); 
                }
            }
        }
    }
    
    private void navigateToPostDetail(Post post) {
        if (getContext() == null || post == null || post.getPostId() == null) return;
        Intent intent = new Intent(requireContext(), PostDetailActivity.class);
        intent.putExtra(PostDetailActivity.EXTRA_POST_ID, post.getPostId());
        startActivity(intent);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if(mapView != null) mapView.onResume();
    }
    
    @Override
    public void onPause() {
        super.onPause();
        if(mapView != null) mapView.onPause();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG_LOG, "onDestroy: Shutting down ExecutorService.");
        if (mapView != null) mapView.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
    
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if(mapView != null) mapView.onLowMemory();
    }
    
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if(mapView != null) mapView.onSaveInstanceState(outState);
    }

    public void clearResults() {
        Log.d(TAG_LOG, "clearResults: Clearing all post data and UI.");
        if (postListForMap != null) postListForMap.clear();
        if (sortedPostListForList != null) sortedPostListForList.clear();
        if (clusterManager != null) {
            clusterManager.clearItems();
            clusterManager.cluster();
        }
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        updateResultsVisibility(false);
        showLoading(false);
    }

    public void search(String query) {
        if (query == null || query.trim().isEmpty()) {
            Log.d(TAG_LOG, "search: Query is empty. Loading nearby posts for list view.");
            getDeviceLocationAndLoadNearbyPosts();
            return;
        }
        Log.d(TAG_LOG, "search: Searching for place name: " + query.trim());
        showLoading(true);
        postRepository.searchPostsByLocationTagName(query.trim())
            .addOnSuccessListener(posts -> {
                Log.d(TAG_LOG, "Search successful. Found " + posts.size() + " posts for place: " + query.trim());
                sortedPostListForList.clear();
                // TODO: 검색 결과도 거리순 정렬 필요시 lastKnownLocation 사용
                sortedPostListForList.addAll(posts); 
                listAdapter.notifyDataSetChanged();
                updateResultsVisibility(!sortedPostListForList.isEmpty());
                showLoading(false);
                // TODO: 검색 결과를 지도에도 반영할지 결정
            })
            .addOnFailureListener(e -> {
                 Log.e(TAG_LOG, "Error searching posts by location tag name", e);
                 showLoading(false);
                 mainThreadHandler.post(() -> Toast.makeText(requireContext(), "장소 검색 오류", Toast.LENGTH_SHORT).show());
                 updateResultsVisibility(false);
            });
    }

    private void moveToMyLocation() {
        if (lastKnownLocation != null) {
            LatLng currentLatLng = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, DEFAULT_ZOOM));
        } else {
            getDeviceLocationAndLoadMapMarkers();
        }
    }
} 
