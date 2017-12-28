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
import org.jocean.http.WritePolicy.Outboundable;
import org.jocean.http.util.RxNettys;
import org.jocean.idiom.DisposableWrapper;
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
import io.netty.buffer.Unpooled;
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
        final AtomicReference<Outboundable> ref = new AtomicReference<>();
        writePolicyAware.setWritePolicy(new WritePolicy() {

            @Override
            public void applyTo(final Outboundable outboundable) {
                ref.set(outboundable);
                outboundable.setFlushPerWrite(true);
            }});
        final AtomicInteger idx = new AtomicInteger(0);
        return Observable.just(new FullMessage() {

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
                        final AtomicReference<Subscriber> subref = new AtomicReference<>();
                        ref.get().sended().subscribe(obj -> {
                            LOG.debug("sended :{}", obj);
                            if (obj instanceof DisposableWrapper) {
                                final DisposableWrapper<ByteBuf> dwb = (DisposableWrapper<ByteBuf>) obj;
                                dwb.dispose();
                                if (idx.incrementAndGet() < 100) {
                                    subref.get().onNext(buildBuf(idx.getAndIncrement()));
                                } else {
                                    subref.get().onCompleted();
                                }
                            }
                        });
                        return Observable.unsafeCreate(subscriber -> { 
                            subref.set(subscriber);
                            subscriber.onNext(buildBuf(idx.getAndIncrement()));
                        });
//                                Observable.concat(
//                                Observable.just(buildBuf(idx)),
//                                ref.get().sended().flatMap(obj -> {
//                                    final DisposableWrapper<ByteBuf> dwb = (DisposableWrapper<ByteBuf>) obj;
//                                    dwb.dispose();
//                                    if (idx.incrementAndGet() < 100) {
//                                        return Observable.just(buildBuf(idx));
//                                    } else {
//                                        return Observable.empty();
//                                    }
//                                }));
                    }});
            }});
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
    
    private DisposableWrapper<ByteBuf> buildBuf(final int idx) {
        return RxNettys
                .wrap4release(Unpooled.wrappedBuffer(Integer.toString(idx).getBytes()));
    }

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
