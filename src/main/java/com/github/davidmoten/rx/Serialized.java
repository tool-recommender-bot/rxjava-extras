package com.github.davidmoten.rx;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.observables.AbstractOnSubscribe;

/**
 * Utility class for writing Observable streams to ObjectOutputStreams and
 * reading Observable streams of indeterminate size from ObjectInputStreams.
 */
public final class Serialized {

    private static final int DEFAULT_BUFFER_SIZE = 8192;

    /**
     * Returns the deserialized objects from the given {@link InputStream} as an
     * {@link Observable} stream.
     * 
     * @param ois
     *            the {@link ObjectInputStream}
     * @param <T>
     *            the generic type of the returned stream
     * @return the stream of deserialized objects from the {@link InputStream}
     *         as an {@link Observable}.
     */
    public static <T extends Serializable> Observable<T> read(final ObjectInputStream ois) {
        return Observable.create(new AbstractOnSubscribe<T, ObjectInputStream>() {

            @Override
            protected ObjectInputStream onSubscribe(Subscriber<? super T> subscriber) {
                return ois;
            }

            @Override
            protected void next(SubscriptionState<T, ObjectInputStream> state) {
                ObjectInputStream ois = state.state();
                try {
                    @SuppressWarnings("unchecked")
                    T t = (T) ois.readObject();
                    state.onNext(t);
                } catch (EOFException e) {
                    state.onCompleted();
                } catch (ClassNotFoundException e) {
                    state.onError(e);
                } catch (IOException e) {
                    state.onError(e);
                }
            }
        });
    }

    /**
     * Returns the deserialized objects from the given {@link File} as an
     * {@link Observable} stream. Uses buffer of size <code>bufferSize</code>
     * buffer reads from the File.
     * 
     * @param file
     *            the input file
     * @param bufferSize
     *            the buffer size for reading bytes from the file.
     * @param <T>
     *            the generic type of the deserialized objects returned in the
     *            stream
     * @return the stream of deserialized objects from the {@link InputStream}
     *         as an {@link Observable}.
     */
    public static <T extends Serializable> Observable<T> read(final File file, final int bufferSize) {
        Func0<ObjectInputStream> resourceFactory = new Func0<ObjectInputStream>() {
            @Override
            public ObjectInputStream call() {
                try {
                    return new ObjectInputStream(new BufferedInputStream(new FileInputStream(file),
                            bufferSize));
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        Func1<ObjectInputStream, Observable<? extends T>> observableFactory = new Func1<ObjectInputStream, Observable<? extends T>>() {

            @Override
            public Observable<? extends T> call(ObjectInputStream is) {
                return read(is);
            }
        };
        Action1<ObjectInputStream> disposeAction = new Action1<ObjectInputStream>() {

            @Override
            public void call(ObjectInputStream ois) {
                try {
                    ois.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return Observable.using(resourceFactory, observableFactory, disposeAction, true);
    }

    /**
     * Returns the deserialized objects from the given {@link File} as an
     * {@link Observable} stream. A buffer size of 8192 bytes is used by
     * default.
     * 
     * @param file
     *            the input file containing serialized java objects
     * @param <T>
     *            the generic type of the deserialized objects returned in the
     *            stream
     * @return the stream of deserialized objects from the {@link InputStream}
     *         as an {@link Observable}.
     */
    public static <T extends Serializable> Observable<T> read(final File file) {
        return read(file, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Returns a duplicate of the input stream but with the side effect that
     * emissions from the source are written to the {@link ObjectOutputStream}.
     * 
     * @param source
     *            the source of objects to write
     * @param oos
     *            the output stream to write to
     * @param <T>
     *            the generic type of the objects being serialized
     * @return re-emits the input stream
     */
    public static <T extends Serializable> Observable<T> write(Observable<T> source,
            final ObjectOutputStream oos) {
        return source.doOnNext(new Action1<T>() {

            @Override
            public void call(T t) {
                try {
                    oos.writeObject(t);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    /**
     * Writes the source stream to the given file in given append mode and using
     * the given buffer size.
     * 
     * @param source
     *            observable stream to write
     * @param file
     *            file to write to
     * @param append
     *            if true writes are appended to file otherwise overwrite the
     *            file
     * @param bufferSize
     *            the buffer size in bytes to use.
     * @param <T>
     *            the generic type of the input stream
     * @return re-emits the input stream
     */
    public static <T extends Serializable> Observable<T> write(final Observable<T> source,
            final File file, final boolean append, final int bufferSize) {
        Func0<ObjectOutputStream> resourceFactory = new Func0<ObjectOutputStream>() {
            @Override
            public ObjectOutputStream call() {
                try {
                    return new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(
                            file, append), bufferSize));
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        Func1<ObjectOutputStream, Observable<? extends T>> observableFactory = new Func1<ObjectOutputStream, Observable<? extends T>>() {

            @Override
            public Observable<? extends T> call(ObjectOutputStream oos) {
                return write(source, oos);
            }
        };
        Action1<ObjectOutputStream> disposeAction = new Action1<ObjectOutputStream>() {

            @Override
            public void call(ObjectOutputStream oos) {
                try {
                    oos.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return Observable.using(resourceFactory, observableFactory, disposeAction, true);
    }

    /**
     * Writes the source stream to the given file in given append mode and using
     * the a buffer size of 8192 bytes.
     * 
     * @param source
     *            observable stream to write
     * @param file
     *            file to write to
     * @param append
     *            if true writes are appended to file otherwise overwrite the
     *            file
     * @param <T>
     *            the generic type of the input stream
     * @return re-emits the input stream
     */
    public static <T extends Serializable> Observable<T> write(final Observable<T> source,
            final File file, final boolean append) {
        return write(source, file, append, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Writes the source stream to the given file in given append mode and using
     * the a buffer size of 8192 bytes.
     * 
     * @param source
     *            observable stream to write
     * @param file
     *            file to write to
     * @param <T>
     *            the generic type of the input stream
     * @return re-emits the input stream
     */
    public static <T extends Serializable> Observable<T> write(final Observable<T> source,
            final File file) {
        return write(source, file, false, DEFAULT_BUFFER_SIZE);
    }
}