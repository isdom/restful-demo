package org.jocean.restfuldemo.bll;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.jocean.http.BodyBuilder;
import org.jocean.http.ContentUtil;
import org.jocean.http.Feature;
import org.jocean.http.FullMessage;
import org.jocean.http.InteractBuilder;
import org.jocean.http.MessageBody;
import org.jocean.http.MessageUtil;
import org.jocean.http.StreamUtil;
import org.jocean.http.WriteCtrl;
import org.jocean.http.client.HttpClient;
import org.jocean.idiom.BeanFinder;
import org.jocean.idiom.DisposableWrapper;
import org.jocean.idiom.DisposableWrapperUtil;
import org.jocean.idiom.Pair;
import org.jocean.idiom.Terminable;
import org.jocean.netty.BlobRepo;
import org.jocean.restfuldemo.bean.DemoRequest;
import org.jocean.svr.AllocatorBuilder;
import org.jocean.svr.ResponseUtil;
import org.jocean.svr.UntilRequestCompleted;
import org.jocean.svr.ZipUtil;
import org.jocean.svr._100ContinueAware;
import org.jocean.wechat.WechatAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
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
@Scope("singleton")
public class DemoResource {

    private static final Logger LOG
        = LoggerFactory.getLogger(DemoResource.class);
    
    @Path("qrcode/{wpa}")
    public Observable<Object> qrcode(@PathParam("wpa") final String wpa, final InteractBuilder ib) {
        return Observable.zip(this._finder.find(HttpClient.class), this._finder.find(wpa, WechatAPI.class), 
                (client, api)-> Pair.of(client, api))
            .flatMap(pair -> {
                final HttpClient client = pair.first;
                final WechatAPI api = pair.second;
                return api.createVolatileQrcode(ib.interact(client), 2592000, "ABC")
                        .map(location->ResponseUtil.redirectOnly(location));
            });
    }
    
    @Path("metaof/{obj}")
    public Observable<String> getSimplifiedObjectMeta(@PathParam("obj") final String objname) {
        return _blobRepo.getSimplifiedObjectMeta(objname).map(meta -> {
            LOG.info("meta:{}", meta);
            if (null != meta.getLastModified()) {
                final Instant last = meta.getLastModified().toInstant();
                final Instant lastDay = last.truncatedTo(ChronoUnit.DAYS);
                final Instant now = Instant.now();
                final Instant nowDay = now.truncatedTo(ChronoUnit.DAYS);
                final Duration duration = Duration.between(lastDay, nowDay);
                return "last:" + last
                    +" \nlastDay:" + lastDay
                    + "\nnow:" + now
                    + "\nnowDay:" + nowDay
                    + "\nDuration in days:" + duration.toDays();
            } else {
                return "Not exist";
            }
        });
    }
    
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
    
    @Path("from/{begin}/to/{end}")
    public Observable<String> pathparam(@PathParam("begin") final String begin, @PathParam("end") final String end,
            final Observable<MessageBody> omb) {
        LOG.info("from {} to {}", begin, end);
        return omb.flatMap(body -> MessageUtil.<String>decodeContentAs(body.content(),
                (buf, cls) -> MessageUtil.parseContentAsString(buf), String.class));
    }
    
    @Path("stream")
    public Object bigresp(final WriteCtrl ctrl, @QueryParam("end") final Integer endNum) {

        final AtomicInteger begin = new AtomicInteger(0);
        final AtomicInteger count = new AtomicInteger(0);
        final int end = endNum.intValue();
        
        ctrl.sended().doOnNext(obj -> DisposableWrapperUtil.dispose(obj)).subscribe();
        
        final Observable<? extends DisposableWrapper<ByteBuf>> content = 
            StreamUtil.<DemoState>buildContent(
                    ctrl.sended(),
                    state2dwb(begin, count, end),
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

    private Func1<DemoState, Observable<DisposableWrapper<ByteBuf>>> state2dwb(
            final AtomicInteger begin,
            final AtomicInteger count, 
            final int end) {
        return state -> {
            LOG.debug("obj with state: {} has been sended", state);
            if (null == state || (state.startid == begin.get() && begin.get() + count.get() - 1 < end)) {
                begin.set(null == state ? 1 : begin.get() + count.get());
                count.set(Math.min(500, end - begin.get() + 1));
                LOG.debug("start new batch from {} to {}", begin.get(), begin.get() + count.get() - 1);
                return Observable.range(begin.get(), count.get()).compose(StreamUtil.src2dwb(
                            ()-> StreamUtil.allocStateableDWB(8192),
                            idx -> (Integer.toString(idx) + ".").getBytes(CharsetUtil.UTF_8),
                            idx -> new DemoState(idx, idx),
                            (idx, st) -> st.endid = idx ));
            } else {
                return null;
            }
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

    static class Formed {
        
        @QueryParam("name")
        public String name;
        
        @QueryParam("sex")
        public String sex;

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Formed [name=").append(name).append(", sex=").append(sex).append("]");
            return builder.toString();
        }
    }
    
    @Path("wwwform")
    public Observable<String> wwwform(final Observable<MessageBody> omb) {
        return omb.flatMap(body -> MessageUtil.decodeContentAs(body.content(),
                        MessageUtil::unserializeAsX_WWW_FORM_URLENCODED, Formed.class))
                .map(formed -> formed.toString());
    }
    
    @Path("null")
    public Observable<String> returnNull(final Observable<HttpObject> req) {
        return null;
    }
    
    @Path("asjson")
    public Observable<Object> asjson(final Observable<MessageBody> omb, final BodyBuilder bb) {
        return omb.flatMap(body -> MessageUtil.<DemoRequest>decodeJsonAs(body, DemoRequest.class))
        .flatMap(req -> ResponseUtil.response().body(bb.build(req, ContentUtil.TOJSON)).build());
    }
    
    @Path("proxy")
    public Observable<Object> proxy(@QueryParam("uri") final String uri, final WriteCtrl ctrl, final Terminable terminable,
            final AllocatorBuilder builder) {
        ctrl.setFlushPerWrite(true);
        
        return this._finder.find(HttpClient.class)
                .flatMap(client -> MessageUtil.interaction(client).uri(uri).path("/")
                        .feature(Feature.ENABLE_LOGGING_OVER_SSL).execution())
                .doOnNext(interaction -> terminable.doOnTerminate(interaction.initiator().closer()))
                .flatMap(interaction -> interaction.execute())
                .compose(MessageUtil.asBody())
                .map(body -> body.content().map(DisposableWrapperUtil.unwrap()))
                .map(content -> {
                    final HttpResponse resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    HttpUtil.setTransferEncodingChunked(resp, true);
                    resp.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=demo.zip");
                    return new FullMessage() {
                        @Override
                        public <M extends HttpMessage> M message() {
                            return (M)resp;
                        }

                        @Override
                        public Observable<? extends MessageBody> body() {
                            return Observable.just(new MessageBody() {
                                @Override
                                public String contentType() {
                                    return HttpHeaderValues.APPLICATION_OCTET_STREAM.toString();
                                }
                                @Override
                                public int contentLength() {
                                    return -1;
                                }
                                @Override
                                public Observable<? extends DisposableWrapper<ByteBuf>> content() {
                                    return ZipUtil.zip().allocator(builder.build(8192))
                                            .entries(Observable.just(ZipUtil.entry("123.txt").content(content).build()))
                                            .hookcloser(closer -> terminable.doOnTerminate(closer))
                                            .build();
                                }});
                        }};
            });
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
    
    @Inject
    private BeanFinder _finder;
}
