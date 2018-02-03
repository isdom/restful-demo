package org.jocean.restfuldemo.bll;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.jocean.http.Feature;
import org.jocean.http.FullMessage;
import org.jocean.http.MessageBody;
import org.jocean.http.MessageUtil;
import org.jocean.http.StreamUtil;
import org.jocean.http.WriteCtrl;
import org.jocean.http.client.HttpClient;
import org.jocean.http.util.RxNettys;
import org.jocean.idiom.BeanFinder;
import org.jocean.idiom.DisposableWrapper;
import org.jocean.idiom.DisposableWrapperUtil;
import org.jocean.idiom.Terminable;
import org.jocean.netty.BlobRepo;
import org.jocean.netty.util.ByteBufArrayOutputStream;
import org.jocean.restfuldemo.bean.DemoRequest;
import org.jocean.svr.ResponseUtil;
import org.jocean.svr.UntilRequestCompleted;
import org.jocean.svr._100ContinueAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;

import com.google.common.io.ByteStreams;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpContent;
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
import io.netty.handler.codec.http.LastHttpContent;
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
    public Observable<Object> asjson(final Observable<MessageBody> omb) {
        return omb.flatMap(body -> MessageUtil.<DemoRequest>decodeJsonAs(body, DemoRequest.class))
        .flatMap(req -> ResponseUtil.response().body(req, ResponseUtil.TOJSON).build());
    }
    
    @Path("proxy")
    public Observable<Object> proxy(@QueryParam("uri") final String uri, final WriteCtrl ctrl, final Terminable terminable) {
        ctrl.setFlushPerWrite(true);
        final ByteBufArrayOutputStream bbaos = new ByteBufArrayOutputStream(PooledByteBufAllocator.DEFAULT, 8192);
        final ZipOutputStream zipos = new ZipOutputStream(bbaos, CharsetUtil.UTF_8);
        zipos.setLevel(Deflater.BEST_COMPRESSION);
        
        return this._finder.find(HttpClient.class)
                .flatMap(client -> MessageUtil.interaction(client).uri(uri).path("/")
                        .feature(Feature.ENABLE_LOGGING_OVER_SSL).execution()
                        .doOnNext(interaction -> terminable.doOnTerminate(interaction.initiator().closer()))
                        .flatMap(interaction -> interaction.execute()))
                .map(DisposableWrapperUtil.unwrap())
                .flatMap(RxNettys.splitFullHttpMessage())
                .flatMap(httpobj -> {
                    if (httpobj instanceof HttpResponse) {
                        return processResponse(zipos, (HttpResponse)httpobj, "demo.zip", "123.txt");
                    } else if (httpobj instanceof HttpContent) {
                        return zipContent(zipos, bbaos, (HttpContent)httpobj, terminable);
                    } else {
                        return Observable.just(httpobj);
                    }},
                    e -> Observable.error(e),
                    () -> finishZip(zipos, bbaos, terminable)
                );
    }

    private Observable<? extends Object> finishZip(final ZipOutputStream zipos, 
            final ByteBufArrayOutputStream bbaos,
            final Terminable terminable) {
        try {
            zipos.closeEntry();
            zipos.finish();
            return Observable.concat(bbaos2dwbs(bbaos, terminable), Observable.just(LastHttpContent.EMPTY_LAST_CONTENT));
        } catch (Exception e) {
            return Observable.error(e);
        } finally {
            try {
                zipos.close();
            } catch (IOException e1) {
            }
        }
    }

    private Observable<? extends Object> zipContent(final ZipOutputStream zipos, 
            final ByteBufArrayOutputStream bbaos,
            HttpContent content, 
            final Terminable terminable) {
        if (content.content().readableBytes() == 0) {
            return Observable.empty();
        }
        final ByteBufInputStream is = new ByteBufInputStream(content.content());
        try {
            final byte[] bytes = ByteStreams.toByteArray(is);
            zipos.write(bytes);
            zipos.flush();
            return bbaos2dwbs(bbaos, terminable);
        } catch (Exception e) {
            return Observable.error(e);
        }
    }

    private Observable<DisposableWrapper<ByteBuf>> bbaos2dwbs(
            final ByteBufArrayOutputStream bbaos,
            final Terminable terminable) {
        return Observable.from(bbaos.buffers())
                .map(DisposableWrapperUtil.<ByteBuf>wrap(RxNettys.<ByteBuf>disposerOf(), terminable));
    }

    private Observable<? extends Object> processResponse(final ZipOutputStream zipos, 
            final HttpResponse resp, 
            final String zipedName,
            final String contentName) {
        HttpUtil.setTransferEncodingChunked(resp, true);
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM);
        resp.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=" + zipedName);
        try {
            final ZipEntry entry = new ZipEntry(contentName);
            zipos.putNextEntry(entry);
        } catch (Exception e) {
            return Observable.error(e);
        }
        return Observable.just(resp);
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
