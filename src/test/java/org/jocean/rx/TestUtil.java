package org.jocean.rx;

import java.util.concurrent.atomic.AtomicBoolean;

import org.jocean.idiom.rx.SubscriberHolder;

import rx.Observable;
import rx.Single;
import rx.SingleSubscriber;
import rx.Subscriber;
import rx.Observable.OnSubscribe;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.subscriptions.Subscriptions;

public class TestUtil {
    public static <T> Observable<? extends T> createObservableByHolder(
            final AtomicBoolean unsubscribed,
            final SubscriberHolder<T> holder) {
        return Observable.create(new OnSubscribe<T>() {
            @Override
            public void call(final Subscriber<? super T> subscriber) {
                subscriber.add(Subscriptions.create(new Action0() {
                    @Override
                    public void call() {
                        unsubscribed.set(true);
                    }}));
                holder.call(subscriber);
            }});
    }
    
    public static <T, R> Func1<T, Observable<? extends R>> flatMapFuncOf(
            final AtomicBoolean unsubscribed,
            final SubscriberHolder<R> holder) {
        return new Func1<T, Observable<? extends R>>() {
            @Override
            public Observable<? extends R> call(T t) {
                return createObservableByHolder(unsubscribed, holder);
            }};
    }
    
    public static <T> Single<? extends T> createSingleByHolder(
            final AtomicBoolean unsubscribed,
            final SingleSubscriberHolder<T> holder) {
        return Single.create(new Single.OnSubscribe<T>() {
            @Override
            public void call(final SingleSubscriber<? super T> subscriber) {
                subscriber.add(Subscriptions.create(new Action0() {
                    @Override
                    public void call() {
                        unsubscribed.set(true);
                    }}));
                holder.call(subscriber);
            }});
    }
    
    public static <T, R> Func1<T, Single<? extends R>> flatMapFuncOf(
            final AtomicBoolean unsubscribed,
            final SingleSubscriberHolder<R> holder) {
        return new Func1<T, Single<? extends R>>() {
            @Override
            public Single<? extends R> call(T t) {
                return createSingleByHolder(unsubscribed, holder);
            }};
    }

}
