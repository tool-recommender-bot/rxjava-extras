package com.github.davidmoten.rx.internal.operators;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.github.davidmoten.rx.buffertofile.DataSerializer;
import com.github.davidmoten.rx.buffertofile.Options;
import com.github.davidmoten.util.Preconditions;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Observable.Operator;
import rx.Producer;
import rx.Scheduler;
import rx.Scheduler.Worker;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func0;
import rx.internal.operators.BackpressureUtils;
import rx.observers.Subscribers;

public final class OperatorBufferToFile<T> implements Operator<T, T> {

	private final DataSerializer<T> dataSerializer;
	private final Scheduler scheduler;
	private final Options options;

	public OperatorBufferToFile(DataSerializer<T> dataSerializer, Scheduler scheduler, Options options) {
		Preconditions.checkNotNull(dataSerializer);
		Preconditions.checkNotNull(scheduler);
		Preconditions.checkNotNull(options);
		this.scheduler = scheduler;
		this.dataSerializer = dataSerializer;
		this.options = options;
	}

	@Override
	public Subscriber<? super T> call(Subscriber<? super T> child) {

		// create the file based queue
		final QueueWithResources<T> queue = createFileBasedQueue(dataSerializer, options);

		// hold a reference to the queueProducer which will be set on
		// subscription to `source`
		final AtomicReference<QueueProducer<T>> queueProducer = new AtomicReference<QueueProducer<T>>();

		// emissions will propagate to downstream via this worker
		final Worker worker = scheduler.createWorker();

		// set up the observable to read from the file based queue
		Observable<T> source = Observable.create(new OnSubscribeFromQueue<T>(queueProducer, queue, worker, options));

		// create the parent subscriber
		Subscriber<T> parentSubscriber = new ParentSubscriber<T>(queueProducer);

		// link unsubscription
		child.add(parentSubscriber);

		// close and delete file based queues in RollingQueue on unsubscription
		child.add(queue);

		// ensure onStart not called twice
		Subscriber<T> wrappedChild = Subscribers.wrap(child);

		// ensure worker gets unsubscribed (last)
		child.add(worker);

		// subscribe to queue
		source.unsafeSubscribe(wrappedChild);

		return parentSubscriber;
	}

	private static <T> QueueWithResources<T> createFileBasedQueue(final DataSerializer<T> dataSerializer,
			final Options options) {
		if (options.rolloverEvery() == Long.MAX_VALUE && options.rolloverSizeBytes() == Long.MAX_VALUE) {
			// skip the Rollover version
			return new FileBasedSPSCQueue<T>(options.bufferSizeBytes(), options.fileFactory().call(), dataSerializer);
		} else {
			final Func0<QueueWithResources<T>> queueFactory = new Func0<QueueWithResources<T>>() {
				@Override
				public QueueWithResources<T> call() {
					// create the file to be used for queue storage (and whose
					// file name will determine the names of other files used
					// for storage if multiple are required per queue)
					File file = options.fileFactory().call();

					return new FileBasedSPSCQueue<T>(options.bufferSizeBytes(), file, dataSerializer);
				}
			};
			return new QueueWithResourcesNonBlockingUnsubscribe<T>(
					new RollingSPSCQueue<T>(queueFactory, options.rolloverSizeBytes(), options.rolloverEvery()));
		}
	}

	private static final class OnSubscribeFromQueue<T> implements OnSubscribe<T> {

		private final AtomicReference<QueueProducer<T>> queueProducer;
		private final QueueWithResources<T> queue;
		private final Worker worker;
		private final Options options;

		OnSubscribeFromQueue(AtomicReference<QueueProducer<T>> queueProducer, QueueWithResources<T> queue,
				Worker worker, Options options) {
			this.queueProducer = queueProducer;
			this.queue = queue;
			this.worker = worker;
			this.options = options;
		}

		@Override
		public void call(Subscriber<? super T> child) {
			QueueProducer<T> qp = new QueueProducer<T>(queue, child, worker, options.delayError());
			queueProducer.set(qp);
			child.setProducer(qp);
		}
	}

	private static final class ParentSubscriber<T> extends Subscriber<T> {

		private final AtomicReference<QueueProducer<T>> queueProducer;

		ParentSubscriber(AtomicReference<QueueProducer<T>> queueProducer) {
			this.queueProducer = queueProducer;
		}

