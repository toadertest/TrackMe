package com.ant.track.app.fragments;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import com.ant.track.app.R;
import com.ant.track.app.activities.RecordStateListener;
import com.ant.track.app.helper.GoogleAskToEnableLocationService;
import com.ant.track.app.helper.GoogleLocationServicesUtils;
import com.ant.track.app.location.GPSLiveTrackerLocationManager;
import com.ant.track.app.maptools.MapOverlay;
import com.ant.track.lib.content.datasource.RouteDataListener;
import com.ant.track.lib.content.datasource.RouteType;
import com.ant.track.lib.content.factory.RouteDataSourceFactory;
import com.ant.track.lib.content.factory.TrackMeDatabaseUtilsImpl;
import com.ant.track.lib.model.Route;
import com.ant.track.lib.model.RouteCheckPoint;
import com.ant.track.lib.prefs.PreferenceUtils;
import com.ant.track.lib.service.RecordingState;
import com.ant.track.lib.stats.RouteStats;
import com.ant.track.lib.utils.LocationUtils;
import com.ant.track.ui.dialogs.CustomFragmentDialog;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.LocationSource;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;

import java.util.ArrayList;
import java.util.EnumSet;

/**
 * Fragment in which a google map resides.
 */
public class LocationFragment extends Fragment implements RouteDataListener {

    private static final String CUSTOM_TAG = LocationFragment.class.getCanonicalName();
    private static final float DEFAULT_ZOOM_LEVEL = 18f;
    private static final String TAG = LocationFragment.class.getSimpleName();
    private static final int REQUEST_CODE_LOCATION = 400;
    private GoogleMap mMap;
    private Route currentRoute;
    private Location lastLocation;
    private boolean reload = true;
    private View rootView;
    private int recordingGpsAccuracy = PreferenceUtils.RECORDING_GPS_ACCURACY_DEFAULT;
    private LocationListener locationListener;
    private LocationSource.OnLocationChangedListener onLocationChangedListener;
    // Current location
    private Location currentLocation;
    private GoogleLocationServicesUtils googleUtils;
    private RecordStateListener listener;
    private ArrayList<Polyline> paths = new ArrayList<>();

    private GoogleAskToEnableLocationService enableLocationService;
    public static final int CONNECTION_RESOLUTION_CODE = 300;
    private GPSLiveTrackerLocationManager mGPSLiveTrackerLocManager;
    private RouteDataSourceFactory routeDataSourceFactory;
    private MapOverlay mapOverlay;


    public static LocationFragment newInstance() {
        LocationFragment f = new LocationFragment();
        Bundle args = new Bundle();
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        routeDataSourceFactory = new RouteDataSourceFactory(getActivity());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.maps_layout, container, false);
        enableLocationService = new GoogleAskToEnableLocationService(getActivity());
        mGPSLiveTrackerLocManager = new GPSLiveTrackerLocationManager(getActivity());
        googleUtils = GoogleLocationServicesUtils.getInstance(getActivity());

