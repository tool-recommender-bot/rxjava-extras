package com.github.davidmoten.rx.internal.operators;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import com.github.davidmoten.rx.Actions;
import com.github.davidmoten.rx.Bytes;
import com.github.davidmoten.rx.Checked;
import com.github.davidmoten.rx.Checked.F0;
import com.github.davidmoten.rx.Functions;

import rx.Observable;
import rx.Observer;
import rx.functions.Action2;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.observables.SyncOnSubscribe;

public final class ObservableServerSocket {

    private ObservableServerSocket() {
        // prevent instantiation
    }

    public static Observable<Observable<byte[]>> create(final int port, final int timeoutMs,
            final int bufferSize) {
        return Observable.using( //
                createServerSocketFactory(port, timeoutMs), //
                new Func1<ServerSocket, Observable<Observable<byte[]>>>() {
                    @Override
                    public Observable<Observable<byte[]>> call(ServerSocket serverSocket) {
                        return createServerSocketObservable(serverSocket, timeoutMs, bufferSize);
                    }
                }, //
                Actions.close(), //
                true);
    }

    private static Func0<ServerSocket> createServerSocketFactory(final int port,
            final int timeoutMs) {
        return Checked.f0(new F0<ServerSocket>() {
            @Override
            public ServerSocket call() throws Exception {
                return createServerSocket(port, timeoutMs);
            }
        });
    }

    private static ServerSocket createServerSocket(int port, long timeoutMs) throws IOException {
        ServerSocket s = new ServerSocket(port);
        s.setSoTimeout((int) timeoutMs);
        return s;
    }

    private static Observable<Observable<byte[]>> createServerSocketObservable(
            ServerSocket serverSocket, final long timeoutMs, final int bufferSize) {
        return Observable.create( //
                SyncOnSubscribe.<ServerSocket, Observable<byte[]>> createSingleState( //
                        Functions.constant0(serverSocket), //
                        new Action2<ServerSocket, Observer<? super Observable<byte[]>>>() {

                            @Override
                            public void call(ServerSocket ss,
                                    Observer<? super Observable<byte[]>> observer) {
                                acceptConnection(timeoutMs, bufferSize, ss, observer);
                            }}
                        ));
    }

    private static void acceptConnection(long timeoutMs, int bufferSize, ServerSocket ss,
            Observer<? super Observable<byte[]>> observer) {
        Socket socket;
        while (true) {
            try {
                socket = ss.accept();
                observer.onNext(createSocketObservable(socket, timeoutMs, bufferSize));
                break;
            } catch (SocketTimeoutException e) {
                // timed out so will continue waiting
            } catch (IOException e) {
                // unknown problem
                observer.onError(e);
                break;
            }
        }
    }

    private static Observable<byte[]> createSocketObservable(final Socket socket, long timeoutMs,
            final int bufferSize) {
        setTimeout(socket, timeoutMs);
        return Observable.using( //
                Checked.f0(new F0<InputStream>() {
                    @Override
                    public InputStream call() throws Exception {
                        return socket.getInputStream();
                    }
                }), //
                new Func1<InputStream, Observable<byte[]>>() {
                    @Override
                    public Observable<byte[]> call(InputStream is) {
                        return Bytes.from(is, bufferSize);
                    }
                }, //
                Actions.close(), //
                true);
    }

    private static void setTimeout(Socket socket, long timeoutMs) {
        try {
            socket.setSoTimeout((int) timeoutMs);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

}