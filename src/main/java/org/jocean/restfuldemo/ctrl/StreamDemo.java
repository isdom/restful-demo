package org.jocean.restfuldemo.ctrl;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.http.WriteCtrl;
import org.jocean.idiom.DisposableWrapperUtil;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.svr.WithStream;
import org.jocean.svr.annotation.HandleError;
import org.jocean.svr.annotation.OnError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocatorMetric;
import io.netty.handler.codec.http.HttpRequest;
import rx.Observable;


@Path("/newrest/")
@Controller
@Scope("prototype")
public class StreamDemo {
    private static final Logger LOG = LoggerFactory.getLogger(StreamDemo.class);

    @HandleError(Exception.class)
    String handleAllError(final HttpRequest req, final Exception e) {
        LOG.warn("error when {}, detail: {}", req.uri(), e);
        return "error when " + req.uri() + "{\n" + ExceptionUtils.exception2detail(e) + "\n}";
    }

    @Path("stream/string1")
    @OnError({
        "org.jocean.restfuldemo.ctrl.ErrorHandler.handleException"
        ,"this.handleAllError"
        })
    public WithStream string1() {
        final AtomicInteger cnt = new AtomicInteger(0);
        return new WithStream() {

            @Override
            public String contentType() {
                return "text/plain";
            }

            @Override
            public void onStream(final StreamContext sctx) {
                if (cnt.get() < 10) {
                    try {
                        sctx.chunkDataOutput().writeUTF(cnt.addAndGet(1) + ", hello");
                    } catch (final IOException e) {
                    }
                    sctx.chunkReady();
                } else {
                    sctx.streamCompleted();
                }
            }};
    }

    @Path("stream/endless")
    @OnError({
        "org.jocean.restfuldemo.ctrl.ErrorHandler.handleException"
        ,"this.handleAllError"
        })
    public WithStream endless(final WriteCtrl ctrl, @QueryParam("dispose") final boolean isDispose) {
        if (isDispose) {
            ctrl.sended().subscribe(obj -> DisposableWrapperUtil.dispose(obj));
        }

        final AtomicInteger cnt = new AtomicInteger(0);
        return new WithStream() {

            @Override
            public String contentType() {
                return "text/plain";
            }

            @Override
            public void onStream(final StreamContext sctx) {
                Observable.timer(1, TimeUnit.SECONDS).subscribe(any -> {
                    try {
                        sctx.chunkDataOutput().writeUTF(Integer.toString(cnt.addAndGet(1)));
                        sctx.chunkDataOutput().writeUTF(",");

                        final PooledByteBufAllocatorMetric allocatorMetric = PooledByteBufAllocator.DEFAULT.metric();
                        sctx.chunkDataOutput().writeUTF(Long.toString(allocatorMetric.directArenas().get(0).numActiveAllocations()));
                        sctx.chunkDataOutput().writeUTF("\n");

                        sctx.chunkReady();

                    } catch (final IOException e) {
                    }
                });
            }};
    }
}
