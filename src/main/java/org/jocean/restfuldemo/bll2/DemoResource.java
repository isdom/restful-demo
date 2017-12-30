package org.jocean.restfuldemo.bll2;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.http.FullMessage;
import org.jocean.http.MessageBody;
import org.jocean.http.WritePolicy;
import org.jocean.http.util.RxNettys;
import org.jocean.idiom.DisposableWrapper;
import org.jocean.idiom.DisposableWrapperUtil;
import org.jocean.netty.BlobRepo;
import org.jocean.restfuldemo.bean.DemoRequest;
import org.jocean.svr.MessageDecoder;
import org.jocean.svr.ResponseUtil;
import org.jocean.svr.UntilRequestCompleted;
import org.jocean.svr.WritePolicyAware;
import org.jocean.svr._100ContinueAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import rx.Observable;
import rx.Observable.Transformer;
import rx.functions.Action0;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.observables.ConnectableObservable;

@Path("/newrest/")
@Controller
public class DemoResource {

    private static final Logger LOG
        = LoggerFactory.getLogger(DemoResource.class);
    
    @Path("stream")
    public Object bigresp(final WritePolicyAware writePolicyAware) {

        final AtomicReference<Observable<? extends DisposableWrapper<ByteBuf>>> contentRef = new AtomicReference<>();

        writePolicyAware.setWritePolicy(new WritePolicy() {
            @Override
            public void applyTo(final Outboundable outboundable) {
                outboundable.setFlushPerWrite(true);
                contentRef.set(buildContent(outboundable.sended().doOnNext(obj -> DisposableWrapperUtil.dispose(obj)), 
                        Observable.range(1, 1000).map(idx -> Integer.toString(idx) + ".").compose(string2dwb())));
            }
        });

        return Observable.just(new FullMessage() {

            @SuppressWarnings("unchecked")
            @Override
            public <M extends HttpMessage> M message() {
                final HttpResponse resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                resp.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
                HttpUtil.setTransferEncodingChunked(resp, true);
                return (M) resp;
            }

            @Override
            public Observable<? extends MessageBody> body() {
                return Observable.just(new MessageBody() {

                    @Override
                    public String contentType() {
                        return null;
                    }

                    @Override
                    public int contentLength() {
                        return 0;
                    }

                    @Override
                    public Observable<? extends DisposableWrapper<ByteBuf>> content() {
                        return contentRef.get();
                    }
                });
            }
        });
    }

    private Observable<? extends DisposableWrapper<ByteBuf>> buildContent(final Observable<Object> sended,
            final Observable<DisposableWrapper<ByteBuf>> src) {
        final ConnectableObservable<DisposableWrapper<ByteBuf>> endSwitch = Observable
                .<DisposableWrapper<ByteBuf>>empty().replay();
        final AtomicReference<Object> firstRef = new AtomicReference<>(null);
        final AtomicInteger count = new AtomicInteger(0);
        
        final Observable<? extends DisposableWrapper<ByteBuf>> cachedContent = 
                sended.compose(sended2content(
                        obj -> null == firstRef.get() || obj == firstRef.get(), 
                        () -> count.getAndIncrement() >= 100, 
                        () -> {
                            firstRef.set(null);
                            return src.doOnNext(dwb -> firstRef.compareAndSet(null, dwb));
                        },
                        () -> endSwitch.connect()))
                .cache();
        cachedContent.subscribe();
        return Observable.switchOnNext(Observable.just(cachedContent, endSwitch));
    }
    
    private Transformer<Object, DisposableWrapper<ByteBuf>> sended2content(
            final Func1<Object, Boolean> needNextContent,
            final Func0<Boolean> isContentComplete, 
            final Func0<Observable<DisposableWrapper<ByteBuf>>> buildContent,
            final Action0 onEnd) {
        return sended->sended.flatMap(obj -> {
            if (needNextContent.call(obj)) {
                if (!isContentComplete.call()) {
                    return buildContent.call();
                } else {
                    onEnd.call();
                }
            }
            return Observable.empty();
        });
    }

