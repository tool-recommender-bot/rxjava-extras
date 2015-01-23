package com.github.davidmoten.rx.operators;

import rx.Observable.Operator;
import rx.Subscriber;

public class OperatorUnsubscribeEagerly<T> implements Operator<T, T> {

	public static class Singleton {
		private static final OperatorUnsubscribeEagerly<?> INSTANCE = new OperatorUnsubscribeEagerly<Object>();

		@SuppressWarnings("unchecked")
		public static final <T> OperatorUnsubscribeEagerly<T> instance() {
			return (OperatorUnsubscribeEagerly<T>) INSTANCE;
		}
	}

	@Override
	public Subscriber<? super T> call(final Subscriber<? super T> child) {
		Subscriber<T> parent = new Subscriber<T>() {

			@Override
			public void onCompleted() {
				unsubscribe();
				child.onCompleted();
			}

			@Override
			public void onError(Throwable e) {
				child.onError(e);
			}

			@Override
			public void onNext(T t) {
				child.onNext(t);
			}

		};
		child.add(parent);
		return parent;
	}

}
