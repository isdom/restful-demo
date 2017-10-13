package org.jocean.restfuldemo.bll2;

import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.netty.BlobRepo;
import org.jocean.restfuldemo.bean.DemoRequest;
import org.jocean.svr.MessageBody;
import org.jocean.svr.MessageDecoder;
import org.jocean.svr.MessageResponse;
import org.jocean.svr.ResponseUtil;
import org.jocean.svr.UntilRequestCompleted;
import org.jocean.svr._100ContinueAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import rx.Observable;
import rx.functions.Func1;

@Path("/newrest/")
public class DemoResource {

    private static final Logger LOG
        = LoggerFactory.getLogger(DemoResource.class);
    
    static class Redirectable implements MessageResponse, MessageBody {

        public Redirectable(final String location) {
            this._location = location;
        }
        
        @Override
        public int status() {
            return 302;
        }
        
        @Override
        public ByteBuf content() {
            return null;
        }

        @HeaderParam("location")
        private String _location;
    }
    
    @Path("hello")
    @OPTIONS
    @POST
    public Observable<Object> hello(
            final HttpMethod method,
            final UntilRequestCompleted<Object> urc) {
        return Observable.just(new Redirectable("http://baidu.com/world"))
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
        return omd.<DemoRequest>flatMap(decoder -> decoder.decodeJsonAs(DemoRequest.class))
            .map(req -> ResponseUtil.responseAsJson(200, req));
    }
    
    @Path("foo")
    public Observable<String> foo(
            @QueryParam("name") final String name,
            @HeaderParam("user-agent") final String ua,
            @HeaderParam("x-forwarded-for") final String peerip,
            final Observable<MessageDecoder> omd) {
        return omd.flatMap(new Func1<MessageDecoder, Observable<String>>() {
                @Override
                public Observable<String> call(final MessageDecoder decoder) {
                    return Observable.just("hi, ", name, "'s ", ua, ",from:", peerip);
                }});
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
    
    @Path("upload")
    @POST
    public Observable<String> upload(final Observable<MessageDecoder> omd) {
        final AtomicInteger idx = new AtomicInteger(0);
        return omd.flatMap( decoder -> {
            LOG.debug(idx.get() + ": MessageDecoder {}", decoder);
            if (decoder.contentType().startsWith(HttpHeaderValues.APPLICATION_JSON.toString())) {
                return decoder.decodeJsonAs(DemoRequest.class).map(req -> req.toString());
            } else {
                return _blobRepo.putBlob(Integer.toString(idx.get()), decoder.blobProducer())
                    .map(new Func1<String, String>() {
                        @Override
                        public String call(final String key) {
                            return "\r\n[" 
                                    + idx.getAndIncrement() 
                                    + "] upload:" + decoder.contentType()
                                    + " and saved as key("
                                    + key + ")";
                        }});
            }
        });
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
