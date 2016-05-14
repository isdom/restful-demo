package org.jocean.rx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.jocean.idiom.rx.SubscriberHolder;
import org.junit.Test;

public class SingleTestCase {
    
    @Test
    public final void testFlatMapTriple() throws InterruptedException {
        final AtomicBoolean unsubscribed1 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed2 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed3 = new AtomicBoolean(false);
        final SingleSubscriberHolder<String> holder1 = new SingleSubscriberHolder<String>();
        final SingleSubscriberHolder<String> holder2 = new SingleSubscriberHolder<String>();
        final SingleSubscriberHolder<String> holder3 = new SingleSubscriberHolder<String>();
        
        TestUtil.createSingleByHolder(unsubscribed1, holder1)
        .flatMap(TestUtil.flatMapFuncOf(unsubscribed2, holder2))
        .flatMap(TestUtil.flatMapFuncOf(unsubscribed3, holder3))
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
    
    @Test
    public final void testFlatMapMixedSingleAndObservable() throws InterruptedException {
        final AtomicBoolean unsubscribed1 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed2 = new AtomicBoolean(false);
        final AtomicBoolean unsubscribed3 = new AtomicBoolean(false);
        final SubscriberHolder<String> holder1 = new SubscriberHolder<String>();
        final SingleSubscriberHolder<String> holder2 = new SingleSubscriberHolder<String>();
//        final SingleSubscriberHolder<String> holder3 = new SingleSubscriberHolder<String>();
//        final SubscriberHolder<String> holder2 = new SubscriberHolder<String>();
        final SubscriberHolder<String> holder3 = new SubscriberHolder<String>();
        
        TestUtil.createObservableByHolder(unsubscribed1, holder1)
        .toSingle()
//        .toObservable()
        .flatMap(TestUtil.flatMapFuncOf(unsubscribed2, holder2))
        .flatMapObservable(TestUtil.flatMapFuncOf(unsubscribed3, holder3))
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
        holder2.getAt(0).onSuccess("world");
//        holder2.getAt(0).onNext("world");
//        holder2.getAt(0).onCompleted();
        
        assertFalse(unsubscribed1.get());
        assertTrue(unsubscribed2.get());
        assertFalse(unsubscribed3.get());
        
        assertEquals(1, holder3.getSubscriberCount());
//        holder3.getAt(0).onSuccess("rx");
        holder3.getAt(0).onNext("hello");
        holder3.getAt(0).onCompleted();
        
        assertTrue(unsubscribed1.get());
        assertTrue(unsubscribed2.get());
        assertTrue(unsubscribed3.get());
    }
}
