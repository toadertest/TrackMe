package com.ant.track.lib.publisher;

import com.ant.track.lib.models.User;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Content publisher that is used as an observer in case
 * an update occurs.
 */
public class ContentPublisherImpl implements ContentPublisher<User, NotifyListener> {

    private static ContentPublisher instance = null;
    private List<NotifyListener> listeners = new ArrayList<>();

    public static ContentPublisher getInstance() {
        if (instance == null) {
            instance = new ContentPublisherImpl();
        }
        return instance;
    }

    @Override
    public void registerListener(final NotifyListener listener) {
        synchronized (listeners) {
            if (listener != null && !listeners.contains(listener)) {
                listeners.add(listener);
            }
        }
    }

    @Override
    public void unregisterListener(NotifyListener listener) {
        synchronized (listeners) {
            if (listener != null) {
                Iterator<NotifyListener> it = listeners.iterator();
                while ((it.hasNext())) {
                    NotifyListener mListener = it.next();
                    if (mListener.equals(listener)) {
                        it.remove();
                    }
                }
            }
        }
    }

    @Override
    public void notifyListeners(User user) {
        if (listeners.isEmpty()) {
            return;
        }
        for (NotifyListener listener : listeners) {
            if (listener != null) {
                listener.notifyUI(user);
            }
        }
    }
}