    private Transformer<String, DisposableWrapper<ByteBuf>> string2dwb() {
        final ByteBufAllocator allocator = PooledByteBufAllocator.DEFAULT;
        final AtomicReference<ByteBuf> bufRef = new AtomicReference<>();
        
        return contents -> {
            return contents.flatMap(s -> {
                if (null == bufRef.get()) {
                    bufRef.set(allocator.buffer(8192, 8192));
                }
                final byte[] bytes = s.getBytes(CharsetUtil.UTF_8);
                if (bytes.length <= bufRef.get().maxWritableBytes()) {
                    bufRef.get().writeBytes(bytes);
                    return Observable.empty();
                } else {
                    final ByteBuf newbuf = allocator.buffer(8192, 8192);
                    newbuf.writeBytes(bytes);
                    return Observable.just(RxNettys.wrap4release(bufRef.getAndSet(newbuf)));
                }
            }, e -> Observable.error(e),
            () -> {
                if (null == bufRef.get()) {
                    return Observable.empty();
                } else {
                    final ByteBuf last = bufRef.getAndSet(null);
                    if (last.readableBytes() > 0) {
                        return Observable.just(RxNettys.wrap4release(last));
                    } else {
                        last.release();
                        return Observable.empty();
                    }
                }
            }).doOnUnsubscribe(() -> {
                final ByteBuf last = bufRef.getAndSet(null);
                if (null != last) {
                    last.release();
                }
            });
        };
    }

    @Path("hello")
    @OPTIONS
    @POST
    public Observable<Object> hello(
            final HttpMethod method,
            final UntilRequestCompleted<Object> urc) {
        return Observable.just(ResponseUtil.redirectOnly("http://baidu.com/world"))
                .compose(urc)
                ;
    }

    @Path("hi")
    public Observable<String> hiAsString(
            @QueryParam("name") final String name,
            @HeaderParam("User-Agent") final String ua,
            final UntilRequestCompleted<String> urc) {
        return Observable.just("hi, ", name, "'s ", ua).compose(urc);
    }

    @Path("null")
    public Observable<String> returnNull(final Observable<HttpObject> req) {
        return null;
    }
    
    @Path("asjson")
    public Observable<Object> asjson(final Observable<MessageDecoder> omd) {
        return omd.flatMap(decoder -> decoder.<DemoRequest>decodeJsonAs(DemoRequest.class))
            .map(req -> ResponseUtil.responseAsJson(200, req));
    }
    
    @Path("foo")
    public Observable<String> foo(
            @QueryParam("name") final String name,
            @HeaderParam("user-agent") final String ua,
            @HeaderParam("x-forwarded-for") final String peerip) {
        return Observable.just("hi, ", name, "'s ", ua, ",from:", peerip);
    }
    
    @Path("foo100")
    public Observable<Object> fooReply100continue(
            final HttpMethod httpmethod,
            @QueryParam("name") final String name,
            @HeaderParam("user-agent") final String ua,
            @HeaderParam("content-length") final String size,
            final UntilRequestCompleted<Object> urc,
            final _100ContinueAware handle100continue
            ) {
        handle100continue.setPredicate(new Func1<HttpRequest, Integer>() {
            @Override
            public Integer call(final HttpRequest r) {
                if (Integer.parseInt(size) >= 1028) {
                    LOG.info("upstream too long, sendback 417");
                    return 417;
                } else {
                    LOG.info("sendback 100-continue");
                    return 100;
                }
            }
        });
        return Observable.just(ResponseUtil.respWithStatus(200),
                httpmethod.toString(),
                "/",
                "hi, ", 
                name, 
                "'s ", 
                ua,
                ResponseUtil.emptyBody())
            .compose(urc)
            ;
    }
    
//    @Path("upload")
//    @POST
//    public Observable<String> upload(final Observable<MessageDecoder> omd) {
//        final AtomicInteger idx = new AtomicInteger(0);
//        return omd.flatMap( decoder -> {
//            LOG.debug(idx.get() + ": MessageDecoder {}", decoder);
//            if (decoder.contentType().startsWith(HttpHeaderValues.APPLICATION_JSON.toString())) {
//                return decoder.decodeJsonAs(DemoRequest.class).map(req -> req.toString());
//            } else {
//                return _blobRepo.putBlob(Integer.toString(idx.get()), decoder.blobProducer())
//                    .map(new Func1<String, String>() {
//                        @Override
//                        public String call(final String key) {
//                            return "\r\n[" 
//                                    + idx.getAndIncrement() 
//                                    + "] upload:" + decoder.contentType()
//                                    + " and saved as key("
//                                    + key + ")";
//                        }});
//            }
//        });
//    }
    
    @Inject
    private BlobRepo _blobRepo;
    
//    @HeaderParam("X-Forwarded-For")
//    private String _peerip;
//    
//    @HeaderParam("User-Agent")
//    private String _ua;
//    
//    @HeaderParam("expect")
//    private String _expect;
//    
//    @QueryParam("name")
//    private String _name;
}
