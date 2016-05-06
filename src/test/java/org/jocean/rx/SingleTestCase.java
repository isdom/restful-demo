package org.jocean.rx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import rx.Single;
import rx.Single.OnSubscribe;
import rx.SingleSubscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.subscriptions.Subscriptions;

public class SingleTestCase {
    private <T> Single<? extends T> createSingleByHolder(
            final AtomicBoolean unsubscribed,
            final SingleSubscriberHolder<T> holder) {
        return Single.create(new OnSubscribe<T>() {
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
    
    private <T, R> Func1<T, Single<? extends R>> flatMapFuncOf(
            final AtomicBoolean unsubscribed,
            final SingleSubscriberHolder<R> holder) {
        return new Func1<T, Single<? extends R>>() {
            @Override
            public Single<? extends R> call(T t) {
                return createSingleByHolder(unsubscribed, holder);
            }};
    }
    
    @Test
    public final void testFlatMapTriple() throws InterruptedException {
        final AtomicBoolean unsubscribed1 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed2 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed3 = new AtomicBoolean(false);
        final SingleSubscriberHolder<String> holder1 = new SingleSubscriberHolder<String>();
        final SingleSubscriberHolder<String> holder2 = new SingleSubscriberHolder<String>();
        final SingleSubscriberHolder<String> holder3 = new SingleSubscriberHolder<String>();
        
        createSingleByHolder(unsubscribed1, holder1)
        .flatMap(flatMapFuncOf(unsubscribed2, holder2))
        .flatMap(flatMapFuncOf(unsubscribed3, holder3))
        .subscribe();
        
        assertEquals(1, holder1.getSubscriberCount());
        assertEquals(0, holder2.getSubscriberCount());
        assertEquals(0, holder3.getSubscriberCount());
        
        holder1.getAt(0).onSuccess("hello");
        
        assertTrue(unsubscribed1.get());
        assertFalse(unsubscribed2.get());
        assertFalse(unsubscribed3.get());
        
        assertEquals(1, holder2.getSubscriberCount());
        assertEquals(0, holder3.getSubscriberCount());
        holder2.getAt(0).onSuccess("world");
        
        assertTrue(unsubscribed1.get());
        assertTrue(unsubscribed2.get());
        assertFalse(unsubscribed3.get());
        
        assertEquals(1, holder3.getSubscriberCount());
        holder3.getAt(0).onSuccess("rx");
        
        assertTrue(unsubscribed1.get());
        assertTrue(unsubscribed2.get());
        assertTrue(unsubscribed3.get());
    }
    
}
