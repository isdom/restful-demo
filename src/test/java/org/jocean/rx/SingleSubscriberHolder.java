package org.jocean.rx;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import rx.Single;
import rx.SingleSubscriber;

public class SingleSubscriberHolder<T> implements Single.OnSubscribe<T> {

    @Override
    public void call(final SingleSubscriber<? super T> subscriber) {
        this._subscribers.add(subscriber);
    }
    
    public SingleSubscriber<? super T> getAt(final int idx) {
        return this._subscribers.get(idx);
    }
    
    public int getSubscriberCount() {
        return this._subscribers.size();
    }

    private final List<SingleSubscriber<? super T>> _subscribers = new CopyOnWriteArrayList<>();
}
