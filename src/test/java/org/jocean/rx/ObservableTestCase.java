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

public class ObservableTestCase {
    private <T> Observable<? extends T> createObservableByHolder(
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
    
    private <T, R> Func1<T, Observable<? extends R>> flatMapFuncOf(
            final AtomicBoolean unsubscribed,
            final SubscriberHolder<R> holder) {
        return new Func1<T, Observable<? extends R>>() {
            @Override
            public Observable<? extends R> call(T t) {
                return createObservableByHolder(unsubscribed, holder);
            }};
    }
    
    @Test
    public final void testFlatMapTwice() throws InterruptedException {
        final AtomicBoolean unsubscribed1 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed2 = new AtomicBoolean(false);
        final SubscriberHolder<String> holder1 = new SubscriberHolder<String>();
        final SubscriberHolder<String> holder2 = new SubscriberHolder<String>();
        
        createObservableByHolder(unsubscribed1, holder1)
        .flatMap(flatMapFuncOf(unsubscribed2, holder2))
        .subscribe();
        
        assertEquals(1, holder1.getSubscriberCount());
        assertEquals(0, holder2.getSubscriberCount());
        
        holder1.getAt(0).onNext("hello");
        holder1.getAt(0).onCompleted();
        
        assertFalse(unsubscribed1.get());
        
        assertEquals(1, holder2.getSubscriberCount());
        holder2.getAt(0).onNext("hello");
        
        assertFalse(unsubscribed1.get());
        assertFalse(unsubscribed2.get());
        holder2.getAt(0).onCompleted();
        
        assertTrue(unsubscribed1.get());
        assertTrue(unsubscribed2.get());
    }

    @Test
    public final void testFlatMapTwice2() throws InterruptedException {
        final AtomicBoolean unsubscribed1 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed2 = new AtomicBoolean(false);
        final SubscriberHolder<String> holder1 = new SubscriberHolder<String>();
        final SubscriberHolder<String> holder2 = new SubscriberHolder<String>();
        
        final Subscription subscription = 
                createObservableByHolder(unsubscribed1, holder1)
                .flatMap(flatMapFuncOf(unsubscribed2, holder2))
                .subscribe();
        
        assertEquals(1, holder1.getSubscriberCount());
        assertEquals(0, holder2.getSubscriberCount());
        
        holder1.getAt(0).onNext("hello");
        holder1.getAt(0).onCompleted();
        
        assertFalse(unsubscribed1.get());
        
        assertEquals(1, holder2.getSubscriberCount());
        
        subscription.unsubscribe();
        
        assertTrue(unsubscribed1.get());
        assertTrue(unsubscribed2.get());
    }

    @Test
    public final void testFlatMapTwice3() throws InterruptedException {
        final AtomicBoolean unsubscribed1 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed2 = new AtomicBoolean(false);
        final SubscriberHolder<String> holder1 = new SubscriberHolder<String>();
        final SubscriberHolder<String> holder2 = new SubscriberHolder<String>();
        
        final Subscription subscription = 
                createObservableByHolder(unsubscribed1, holder1)
                .flatMap(flatMapFuncOf(unsubscribed2, holder2))
                .subscribe();
        
        assertEquals(1, holder1.getSubscriberCount());
        assertEquals(0, holder2.getSubscriberCount());
        
        subscription.unsubscribe();
        
        assertTrue(unsubscribed1.get());
        assertFalse(unsubscribed2.get());
    }

    @Test
    public final void testFlatMapTriple() throws InterruptedException {
        final AtomicBoolean unsubscribed1 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed2 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed3 = new AtomicBoolean(false);
        final SubscriberHolder<String> holder1 = new SubscriberHolder<String>();
        final SubscriberHolder<String> holder2 = new SubscriberHolder<String>();
        final SubscriberHolder<String> holder3 = new SubscriberHolder<String>();
        
        createObservableByHolder(unsubscribed1, holder1)
        .flatMap(flatMapFuncOf(unsubscribed2, holder2))
        .flatMap(flatMapFuncOf(unsubscribed3, holder3))
        .subscribe();
        
        assertEquals(1, holder1.getSubscriberCount());
        assertEquals(0, holder2.getSubscriberCount());
        assertEquals(0, holder3.getSubscriberCount());
        
        holder1.getAt(0).onNext("hello");
        holder1.getAt(0).onCompleted();
        
        assertFalse(unsubscribed1.get());
        assertFalse(unsubscribed2.get());
        assertFalse(unsubscribed3.get());
        
        assertEquals(1, holder2.getSubscriberCount());
        assertEquals(0, holder3.getSubscriberCount());
        holder2.getAt(0).onNext("world");
        holder2.getAt(0).onCompleted();
        
        assertFalse(unsubscribed1.get());
        assertTrue(unsubscribed2.get());
        assertFalse(unsubscribed3.get());
        
        assertEquals(1, holder3.getSubscriberCount());
        holder3.getAt(0).onNext("rx");
        holder3.getAt(0).onCompleted();
        
        assertTrue(unsubscribed1.get());
        assertTrue(unsubscribed2.get());
        assertTrue(unsubscribed3.get());
    }
    
    @Test
    public final void testFlatMapQuadruple() throws InterruptedException {
        final AtomicBoolean unsubscribed1 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed2 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed3 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed4 = new AtomicBoolean(false);
        final SubscriberHolder<String> holder1 = new SubscriberHolder<String>();
        final SubscriberHolder<String> holder2 = new SubscriberHolder<String>();
        final SubscriberHolder<String> holder3 = new SubscriberHolder<String>();
        final SubscriberHolder<String> holder4 = new SubscriberHolder<String>();
        
        createObservableByHolder(unsubscribed1, holder1)
        .flatMap(flatMapFuncOf(unsubscribed2, holder2))
        .flatMap(flatMapFuncOf(unsubscribed3, holder3))
        .flatMap(flatMapFuncOf(unsubscribed4, holder4))
        .subscribe();
        
        assertEquals(1, holder1.getSubscriberCount());
        assertEquals(0, holder2.getSubscriberCount());
        assertEquals(0, holder3.getSubscriberCount());
        assertEquals(0, holder4.getSubscriberCount());
        
        holder1.getAt(0).onNext("hello");
        holder1.getAt(0).onCompleted();
        
        assertFalse(unsubscribed1.get());
        assertFalse(unsubscribed2.get());
        assertFalse(unsubscribed3.get());
        assertFalse(unsubscribed4.get());
        
        assertEquals(1, holder2.getSubscriberCount());
        assertEquals(0, holder3.getSubscriberCount());
        assertEquals(0, holder4.getSubscriberCount());
        holder2.getAt(0).onNext("world");
        holder2.getAt(0).onCompleted();
        
        assertFalse(unsubscribed1.get());
        assertTrue(unsubscribed2.get());
        assertFalse(unsubscribed3.get());
        assertFalse(unsubscribed4.get());
        
        assertEquals(1, holder3.getSubscriberCount());
        assertEquals(0, holder4.getSubscriberCount());
        holder3.getAt(0).onNext("rx");
        holder3.getAt(0).onCompleted();
        
        assertFalse(unsubscribed1.get());
        assertTrue(unsubscribed2.get());
        assertTrue(unsubscribed3.get());
        assertFalse(unsubscribed4.get());
        
        assertEquals(1, holder4.getSubscriberCount());
        holder4.getAt(0).onNext("java");
        holder4.getAt(0).onCompleted();
        
        assertTrue(unsubscribed1.get());
        assertTrue(unsubscribed2.get());
        assertTrue(unsubscribed3.get());
        assertTrue(unsubscribed4.get());
    }

    @Test
    public final void testFlatMapQuadrupleDelayOnCompleted() throws InterruptedException {
        final AtomicBoolean unsubscribed1 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed2 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed3 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed4 = new AtomicBoolean(false);
        final SubscriberHolder<String> holder1 = new SubscriberHolder<String>();
        final SubscriberHolder<String> holder2 = new SubscriberHolder<String>();
        final SubscriberHolder<String> holder3 = new SubscriberHolder<String>();
        final SubscriberHolder<String> holder4 = new SubscriberHolder<String>();
        
        final Subscription subscription = 
            createObservableByHolder(unsubscribed1, holder1)
            .flatMap(flatMapFuncOf(unsubscribed2, holder2))
            .flatMap(flatMapFuncOf(unsubscribed3, holder3))
            .flatMap(flatMapFuncOf(unsubscribed4, holder4))
            .subscribe();
        
        assertEquals(1, holder1.getSubscriberCount());
        assertEquals(0, holder2.getSubscriberCount());
        assertEquals(0, holder3.getSubscriberCount());
        assertEquals(0, holder4.getSubscriberCount());
        
        holder1.getAt(0).onNext("hello");
        
        assertFalse(unsubscribed1.get());
        assertFalse(unsubscribed2.get());
        assertFalse(unsubscribed3.get());
        assertFalse(unsubscribed4.get());
        
        assertEquals(1, holder2.getSubscriberCount());
        assertEquals(0, holder3.getSubscriberCount());
        assertEquals(0, holder4.getSubscriberCount());
        holder2.getAt(0).onNext("world");
        
        assertFalse(unsubscribed1.get());
        assertFalse(unsubscribed2.get());
        assertFalse(unsubscribed3.get());
        assertFalse(unsubscribed4.get());
        
        assertEquals(1, holder3.getSubscriberCount());
        assertEquals(0, holder4.getSubscriberCount());
        holder3.getAt(0).onNext("rx");
        
        assertFalse(unsubscribed1.get());
        assertFalse(unsubscribed2.get());
        assertFalse(unsubscribed3.get());
        assertFalse(unsubscribed4.get());
        
        assertEquals(1, holder4.getSubscriberCount());
        holder4.getAt(0).onNext("java");
        
        assertFalse(unsubscribed1.get());
        assertFalse(unsubscribed2.get());
        assertFalse(unsubscribed3.get());
        assertFalse(unsubscribed4.get());
        
        subscription.unsubscribe();
        
        assertTrue(unsubscribed1.get());
        assertTrue(unsubscribed2.get());
        assertTrue(unsubscribed3.get());
        assertTrue(unsubscribed4.get());
    }
}
