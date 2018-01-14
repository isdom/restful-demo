package org.jocean.restfuldemo.bll2;

import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.http.FullMessage;
import org.jocean.http.MessageBody;
import org.jocean.http.WriteCtrl;
import org.jocean.idiom.DisposableWrapper;
import org.jocean.idiom.DisposableWrapperUtil;
import org.jocean.idiom.StateableUtil;
import org.jocean.netty.BlobRepo;
import org.jocean.restfuldemo.StreamUtil;
import org.jocean.restfuldemo.bean.DemoRequest;
import org.jocean.svr.MessageDecoder;
import org.jocean.svr.ResponseUtil;
import org.jocean.svr.UntilRequestCompleted;
import org.jocean.svr._100ContinueAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;

import io.netty.buffer.ByteBuf;
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
import rx.functions.Func1;

@Path("/newrest/")
@Controller
public class DemoResource {

    private static final Logger LOG
        = LoggerFactory.getLogger(DemoResource.class);
    
    static class DemoState {
        int startid;
        int endid;
        
        DemoState(int startid, int endid) {
            this.startid = startid;
            this.endid = endid;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("DemoState [startid=").append(startid).append(", endid=").append(endid).append("]");
            return builder.toString();
        }
    }
    
    private Func1<DemoState, Observable<DisposableWrapper<ByteBuf>>> state2dwbs(
            final AtomicInteger begin,
            final AtomicInteger count, final int end) {
        return state -> {
            LOG.debug("on state: {}", state);
            if (null == state || (state.startid == begin.get() && begin.get() + count.get() - 1 < end)) {
                begin.set(null == state ? 1 : begin.get() + count.get());
                count.set(Math.min(500, end - begin.get() + 1));
                LOG.debug("start new batch from {} to {}", begin.get(), begin.get() + count.get() - 1);
                return Observable.range(begin.get(), count.get()).doOnCompleted(()->{
                  LOG.info("range begin ({}) count ({}) end", begin.get(), count.get());
                }).compose(StreamUtil.src2dwb(
                        () -> StreamUtil.allocStateableDWB(8192),
                        idx -> (Integer.toString(idx) + ".").getBytes(CharsetUtil.UTF_8),
                        idx -> new DemoState(idx, idx),
                        (idx, st) -> st.endid = idx ));
            } else {
                return null;
            }
        };
    }
    
    @Path("stream")
    public Object bigresp(final WriteCtrl ctrl, @QueryParam("end") final Integer endNum) {

        final AtomicInteger begin = new AtomicInteger(0);
        final AtomicInteger count = new AtomicInteger(0);
        final int end = endNum.intValue();
        final Observable<? extends DisposableWrapper<ByteBuf>> content = 
        /*
                ctrl.sended().doOnNext(obj -> DisposableWrapperUtil.dispose(obj))
                    .flatMap(obj -> {
                        final DemoState state = StateableUtil.stateOf(obj);
                        
                        final Observable<DisposableWrapper<ByteBuf>> dwbs = state2dwbs.call(state);
                        if (null != dwbs) {
                            LOG.info("sended2content: null != dwbs");
                            return dwbs;
                        }
                        LOG.info("sended2content: Observable.empty()");
                        return Observable.empty();
                    }).cache();
        content.subscribe();
        */
        
        StreamUtil.<DemoState>buildContent(
                ctrl.sended().doOnNext(obj -> DisposableWrapperUtil.dispose(obj)),
                state -> {
                    LOG.debug("on state: {}", state);
                    if (null == state || (state.startid == begin.get() && begin.get() + count.get() - 1 < end)) {
                        begin.set(null == state ? 1 : begin.get() + count.get());
                        count.set(Math.min(500, end - begin.get() + 1));
                        LOG.debug("start new batch from {} to {}", begin.get(), begin.get() + count.get() - 1);
                        return Observable.range(begin.get(), count.get()).doOnCompleted(()->{
                          LOG.info("range begin ({}) count ({}) end", begin.get(), count.get());
                        }).compose(StreamUtil.src2dwb(
                                ()-> StreamUtil.allocStateableDWB(8192),
                                idx ->(Integer.toString(idx) + ".").getBytes(CharsetUtil.UTF_8),
                                idx -> new DemoState(idx, idx),
                                (idx, st) -> st.endid = idx ));
                    } else {
                        return null;
                    }
                },
                state -> end == state.endid)
        .doOnNext(dwb -> {
            LOG.info("buildContent : onNext {}", dwb);
        });
        
        ctrl.setFlushPerWrite(true);

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
                        return content;
                    }
                });
            }
        });
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
