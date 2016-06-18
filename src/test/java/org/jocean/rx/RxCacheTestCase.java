package org.jocean.rx;

import static org.junit.Assert.assertEquals;

import org.jocean.idiom.rx.SubscriberHolder;
import org.junit.Test;

import rx.Observable;
import rx.observers.TestSubscriber;

public class RxCacheTestCase {

    @Test
    public final void testCache() {
        
        final SubscriberHolder<String> holder1 = new SubscriberHolder<String>();
        
        final Observable<String> observable1 = 
            Observable.create(holder1)
            .cache();
        
        assertEquals(0, holder1.getSubscriberCount());
        
        final TestSubscriber<String> subs1 = new TestSubscriber<>();
        observable1.subscribe(subs1);
        assertEquals(1, holder1.getSubscriberCount());
        
        holder1.getAt(0).onNext("hello");
        subs1.assertValues("hello");

        final TestSubscriber<String> subs2 = new TestSubscriber<>();
        observable1.subscribe(subs2);
        assertEquals(1, holder1.getSubscriberCount());
        subs2.assertValues("hello");
        
        holder1.getAt(0).onNext("world");
        subs1.assertValues("hello", "world");
        subs2.assertValues("hello", "world");
    }

}
