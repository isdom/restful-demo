package org.jocean.restfuldemo.bll2;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
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
import rx.Subscriber;
import rx.functions.Func1;

@Path("/newrest/")
@Controller
public class DemoResource {

    private static final Logger LOG
        = LoggerFactory.getLogger(DemoResource.class);
    
    @Path("stream")
    public Object bigresp(final WritePolicyAware writePolicyAware) {
        final AtomicInteger count = new AtomicInteger(0);
        final Func1<Queue<String>, Boolean> fillContent = content -> {
            if (count.get() < 100) {
                LOG.debug("re-fill content");
                count.incrementAndGet();
                for (int idx = 1; idx < 10000; idx++) {
                    content.add(Integer.toString(idx) + ".");
                }
                return true;
            } else {
                return false;
            }
        };
        
        final Queue<String> content = new LinkedList<>();
        final AtomicReference<Object> lastRef = new AtomicReference<>(null);
        
        final AtomicReference<Subscriber<? super DisposableWrapper<ByteBuf>>> subref = 
                new AtomicReference<>();
        
        writePolicyAware.setWritePolicy(new WritePolicy() {
            @Override
            public void applyTo(final Outboundable outboundable) {
                outboundable.setFlushPerWrite(true);
                outboundable.sended().subscribe(obj -> {
                    LOG.debug("sended :{}", obj);
                    DisposableWrapperUtil.dispose(obj);
                    if (obj == lastRef.get()) {
                        // the last of current send batch
                        final Subscriber<? super DisposableWrapper<ByteBuf>> subscriber = subref.get();
                        buildContentAndSend(content, fillContent, lastRef, subscriber);
                    }});
            }});
        
        return Observable.just(new FullMessage() {

            @SuppressWarnings("unchecked")
            @Override
            public <M extends HttpMessage> M message() {
                final HttpResponse resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                resp.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_PLAIN);
                HttpUtil.setTransferEncodingChunked(resp, true);
                return (M)resp;
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
                        return Observable.unsafeCreate(subscriber -> {
                            subref.set(subscriber);
                            buildContentAndSend(content, fillContent, lastRef, subscriber);
                        });
                    }});
            }});
    }

    private void buildContentAndSend(
            final Queue<String> content, 
            final Func1<Queue<String>, Boolean> fillContent,
            final AtomicReference<Object> lastRef, 
            final Subscriber<? super DisposableWrapper<ByteBuf>> subscriber) {
        if (content.isEmpty()) {
            if (!fillContent.call(content)) {
                subscriber.onCompleted();
                LOG.debug("content onCompleted");
                return;
            }
        }
        sendContentAndMark(content, lastRef, subscriber);
    }

    private void sendContentAndMark(
            final Queue<String> content,
            final AtomicReference<Object> lastRef,
            final Subscriber<? super DisposableWrapper<ByteBuf>> subscriber) {
        LOG.debug("content's count: {} with first element: {}", content.size(), content.peek());
        final List<DisposableWrapper<ByteBuf>> dwbs = toDWBS(content, 5);
        final DisposableWrapper<ByteBuf> last = dwbs.get(dwbs.size()-1);
        LOG.debug("dwbs size: {}, last element: {}", dwbs.size(), last);
        lastRef.set(last);
        for (DisposableWrapper<ByteBuf> dwb : dwbs) {
            subscriber.onNext(dwb);
        }
        dwbs.clear();
    }

    private final List<DisposableWrapper<ByteBuf>> toDWBS(final Queue<String> ids, final int maxBufCount) {
        final List<DisposableWrapper<ByteBuf>> dwbs = new ArrayList<>(maxBufCount);
        final ByteBufAllocator allocator = PooledByteBufAllocator.DEFAULT;
        
        ByteBuf buf = allocator.buffer(8192, 8192);
        String line = ids.peek();
        
        while (null != line) {
            final byte[] bytes = line.getBytes(CharsetUtil.UTF_8);
            if (bytes.length <= buf.maxWritableBytes()) {
                buf.writeBytes(bytes);
                ids.remove();
            } else {
                dwbs.add(RxNettys.wrap4release(buf));
                if (dwbs.size() >= maxBufCount) {
                    return dwbs;
                }
                buf = allocator.buffer(8192, 8192);
            }
            line = ids.peek();
        }
        if (buf.readableBytes() > 0) {
            dwbs.add(RxNettys.wrap4release(buf));
        } else {
            buf.release();
        }
        return dwbs;
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