        return rootView;
    }

    private void requestLastLocationOrNotifyError() {
        if (mGPSLiveTrackerLocManager != null && !isGpsProviderEnabled()) {
            notifyUserNoLocationIsAvailable();
        } else {
            requestLastKnowLocation();
            mGPSLiveTrackerLocManager.requestLocationUpdates(0, 0, locationListener);
        }
    }

    private void requestLastKnowLocation() {
        if (mGPSLiveTrackerLocManager != null) {
            if (locationListener != null) {
                mGPSLiveTrackerLocManager.removeLocationUpdates(locationListener);
            }
            locationListener = null;
            initLocationListener();
            mGPSLiveTrackerLocManager.requestLastLocation(locationListener);
        }
    }

    private void initLocationListener() {
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (isResumed()) {
                    boolean isFirst = setCurrentLocation(location);
                    updateCurrentLocation(isFirst);
                }
            }
        };
    }

    /**
     * Sets the current location.
     *
     * @param location the location
     * @return true if this is the first location
     */
    protected boolean setCurrentLocation(Location location) {
        boolean isFirst = false;
        if (currentLocation == null && location != null) {
            isFirst = true;
        }
        currentLocation = location;
        return isFirst;
    }


    /**
     * Updates the current location.
     *
     * @param forceZoom true to force zoom to the current location regardless of
     *                  the keepCurrentLocationVisible policy
     */
    protected void updateCurrentLocation(final boolean forceZoom) {
        if (getActivity() == null) {
            return;
        }
        getActivity().runOnUiThread(new Runnable() {
            public void run() {
                updateLocationInternal(forceZoom);
            }
        });
    }

    /**
     * updates the current location according to the forceZoom parameter.
     * Technically speaking, the forceZoom is the first time the my location button is triggered, or, in case of a resume.
     *
     * @param forceZoom true force, false otherwise
     */
    private void updateLocationInternal(boolean forceZoom) {
        if (!isResumed() || getGoogleMap() == null || onLocationChangedListener == null
                || currentLocation == null) {
            Log.d(TAG, "isResumed or google map or locationchanged listener or current location is null");
            return;
        }
        onLocationChangedListener.onLocationChanged(currentLocation);
        if (forceZoom || !isLocationVisible(currentLocation) || isRecording()) {
            LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
            CameraUpdate cameraUpdate = forceZoom ? CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM_LEVEL) : CameraUpdateFactory.newLatLng(latLng);
            getGoogleMap().animateCamera(cameraUpdate);
        }
    }

    /**
     * Returns true if the location is visible. Needs to run on the UI thread.
     *
     * @param location the location
     */
    protected boolean isLocationVisible(Location location) {
        if (location == null || getGoogleMap() == null) {
            return false;
        }
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        return getGoogleMap().getProjection().getVisibleRegion().latLngBounds.contains(latLng);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (this.listener == null) {
            listener = (RecordStateListener) getActivity();
        }
        mapOverlay = new MapOverlay(getActivity());
        initializeMapIfNeeded();
    }

    private void initializeMapIfNeeded() {
        if (mMap == null) {
            loadMapAsync();
        }
    }

    private void loadMapAsync() {
        ((SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.location_map)).getMapAsync(getOnMapReadyCallback());
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        initGpsTracker();
        routeDataSourceFactory.start();
        registerRouteDataListeners();
        long routeId = -1L;
        if (listener != null) {
            routeId = listener.getRouteId();
        }
        initAndShowRouteInternal(routeId);
    }

    public void updateRoute(long routeid) {

        boolean isPaused = routeDataSourceFactory.isSelectedRoutePaused();
        if (!isPaused) {
            initAndShowRouteInternal(routeid);
        }
    }

    private void initAndShowRouteInternal(long routeId) {

        routeDataSourceFactory.loadRouteById(routeId);
        mapOverlay.setShowEndMarker(!isRecording());
        currentRoute = TrackMeDatabaseUtilsImpl.getInstance().getRouteById(routeId);
        if (isRecording() && currentLocation != null) {
            updateCurrentLocation(true);
        } else {
            showRoute();
        }
    }

    private void showRoute() {
        if (mMap == null || currentRoute == null) {
            Log.e(TAG, "map or route is null.");
            return;
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                moveCameraOverRoute();
            }
        });
    }

    private void moveCameraOverRoute() {
        if (currentRoute == null || mMap == null) {
            Log.d(TAG, "route is null or google map is not 'Log.wtf(terrible)' ready");
            return;
        }
        RouteStats routeStats = currentRoute.getRouteStats();
        //todo calculate the boundaries where to zoom in.
        if (currentRoute.getNumberOfPoints() < 2) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LocationUtils.getDefaultLatLng(), mMap.getMinZoomLevel()));
        }
        if (!LocationUtils.isValidLatitude(routeStats.getLatitudeMax()) ||
                !LocationUtils.isValidLatitude(routeStats.getLatitudeMin()) ||
                !LocationUtils.isValidLongitude(routeStats.getLongitudeMax()) |
                        !LocationUtils.isValidLongitude(routeStats.getLongitudeMin())) {
            return;
        }

        if (rootView.getWidth() == 0 || rootView.getHeight() == 0) {
            Log.d(TAG, "empty view. return. not showing any route");
            return;
        }

        int latitudeSpanE6 = routeStats.getTop() - routeStats.getBottom();
        int longitudeSpanE6 = routeStats.getRight() - routeStats.getLeft();
        if (latitudeSpanE6 > 0 && latitudeSpanE6 < 180E6 && longitudeSpanE6 > 0 && longitudeSpanE6 < 360E6) {
            LatLng southWest = new LatLng(routeStats.getBottomDegrees(), routeStats.getLeftDegrees());
            LatLng northEast = new LatLng(routeStats.getTopDegrees(), routeStats.getRightDegrees());

            LatLngBounds bounds = LatLngBounds.builder().include(southWest).include(northEast).build();
            CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, rootView.getWidth(), rootView.getHeight(), 32);
            mMap.moveCamera(cameraUpdate);

        }
    }

    public synchronized void initGpsTracker() {
        if (mMap != null) {
            try {
                mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                checkIfPermissionAllowedForLocation();
            } catch (SecurityException secex) {
                Toast.makeText(getActivity(), "not enabled in manifest", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * rule is the following. First we cheƒck if the permissions are there. If not, we check if we can enable or not.
     * If the permissions are check if gps is enabled.
     */
    private void checkIfPermissionAllowedForLocation() {

        //if permissions are set then we go to else, check for gps
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Request missing location permission.
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
                askIfToRequestPermissions();
            } else {
                requestPermissions();
            }
        } else {
            // Location permission has been granted, continue as usual.
            if (!isGpsProviderEnabled()) {
                askToEnableGPS();
            } else {
                mMap.setMyLocationEnabled(true);
            }
        }
    }

    private boolean isGpsProviderEnabled() {
        return googleUtils.isGpsProviderEnabled();
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(getActivity(),
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_CODE_LOCATION);
    }

    private void enableGPS() {
        if (enableLocationService != null) {
            enableLocationService.askToEnableGps(locationCallback);
        }
    }

    private GoogleAskToEnableLocationService.GpsCallback locationCallback = new GoogleAskToEnableLocationService.GpsCallback() {
        @Override
        public void onSuccess() {
            initGpsTracker();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mMap != null) {
                        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                    }
                }
            });
        }

        @Override
        public void onResolutionRequired(Status status) {
            try {
                status.startResolutionForResult(getActivity(), CONNECTION_RESOLUTION_CODE);
            } catch (IntentSender.SendIntentException setex) {
                Toast.makeText(getActivity(), "Exception in sending intent:", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onError() {
            Toast.makeText(getActivity(), "Location services unavailable!", Toast.LENGTH_SHORT).show();
        }
    };

    private GoogleMap.OnCameraMoveStartedListener getCameraChangeListener() {
        return new GoogleMap.OnCameraMoveStartedListener() {

            @Override
            public void onCameraMoveStarted(int i) {
                if (isResumed() && currentLocation != null && !isLocationVisible(currentLocation)) {
                    //todo
                }
            }
        };
    }

    protected LocationSource getNewLocationSource() {
        return new LocationSource() {

            @Override
            public void activate(OnLocationChangedListener listener) {
                onLocationChangedListener = listener;
            }

            @Override
            public void deactivate() {
                onLocationChangedListener = null;
            }
        };
    }

    @Override
    public void onPause() {
        if (routeDataSourceFactory != null) {
            unregisterDataListeners();
            routeDataSourceFactory.stop();
        }
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (locationListener != null) {
            mGPSLiveTrackerLocManager.removeLocationUpdates(locationListener);
            locationListener = null;
        }
        mGPSLiveTrackerLocManager.close();
        //routeDataSourceFactory.stop();
    }

    private void registerRouteDataListeners() {
        routeDataSourceFactory.registerListeners(this, EnumSet.of(
                RouteType.ROUTE,
                RouteType.PREFERENCE,
                RouteType.ROUTE_CHECK_POINT,
                RouteType.ROUTE_POINT,
                RouteType.ROUTE_RESAMPLE_POINTS));
    }

    private void unregisterDataListeners() {
        routeDataSourceFactory.unregisterListener(this);
    }

    public OnMapReadyCallback getOnMapReadyCallback() {
        return new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap map) {
                mMap = map;
                if (mMap == null) {
                    return;
                }

                mMap.setIndoorEnabled(true);
                mMap.getUiSettings().setMyLocationButtonEnabled(true);
                mMap.setOnMyLocationButtonClickListener(getOnMyLocationClickListener());
                mMap.setLocationSource(getNewLocationSource());
                mMap.setOnCameraMoveStartedListener(getCameraChangeListener());
                mMap.getUiSettings().setCompassEnabled(true);
                mMap.getUiSettings().setZoomControlsEnabled(true);
                initGpsTracker();
            }
        };
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CONNECTION_RESOLUTION_CODE:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        initGpsTracker();
                        break;
                    case Activity.RESULT_CANCELED:
                        Toast.makeText(getContext(), "Gps not enabled:", Toast.LENGTH_SHORT).show();
                        break;
                    default:
                        break;
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_LOCATION) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initGpsTracker();
            } else {
                Toast.makeText(getActivity(), "Manifest permission, not enabled", Toast.LENGTH_SHORT).show();

            }
        }
    }

    private void askToEnableGPS() {
        CustomFragmentDialog customFragmentDialog = CustomFragmentDialog.newInstance(getString(R.string.enable_gps_title),
                getString(R.string.enable_gps_message),
                getString(R.string.ok),
                getString(R.string.cancel),
                callback);
        customFragmentDialog.show(getFragmentManager(), CUSTOM_TAG);
    }


    private void askIfToRequestPermissions() {
        CustomFragmentDialog customFragmentDialog = CustomFragmentDialog.newInstance(getString(R.string.enable_gps_title),
                getString(R.string.enable_permissions_message),
                getString(R.string.ok),
                getString(R.string.cancel),
                callback_permissions);
        customFragmentDialog.show(getFragmentManager(), CUSTOM_TAG);
    }

    private CustomFragmentDialog.Callback callback = new CustomFragmentDialog.Callback() {
        @Override
        public void onPositiveButtonClicked(Bundle bundle) {
            enableGPS();
        }

        @Override
        public void onNegativeButtonClicked(Bundle bundle) {

        }
    };

    private CustomFragmentDialog.Callback callback_permissions = new CustomFragmentDialog.Callback() {
        @Override
        public void onPositiveButtonClicked(Bundle bundle) {
            requestPermissions();
        }

        @Override
        public void onNegativeButtonClicked(Bundle bundle) {

        }
    };

    /**
     * in case the user wants to know its location and the gps is disabled then a notification will be shown
     */
    protected void notifyUserNoLocationIsAvailable() {
        String message = googleUtils.getGpsDisabledMyLocationMessage();
        Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
    }

    private GoogleMap.OnMyLocationButtonClickListener getOnMyLocationClickListener() {
        return new GoogleMap.OnMyLocationButtonClickListener() {
            @Override
            public boolean onMyLocationButtonClick() {
                requestLastLocationOrNotifyError();
                return true;
            }
        };
    }

    private boolean isRecording() {
        return routeDataSourceFactory != null && routeDataSourceFactory.isSelectedRouteRecording();
    }

    public GoogleMap getGoogleMap() {
        return mMap;
    }

    //==================================route data listeners======================================//


    @Override
    public void onNewRouteCheckPointUpdate() {
        if (isResumed()) {
            getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    if (isResumed() && mMap != null && currentRoute != null) {
                        mapOverlay.update(mMap, paths, reload);
                    }
                }
            });
        }
    }

    @Override
    public void onNewRouteUpdate(Route route) {
        this.currentRoute = route;
        showRoute();
    }

    @Override
    public void addNewRouteCheckPoint(RouteCheckPoint routeCheckPoint) {
        if (isResumed() && mapOverlay != null && LocationUtils.isValidLocation(routeCheckPoint.getLocation())) {
            mapOverlay.addRouteCheckPoint(routeCheckPoint);
        }
    }

    @Override
    public void onNewRoutePointUpdateDone() {
        if (isResumed() && mapOverlay != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mMap != null && isResumed() && mapOverlay != null && currentRoute != null) {
                        boolean hasStartMarker = mapOverlay.update(mMap, paths, reload);
                        if (hasStartMarker) {
                            reload = false;
                        }

                        if (lastLocation != null && isRecording()) {
                            boolean firstLocation = setCurrentLocation(lastLocation);
                            updateCurrentLocation(firstLocation);
                        }
                    }
                }
            });
        }
    }

    private void runOnUiThread(Runnable runnable) {
        getActivity().runOnUiThread(runnable);
    }

    @Override
    public void addLocationToQueue(Location location) {
        if (isResumed() && mapOverlay != null) {
            mapOverlay.addLocation(location);
        }
    }

    @Override
    public void addLocationToMap(Location location) {
        lastLocation = location;
        if (isResumed() && mapOverlay != null) {
            mapOverlay.addLocationToMap(location);
        }
    }

    @Override
    public void setLastLocation(Location location) {
        this.lastLocation = location;
    }

    @Override
    public void onRecordingDistanceIntervalChanged(int newDistance) {
        //todo
    }

    @Override
    public void onRecordingGpsAccuracyChanged(int newValue) {
        recordingGpsAccuracy = newValue;
    }

    @Override
    public void onRecordingMaxDistanceChanged(int newDistance) {
        //todo
    }

    @Override
    public void clearPoints() {
        Log.d(TAG, "clearing points");
        RecordingState state = PreferenceUtils.getRecordingState(getActivity(), R.string.recording_state_key, RecordingState.NOT_STARTED);
        if (isResumed() && mapOverlay != null && (state == RecordingState.NOT_STARTED || state == RecordingState.STOPPED)) {
            mapOverlay.clearPoints();
            reload = true;
        }
    }

    @Override
    public void onMapTypeChanged(final int mapType) {
        if (isResumed()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mMap != null) {
                        mMap.setMapType(mapType);
                    }
                }
            });
        }
    }

    public void setShowEndMarker() {
        if (mapOverlay != null) {
            mapOverlay.setShowEndMarker(true);
        }
    }

    //==========================================end of listener calls for database changes==============//

}