		@Override
		public void onStart() {
			request(Long.MAX_VALUE);
		}

		@Override
		public void onCompleted() {
			queueProducer.get().onCompleted();
		}

		@Override
		public void onError(Throwable e) {
			queueProducer.get().onError(e);
		}

		@Override
		public void onNext(T t) {
			queueProducer.get().onNext(t);
		}

	}

	private static final class QueueProducer<T> extends AtomicLong implements Producer, Action0 {

		// inherits from AtomicLong to represent the oustanding requests count

		private static final long serialVersionUID = 2521533710633950102L;

		private final QueueWithResources<T> queue;
		private final AtomicInteger drainRequested = new AtomicInteger(0);
		private final Subscriber<? super T> child;
		private final Worker worker;
		private final boolean delayError;
		private volatile boolean done;

		// Is set just before the volatile `done` is set and read just after
		// `done` is read. Thus doesn't need to be volatile.
		private Throwable error = null;

		QueueProducer(QueueWithResources<T> queue, Subscriber<? super T> child, Worker worker, boolean delayError) {
			super();
			this.queue = queue;
			this.child = child;
			this.worker = worker;
			this.delayError = delayError;
			this.done = false;
		}

		void onNext(T t) {
			if (!queue.offer(t)) {
				onError(new RuntimeException(
						"could not place item on queue (queue.offer(item) returned false), item= " + t));
				return;
			} else {
				drain();
			}
		}

		void onError(Throwable e) {
			// must assign error before assign done = true to avoid race
			// condition in finished() and also so appropriate memory barrier in
			// place given error is non-volatile
			error = e;
			done = true;
			drain();
		}

		void onCompleted() {
			done = true;
			drain();
		}

		@Override
		public void request(long n) {
			if (n > 0) {
				BackpressureUtils.getAndAddRequest(this, n);
				drain();
			}
		}

		private void drain() {
			// only schedule a drain if current drain has finished
			// otherwise the drainRequested counter will be incremented
			// and the drain loop will ensure that another drain cycle occurs if
			// required
			if (!child.isUnsubscribed() && drainRequested.getAndIncrement() == 0) {
				worker.schedule(this);
			}
		}

		// this method executed from drain() only
		@Override
		public void call() {
			// catch exceptions related to file based queue in drainNow()
			try {
				drainNow();
			} catch (Throwable e) {
				child.onError(e);
			}
		}

		private void drainNow() {

			while (true) {
				// reset drainRequested counter
				drainRequested.set(1);
				if (child.isUnsubscribed()) {
					// leave drainRequested > 0 to prevent more
					// scheduling of drains
					return;
				}
				// get the number of unsatisfied requests
				long requests = get();
				long emitted = 0;
				while (requests > 0) {
					T item = queue.poll();
					if (item == null) {
						// queue is empty
						if (finished()) {
							return;
						} else {
							// another drain was requested so go
							// round again but break out of this
							// while loop to the outer loop so we
							// can update requests and reset drainRequested
							break;
						}
					} else {
						// there was an item on the queue
						child.onNext(item);
						requests--;
						emitted++;
					}
				}
				// try and avoid the addAndGet if possible because it is
				// more expensive than an emitted comparison
				if (emitted != 0) {
					requests = addAndGet(-emitted);
				}
				if (child.isUnsubscribed() || (requests == 0L && finished())) {
					return;
				}
			}
		}

		private boolean finished() {
			if (done) {
				Throwable t = error;
				if (queue.isEmpty()) {
					// first close the queue (which in this case though
					// empty also disposes of its resources)
					queue.unsubscribe();

					if (t != null) {
						child.onError(t);
					} else {
						child.onCompleted();
					}
					// leave drainRequested > 0 so that further drain
					// requests are ignored
					return true;
				} else if (t != null && !delayError) {
					// queue is not empty but we are going to shortcut
					// that because delayError is false

					// first close the queue (which in this case also
					// disposes of its resources)
					queue.unsubscribe();

					// now report the error
					child.onError(t);

					// leave drainRequested > 0 so that further drain
					// requests are ignored
					return true;
				} else {
					// otherwise we need to wait for all items waiting
					// on the queue to be requested and delivered
					// (delayError=true)
					return drainRequested.compareAndSet(1, 0);
				}
			} else {
				return drainRequested.compareAndSet(1, 0);
			}
		}
	}
}
