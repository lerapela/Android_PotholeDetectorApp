package com.surendramaran.yolov8tflite;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.Locale;

public class LeafLetMap extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private MapView mapView;
    private MyLocationNewOverlay locationOverlay;
    private TextToSpeech tts;
    private DatabaseReference database;
    private FusedLocationProviderClient fusedLocationClient;
    private String lastSpokenAddress = "";  // Track the last spoken address
    private boolean hasSpoken = false; // Flag to track if speech has occurred

    // Other UI elements and variables
    private DrawerLayout drawerLayout;
    private ImageView swipeHintImage;
    private TextView swipeHintText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load OSMDroid configuration
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Configuration.getInstance().load(this, prefs);

        setContentView(R.layout.activity_leaf_let_map);

        // Initialize UI elements
        swipeHintImage = findViewById(R.id.swipe_hint);
        swipeHintText = findViewById(R.id.swipe_hint_text);
        mapView = findViewById(R.id.mapView);
        drawerLayout = findViewById(R.id.drawer_layout);

        // Configure mapView
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(true);

        // Set initial map location (Johannesburg, South Africa)
        GeoPoint southAfrica = new GeoPoint(-26.2041, 28.0473);
        mapView.getController().setCenter(southAfrica);
        mapView.getController().setZoom(10);

        // Initialize location overlay
        locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        locationOverlay.enableMyLocation();
        locationOverlay.enableFollowLocation();
        locationOverlay.setDrawAccuracyEnabled(true);
        mapView.getOverlays().add(locationOverlay);

        // Add swipe hint animation
        ObjectAnimator animation = ObjectAnimator.ofFloat(swipeHintImage, "translationX", 0f, 20f, 0f);
        animation.setDuration(500);
        animation.setRepeatCount(ValueAnimator.INFINITE);
        animation.setRepeatMode(ValueAnimator.REVERSE);
        animation.start();

        // Handle navigation menu clicks
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setBackgroundResource(R.drawable.nav_border);
        navigationView.setPadding(0, 25, 0, 0);
        navigationView.setNavigationItemSelectedListener(menuItem -> {
            handleNavigationItemSelected(menuItem);
            return true;
        });

        // Add back press handling
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    finish();
                }
            }
        });

        // Request permissions
        requestPermissions();

        // Firebase setup
        database = FirebaseDatabase.getInstance().getReference("images");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize TTS
        tts = new TextToSpeech(this, this);

        // Check and request location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startMonitoringPotholes();
        }
    }

    // Handle navigation menu click
    private void handleNavigationItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.map) {
            // Already on map, do nothing
        } else if (menuItem.getItemId() == R.id.detect) {
            startActivity(new Intent(LeafLetMap.this, MainActivity.class));
        } else if (menuItem.getItemId() == R.id.close) {
            finish();
        }
        drawerLayout.closeDrawers();
    }

    // Request location and other permissions
    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_LOCATION_PERMISSION);
                break;
            }
        }
    }

    // Method to start monitoring potholes
    private void startMonitoringPotholes() {
        database.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (ContextCompat.checkSelfPermission(LeafLetMap.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.getLastLocation().addOnSuccessListener(LeafLetMap.this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            mapView.getOverlays().clear();  // Clear previous markers

                            for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                                Double lat = snapshot.child("latitude").getValue(Double.class);
                                Double lng = snapshot.child("longitude").getValue(Double.class);
                                String address = snapshot.child("address").getValue(String.class);
                                String status = snapshot.child("status").getValue(String.class);

                                if (lat != null && lng != null && address != null) {
                                    // Create and customize markers for potholes
                                    Marker potholeMarker = new Marker(mapView);
                                    potholeMarker.setPosition(new GeoPoint(lat, lng));
                                    potholeMarker.setIcon(getResources().getDrawable(
                                            "fixed".equalsIgnoreCase(status) ? R.drawable.green_dot : R.drawable.red_dot));
                                    potholeMarker.setTitle(address);

                                    mapView.getOverlays().add(potholeMarker); // Add the marker to the map

                                    // Provide speech feedback if near and address is different from last spoken
                                    if (location != null && calculateDistance(location.getLatitude(), location.getLongitude(), lat, lng) <= 0.5) {
                                        if (!address.equals(lastSpokenAddress)) {  // Check if this address was already spoken
                                            if (!hasSpoken) { // Check if speech has already occurred
                                                speak("Pothole detected ahead near " + address);
                                                lastSpokenAddress = address;  // Update the last spoken address
                                                hasSpoken = true; // Set the flag to true
                                            }
                                        }
                                    }
                                }
                            }

                            mapView.zoomToBoundingBox(mapView.getBoundingBox(), true);
                        }
                    });
                } else {
                    Toast.makeText(LeafLetMap.this, "Location permission required", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.w("Map", "loadPotholes:onCancelled", databaseError.toException());
            }
        });
    }

    // Calculate distance between two points (in km)
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Earth radius in km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;  // Distance in km
    }

    // Speak text through TTS
    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    // Initialize TTS
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int langResult = tts.setLanguage(Locale.US);
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language is not supported or missing data");
            }
        } else {
            Log.e("TTS", "Initialization failed");
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tts != null) {
            tts.stop();  // Stops any ongoing speech
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hasSpoken = false; // Reset the flag when the activity is resumed
    }
}