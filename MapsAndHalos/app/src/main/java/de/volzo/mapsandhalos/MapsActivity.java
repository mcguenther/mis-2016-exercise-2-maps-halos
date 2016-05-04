package de.volzo.mapsandhalos;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.shapes.Shape;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.ArraySet;
import android.util.Log;
import android.widget.EditText;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.VisibleRegion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private static final String TAG = "MyActivity";
    private static final int REQUEST_CODE_LOCATION = 2;

    private static final String MARKER_HANDLE = "MARKER";

    private List<MarkerOptions> globalMarker = new ArrayList<MarkerOptions>();
    private List<Circle> globalCircles = new ArrayList<Circle>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(final GoogleMap googleMap) {
        mMap = googleMap;

        // Getting LocationManager object from System Service LOCATION_SERVICE
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Creating a criteria object to retrieve provider
        Criteria criteria = new Criteria();

        // Getting the name of the best provider
        String provider = locationManager.getBestProvider(criteria, true);

        // Getting Current Location
        zoomToCurrentLocation(mMap);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_CODE_LOCATION);
            return;
        }
        mMap.setMyLocationEnabled(true);

        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                EditText etCaptionInput = (EditText) findViewById(R.id.etCaptionInput);
                String markerTitle = etCaptionInput.getText().toString();
                if (markerTitle.length() > 0) {
                    // see https://developers.google.com/maps/documentation/android-api/marker?hl=de#marker_hinzufugen for hints regarding marker creation
                    Marker currentPos = addMarkerToMapAndSave(mMap, new MarkerOptions().position(latLng).title(markerTitle));
                }
            }

        });

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                return false;
            }
        });

        List<MarkerOptions> loadedMaker = loadAllMarker();

        for (MarkerOptions mo : loadedMaker) {
            addMarkerToMap(mMap, mo);
        }

        drawHalos(googleMap);

        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition cameraPosition) {
                drawHalos(googleMap);
            }
        });
    }

    private List<MarkerOptions> loadAllMarker() {
        SharedPreferences sharedPref = getPreferences(MODE_PRIVATE);

        Set<String> markerSet = sharedPref.getStringSet(MARKER_HANDLE, new HashSet<String>());
        List<MarkerOptions> loadedMarker = new ArrayList<MarkerOptions>();

        for (String payload : markerSet) {
            MarkerOptions mo = deserializeMarkerOption(payload);
            loadedMarker.add(mo);
        }

        Log.i(TAG, "loaded marker: " + loadedMarker.size());

        globalMarker = loadedMarker;

        return loadedMarker;
    }

    private void saveAllMarker(List<MarkerOptions> allMarker) {
        SharedPreferences sharedPref = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        Set<String> markerset = new HashSet<String>();

        for (MarkerOptions option : allMarker) {
            markerset.add(serializeMarkerOption(option));
        }

        editor.putStringSet(MARKER_HANDLE, markerset);
        editor.commit();

        globalMarker = allMarker;

        Log.i(TAG, "saved marker: " + markerset.size());
    }

    private void drawHalos(GoogleMap gMap) {

        for (Circle circle : globalCircles) {
            circle.remove();
        }

        VisibleRegion region = mMap.getProjection().getVisibleRegion();
        LatLngBounds bounds = region.latLngBounds;

        Location northEast = new Location("northEast");
        northEast.setLatitude(region.latLngBounds.northeast.latitude);
        northEast.setLongitude(region.latLngBounds.northeast.longitude);
        Location southWest = new Location("southWest");
        southWest.setLatitude(region.latLngBounds.southwest.latitude);
        southWest.setLongitude(region.latLngBounds.southwest.longitude);

        float screenDiag = northEast.distanceTo(southWest);

        for (MarkerOptions mo : globalMarker) {

            // is the Marker on the screen?
            if (bounds.contains(mo.getPosition())) continue;

            LatLng cameraPosition = mMap.getCameraPosition().target;
            Location camera = new Location("l1");
            camera.setLatitude(cameraPosition.latitude);
            camera.setLongitude(cameraPosition.longitude);
            Location marker = new Location("l2");
            marker.setLatitude(mo.getPosition().latitude);
            marker.setLongitude(mo.getPosition().longitude);

            float circleRadius = (float) (camera.distanceTo(marker) - screenDiag/3);

            if (circleRadius < 0) continue;

            // Instantiates a new CircleOptions object and defines the center and radius
            CircleOptions circleOptions = new CircleOptions()
                    .center(mo.getPosition())
                    .strokeColor(Color.RED)
                    .radius(circleRadius); // In meters

            // Get back the mutable Circle
            Circle circle = gMap.addCircle(circleOptions);
            globalCircles.add(circle);
        }
    }

    private Marker addMarkerToMap(GoogleMap gMap, MarkerOptions options) {
        Marker newMarker = gMap.addMarker(options);

        return newMarker;
    }

    private Marker addMarkerToMapAndSave(GoogleMap gMap, MarkerOptions options) {
        Marker newMarker = addMarkerToMap(gMap, options);

        List<MarkerOptions> allMarker = loadAllMarker();

        allMarker.add(options);
        saveAllMarker(allMarker);

        return newMarker;
    }

    private void refreshMap() {
        SharedPreferences sharedPref = getPreferences(MODE_PRIVATE);

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_LOCATION) {
            if (permissions.length == 1 &&
                    permissions[0] == Manifest.permission.ACCESS_FINE_LOCATION &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    System.out.println("still no permissions.");
                    return;
                }
                mMap.setMyLocationEnabled(true);
                zoomToCurrentLocation(mMap);
            } else {
                // Permission was denied. Display an error message.
            }
        }
    }

    private void zoomToCurrentLocation(GoogleMap mMap) {
        // Getting LocationManager object from System Service LOCATION_SERVICE
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Creating a criteria object to retrieve provider
        Criteria criteria = new Criteria();

        // Getting the name of the best provider
        String provider = locationManager.getBestProvider(criteria, true);

        // Getting Current Location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_CODE_LOCATION);
            Location location = locationManager.getLastKnownLocation(provider);

            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(new LatLng(location.getLatitude(), location.getLongitude()))      // Sets the center of the map to location user
                    .zoom(15)                   // Sets the zoom
                    .build();                   // Creates a CameraPosition from the builder
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            return;
        }
        Location location = locationManager.getLastKnownLocation(provider);
        if (location == null) return;

        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(new LatLng(location.getLatitude(), location.getLongitude()))      // Sets the center of the map to location user
                .zoom(15)                   // Sets the zoom
                .build();                   // Creates a CameraPosition from the builder
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    private String serializeMarkerOption(MarkerOptions mo) {
        String title = mo.getTitle();
        String lat = Double.toString(mo.getPosition().latitude);
        String lon = Double.toString(mo.getPosition().longitude);

        return title + ";" + lat + ";" + lon;
    }

    private MarkerOptions deserializeMarkerOption(String payload) {
        MarkerOptions mo = new MarkerOptions();

        String[] snips = payload.split(";");

        String title = snips[0];
        Double lat = Double.parseDouble(snips[1]);
        Double lon = Double.parseDouble(snips[2]);

        mo.title(title);
        mo.position(new LatLng(lat, lon));

        return mo;
    }

}
