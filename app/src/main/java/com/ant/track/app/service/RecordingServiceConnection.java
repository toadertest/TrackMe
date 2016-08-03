package com.ant.track.app.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.ant.track.app.BuildConfig;
import com.ant.track.app.R;
import com.ant.track.app.service.utils.RecordingServiceConnectionUtils;
import com.ant.track.lib.prefs.PreferenceUtils;
import com.ant.track.lib.service.RecordingState;

import java.lang.ref.WeakReference;

/**
 * Class that deals with starting and stopping the location
 * service.
 */
public class RecordingServiceConnection {

    private static final String TAG = RecordingServiceConnection.class.getSimpleName();
    private final Callback callback;
    private WeakReference<Context> contextRef;
    /**
     * Messenger for sending messages to the service.
     */
    private Messenger mServiceMessenger = null;
    /**
     * Messenger for receiving messages from the service.
     */
    private Messenger mClientMessenger = null;
    private IncomingHandler incomingHandler;

    /**
     * Constructor.
     *
     * @param context  the context
     * @param callback the callback to invoke when the service binding changes
     */
    public RecordingServiceConnection(Context context, Callback callback) {
        this.contextRef = new WeakReference<>(context);
        this.callback = callback;
        incomingHandler = new IncomingHandler(Looper.getMainLooper());
        mClientMessenger = new Messenger(incomingHandler);
    }

    private class IncomingHandler extends Handler {

        public IncomingHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case RecordingServiceConstants.MSG_UPDATE_ROUTE_ID:
                    if (callback != null && msg.obj != null) {
                        callback.onRouteUpdate((long) msg.obj);
                    }
                    break;
                case RecordingServiceConstants.MSG_NOT_ALLOWED:
                    if (callback != null && msg.obj != null) {
                        callback.onError((String) msg.obj);
                    }
                    break;
                case RecordingServiceConstants.MSG_CLIENT_CONNECTED: {
                    if (callback != null) {
                        callback.onConnected();
                    }
                    break;
                }

