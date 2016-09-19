package com.github.davidmoten.rx.testing;

import java.util.List;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Producer;
import rx.Subscriber;
import rx.functions.Func1;
import rx.observers.TestSubscriber;

public class TestSubscriber2<T> extends Subscriber<T> {

    private final TestSubscriber<T> ts;

    public static <T> TestSubscriber2<T> create() {
        return new TestSubscriber2<T>(new TestSubscriber<T>());
    }

    public static <T> TestSubscriber2<T> create(long initialRequest) {
        return new TestSubscriber2<T>(new TestSubscriber<T>(initialRequest));
    }

    public static <T> Func1<Observable<T>, TestSubscriber2<T>> subscribe() {
        return new Func1<Observable<T>, TestSubscriber2<T>>() {

            @Override
            public TestSubscriber2<T> call(Observable<T> o) {
                TestSubscriber2<T> ts = create();
                o.subscribe(ts);
                return ts;
            }

        };
    }
    
    public static <T> Func1<Observable<T>, TestSubscriber2<T>> subscribe(
            final long initialRequest) {
        return new Func1<Observable<T>, TestSubscriber2<T>>() {

            @Override
            public TestSubscriber2<T> call(Observable<T> o) {
                TestSubscriber2<T> ts = create(initialRequest);
                o.subscribe(ts);
                return ts;
            }

        };
    }
    
    public int hashCode() {
        return ts.hashCode();
    }

    public boolean equals(Object obj) {
        return ts.equals(obj);
    }

    public void onStart() {
        ts.onStart();
    }

    public void onCompleted() {
        ts.onCompleted();
    }

    public void setProducer(Producer p) {
        ts.setProducer(p);
    }

    public final int getCompletions() {
        return ts.getCompletions();
    }

    public void onError(Throwable e) {
        ts.onError(e);
    }

    public List<Throwable> getOnErrorEvents() {
        return ts.getOnErrorEvents();
    }

    public void onNext(T t) {
        ts.onNext(t);
    }

    public String toString() {
        return ts.toString();
    }

    public final int getValueCount() {
        return ts.getValueCount();
    }

    public TestSubscriber2<T> requestMore(long n) {
        ts.requestMore(n);
        return this;
    }

    public List<T> getOnNextEvents() {
        return ts.getOnNextEvents();
    }

    public TestSubscriber2<T> assertReceivedOnNext(List<T> items) {
        ts.assertReceivedOnNext(items);
        return this;
    }

    public final boolean awaitValueCount(int expected, long timeout, TimeUnit unit) {
        return ts.awaitValueCount(expected, timeout, unit);
    }

    public TestSubscriber2<T> assertTerminalEvent() {
        ts.assertTerminalEvent();
        return this;
    }

    public TestSubscriber2<T> assertUnsubscribed() {
        ts.assertUnsubscribed();
        return this;
    }

    public TestSubscriber2<T> assertNoErrors() {
        ts.assertNoErrors();
        return this;
    }

    public TestSubscriber2<T> awaitTerminalEvent() {
        ts.awaitTerminalEvent();
        return this;
    }

    public TestSubscriber2<T> awaitTerminalEvent(long timeout, TimeUnit unit) {
        ts.awaitTerminalEvent(timeout, unit);
        return this;
    }

    public TestSubscriber2<T> awaitTerminalEventAndUnsubscribeOnTimeout(long timeout,
            TimeUnit unit) {
        ts.awaitTerminalEventAndUnsubscribeOnTimeout(timeout, unit);
        return this;
    }

    public Thread getLastSeenThread() {
        return ts.getLastSeenThread();
    }

    public TestSubscriber2<T> assertCompleted() {
        ts.assertCompleted();
        return this;
    }

    public TestSubscriber2<T> assertNotCompleted() {
        ts.assertNotCompleted();
        return this;
    }

    public TestSubscriber2<T> assertError(Class<? extends Throwable> clazz) {
        ts.assertError(clazz);
        return this;
    }

    public TestSubscriber2<T> assertError(Throwable throwable) {
        ts.assertError(throwable);
        return this;
    }

    public TestSubscriber2<T> assertNoTerminalEvent() {
        ts.assertNoTerminalEvent();
        return this;
    }

    public TestSubscriber2<T> assertNoValues() {
        ts.assertNoValues();
        return this;
    }

    public TestSubscriber2<T> assertValueCount(int count) {
        ts.assertValueCount(count);
        return this;
    }

    public TestSubscriber2<T> assertValues(T... values) {
        ts.assertValues(values);
        return this;
    }

    public TestSubscriber2<T> assertValue(T value) {
        ts.assertValue(value);
        return this;
    }

    public final TestSubscriber2<T> assertValuesAndClear(T expectedFirstValue,
            T... expectedRestValues) {
        ts.assertValuesAndClear(expectedFirstValue, expectedRestValues);
        return this;
    }

    public TestSubscriber2(TestSubscriber<T> ts) {
        this.ts = ts;
    }

}
