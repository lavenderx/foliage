package com.github.lavenderx.netty_rpc.client;

import com.github.lavenderx.netty_rpc.protocol.RpcRequest;
import com.github.lavenderx.netty_rpc.protocol.RpcResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class RpcFuture implements Future<Object> {

    private final Sync sync;
    private final RpcRequest request;
    private final long startTime;
    private final long responseTimeThreshold = 5000;

    private final List<AsyncRpcCallback> pendingCallbacks = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    private RpcResponse response;

    public RpcFuture(RpcRequest request) {
        this.sync = new Sync();
        this.request = request;
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public boolean isDone() {
        return sync.isDone();
    }

    @Override
    public Object get() throws InterruptedException, ExecutionException {
        sync.acquire(-1);
        if (this.response != null) {
            return this.response.getResult();
        } else {
            return null;
        }
    }

    @Override
    public Object get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        boolean success = sync.tryAcquireNanos(-1, unit.toNanos(timeout));
        if (success) {
            if (this.response != null) {
                return this.response.getResult();
            } else {
                return null;
            }
        } else {
            throw new RuntimeException("Timeout exception. Request id: " + this.request.getRequestId()
                    + ". Request class name: " + this.request.getClassName()
                    + ". Request method: " + this.request.getMethodName());
        }
    }

    @Override
    public boolean isCancelled() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
    }

    public void done(RpcResponse response) {
        this.response = response;
        sync.release(1);
        invokeCallbacks();
        // Threshold
        long responseTime = System.currentTimeMillis() - startTime;
        if (responseTime > this.responseTimeThreshold) {
            log.warn("Service response time is too slow. Request id = {}. Response Time = {}ms",
                    response.getRequestId(), responseTime);
        }
    }

    private void invokeCallbacks() {
        lock.lock();
        try {
            pendingCallbacks.forEach(this::runCallback);
        } finally {
            lock.unlock();
        }
    }

    public RpcFuture addCallback(AsyncRpcCallback callback) {
        lock.lock();
        try {
            if (isDone()) {
                runCallback(callback);
            } else {
                this.pendingCallbacks.add(callback);
            }
        } finally {
            lock.unlock();
        }
        return this;
    }

    private void runCallback(final AsyncRpcCallback callback) {
        final RpcResponse res = this.response;
        RpcClient.submit(() -> {
            if (!res.isError()) {
                callback.success(res.getResult());
            } else {
                callback.fail(new RuntimeException("Response error", new Throwable(res.getError())));
            }
        });
    }

    static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -3234409599877003143L;

        // Future status
        private final int done = 1;
        private final int pending = 0;

        protected boolean tryAcquire(int acquires) {
            return getState() == done;
        }

        protected boolean tryRelease(int releases) {
            if (getState() == pending) {
                if (compareAndSetState(pending, done)) {
                    return true;
                }
            }
            return false;
        }

        public boolean isDone() {
            getState();
            return getState() == done;
        }
    }
}