                case RecordingServiceConstants.MSG_CLIENT_DISCONNECTED: {
                    long routeid = (long) msg.obj;
                    if (callback != null) {
                        callback.onDisconnected(routeid);
                    }
                    break;
                }
                case RecordingServiceConstants.MSG_EXCEPTION: {
                    if (callback != null) {
                        resetRecordingState();
                        unbindAndStop();
                    }
                    break;
                }
            }
        }
    }

    private final IBinder.DeathRecipient deathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            Log.d(TAG, "Service died.");
            setRecordingService(null);
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "Connected to the service.");
            try {
                service.linkToDeath(deathRecipient, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to bind a death recipient.", e);
            }
            setRecordingService(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.i(TAG, "Disconnected from the service.");
            setRecordingService(null);
        }
    };

    /**
     * Starts and binds the service.
     */
    public void startAndBind() {
        bindService(true);
    }

    /**
     * Binds the service if it is started.
     */
    public void bindIfConnected() {
        bindService(false);
    }

    /**
     * Unbinds and stops the service.
     */
    public void unbindAndStop() {
        unbind();
        contextRef.get().stopService(new Intent(contextRef.get(), RecordingServiceImpl.class));
    }

    /**
     * Unbinds the service (but leave it running).
     */
    public void unbind() {
        try {
            contextRef.get().unbindService(serviceConnection);
        } catch (IllegalArgumentException e) {
            // Means not bound to the service. OK to ignore.
        }
        setRecordingService(null);
    }

    /**
     * Gets the track recording service if bound. Returns null otherwise
     */
    public IBinder getServiceIfBound() {
        if (mServiceMessenger != null && !mServiceMessenger.getBinder().isBinderAlive()) {
            setRecordingService(null);
            return null;
        }
        if (mServiceMessenger != null) {
            return mServiceMessenger.getBinder();
        }
        return null;
    }

    /**
     * Binds the service if it is started.
     *
     * @param startIfNeeded start the service if needed
     */
    private void bindService(boolean startIfNeeded) {
        if (mServiceMessenger != null && mServiceMessenger.getBinder() != null) {
            // Service is already started and bound.
            return;
        }

        if (!startIfNeeded && !RecordingServiceConnectionUtils.isRecordingServiceRunning(contextRef.get())) {
            Log.d(TAG, "Service is not started. Not binding it.");
            resetRecordingState();
            return;
        }

        if (startIfNeeded) {
            Log.i(TAG, "Starting the service.");
            contextRef.get().startService(new Intent(contextRef.get(), RecordingServiceImpl.class));
        }

        Log.i(TAG, "Binding the service.");
        int flags = BuildConfig.DEBUG ? Context.BIND_DEBUG_UNBIND : 0;
        contextRef.get().bindService(new Intent(contextRef.get(), RecordingServiceImpl.class), serviceConnection, flags);
    }

    /**
     * in cases when there is a crash and we want a full reset.
     */
    private void resetRecordingState() {
        long recordingTrackId = PreferenceUtils.getLong(contextRef.get(), R.string.route_id_key, -1);
        if (recordingTrackId != PreferenceUtils.DEFAULT_ROUTE_ID) {
            PreferenceUtils.setRouteId(contextRef.get(), R.string.route_id_key, PreferenceUtils.DEFAULT_ROUTE_ID);
        }
        RecordingState recordingTrackPaused = PreferenceUtils.getRecordingState(contextRef.get(),
                R.string.recording_state_key, PreferenceUtils.RECORDING_STATE_NOT_STARTED_DEFAULT);
        if (recordingTrackPaused != RecordingState.NOT_STARTED) {
            PreferenceUtils.setRecordingState(contextRef.get(), R.string.recording_state_key,
                    PreferenceUtils.RECORDING_STATE_NOT_STARTED_DEFAULT);
        }
    }

    /**
     * Sets the mRecordingService.
     *
     * @param binder the binder
     */
    private void setRecordingService(IBinder binder) {

        try {
            if (binder != null) {
                attachListenerToService(binder);
            } else {
                mServiceMessenger = null;
            }
        } catch (RemoteException remex) {
            Log.e(TAG, "Exception in sending the message to the service");
            if (callback != null) {
                callback.onError(remex.getMessage());
            }
        }
    }

    private void attachListenerToService(IBinder binder) throws RemoteException {
        mServiceMessenger = new Messenger(binder);
        Message message = Message.obtain(null, RecordingServiceConstants.MSG_REGISTER_CLIENT, 0, 0);
        message.replyTo = mClientMessenger;
        mServiceMessenger.send(message);
    }

    public void startTracking() throws RemoteException {
        Message message = Message.obtain(null, RecordingServiceConstants.MSG_START_TRACKING, 0, 0);
        mServiceMessenger.send(message);
    }

    public void resumeTracking() throws RemoteException {
        if (mServiceMessenger != null) {
            Message message = Message.obtain(null, RecordingServiceConstants.MSG_RESUME_TRACKING, 0, 0);
            mServiceMessenger.send(message);
        }
    }

    public void pauseTracking() throws RemoteException {
        if (mServiceMessenger != null) {
            Message message = Message.obtain(null, RecordingServiceConstants.MSG_PAUSE_TRACKING, 0, 0);
            mServiceMessenger.send(message);
        }
    }

    public void stopTracking() throws RemoteException {
        if (mServiceMessenger != null) {
            Message message = Message.obtain(null, RecordingServiceConstants.MSG_STOP_TRACKING, 0, 0);
            mServiceMessenger.send(message);
        }
    }

    public interface Callback {

        /**
         * callback for when a new route is created.
         *
         * @param id - the route id.
         */
        void onRouteUpdate(long id);

        /**
         * callback for when the service has been connected.
         */
        void onConnected();

        /**
         * callback for when the service has been disconnected.
         *
         * @param routeid
         */
        void onDisconnected(long routeid);

        /**
         * Notification about an error that occurred.
         */
        void onError(String message);
    }
}
