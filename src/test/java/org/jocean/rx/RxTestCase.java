package org.jocean.rx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.jocean.idiom.rx.SubscriberHolder;
import org.junit.Test;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.subscriptions.Subscriptions;

public class RxTestCase {

    @Test
    public final void testFlatMap() throws InterruptedException {
        final AtomicBoolean unsubscribed1 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed2 = new AtomicBoolean(false);
        final SubscriberHolder<Integer> holderOfInteger = new SubscriberHolder<Integer>();
        final SubscriberHolder<String> holderOfString = new SubscriberHolder<String>();
        
        final Subscription subscription = 
                Observable.create(new OnSubscribe<Integer>() {
            @Override
            public void call(final Subscriber<? super Integer> subscriber) {
                subscriber.add(Subscriptions.create(new Action0() {
                    @Override
                    public void call() {
                        unsubscribed1.set(true);
                    }}));
                holderOfInteger.call(subscriber);
            }})
        .flatMap(new Func1<Integer, Observable<? extends String>>() {
            @Override
            public Observable<? extends String> call(final Integer t) {
                return Observable.create(new OnSubscribe<String>() {
                    @Override
                    public void call(final Subscriber<? super String> subscriber) {
                        subscriber.add(Subscriptions.create(new Action0() {
                            @Override
                            public void call() {
                                unsubscribed2.set(true);
                            }}));
                        holderOfString.call(subscriber);
                    }});
            }})
        .subscribe();
        
        assertEquals(1, holderOfInteger.getSubscriberCount());
        assertEquals(0, holderOfString.getSubscriberCount());
        
        holderOfInteger.getAt(0).onNext(1);
        holderOfInteger.getAt(0).onCompleted();
        
        assertFalse(unsubscribed1.get());
        
        assertEquals(1, holderOfString.getSubscriberCount());
        holderOfString.getAt(0).onNext("hello");
        holderOfString.getAt(0).onNext("world");
        
        assertFalse(unsubscribed1.get());
        assertFalse(unsubscribed2.get());
        holderOfString.getAt(0).onCompleted();
        
        assertTrue(unsubscribed1.get());
        assertTrue(unsubscribed2.get());
    }

    @Test
    public final void testFlatMap2() throws InterruptedException {
        final AtomicBoolean unsubscribed1 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed2 = new AtomicBoolean(false);
        final SubscriberHolder<Integer> holderOfInteger = new SubscriberHolder<Integer>();
        final SubscriberHolder<String> holderOfString = new SubscriberHolder<String>();
        
        final Subscription subscription = 
                Observable.create(new OnSubscribe<Integer>() {
            @Override
            public void call(final Subscriber<? super Integer> subscriber) {
                subscriber.add(Subscriptions.create(new Action0() {
                    @Override
                    public void call() {
                        unsubscribed1.set(true);
                    }}));
                holderOfInteger.call(subscriber);
            }})
        .flatMap(new Func1<Integer, Observable<? extends String>>() {
            @Override
            public Observable<? extends String> call(final Integer t) {
                return Observable.create(new OnSubscribe<String>() {
                    @Override
                    public void call(final Subscriber<? super String> subscriber) {
                        subscriber.add(Subscriptions.create(new Action0() {
                            @Override
                            public void call() {
                                unsubscribed2.set(true);
                            }}));
                        holderOfString.call(subscriber);
                    }});
            }})
        .subscribe();
        
        assertEquals(1, holderOfInteger.getSubscriberCount());
        assertEquals(0, holderOfString.getSubscriberCount());
        
        holderOfInteger.getAt(0).onNext(1);
        holderOfInteger.getAt(0).onCompleted();
        
        assertFalse(unsubscribed1.get());
        
        assertEquals(1, holderOfString.getSubscriberCount());
        
        subscription.unsubscribe();
        
        assertTrue(unsubscribed1.get());
        assertTrue(unsubscribed2.get());
    }

    @Test
    public final void testFlatMap3() throws InterruptedException {
        final AtomicBoolean unsubscribed1 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed2 = new AtomicBoolean(false);
        final SubscriberHolder<Integer> holderOfInteger = new SubscriberHolder<Integer>();
        final SubscriberHolder<String> holderOfString = new SubscriberHolder<String>();
        
        final Subscription subscription = 
                Observable.create(new OnSubscribe<Integer>() {
            @Override
            public void call(final Subscriber<? super Integer> subscriber) {
                subscriber.add(Subscriptions.create(new Action0() {
                    @Override
                    public void call() {
                        unsubscribed1.set(true);
                    }}));
                holderOfInteger.call(subscriber);
            }})
        .flatMap(new Func1<Integer, Observable<? extends String>>() {
            @Override
            public Observable<? extends String> call(final Integer t) {
                return Observable.create(new OnSubscribe<String>() {
                    @Override
                    public void call(final Subscriber<? super String> subscriber) {
                        subscriber.add(Subscriptions.create(new Action0() {
                            @Override
                            public void call() {
                                unsubscribed2.set(true);
                            }}));
                        holderOfString.call(subscriber);
                    }});
            }})
        .subscribe();
        
        assertEquals(1, holderOfInteger.getSubscriberCount());
        assertEquals(0, holderOfString.getSubscriberCount());
        
        subscription.unsubscribe();
        
        assertTrue(unsubscribed1.get());
        assertFalse(unsubscribed2.get());
    }
}
