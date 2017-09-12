package org.jocean.restfuldemo.bll2;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.http.util.RxNettys;
import org.jocean.restfuldemo.bean.DemoRequest;
import org.jocean.svr.MessageBody;
import org.jocean.svr.MessageResponse;
import org.jocean.svr.ParamUtil;
import org.jocean.svr.ResponseUtil;
import org.jocean.svr.ToFullHttpRequest;
import org.jocean.svr.UntilRequestCompleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import rx.Observable;
import rx.functions.Func0;
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
        
        @HeaderParam("content-length")
        private int _size = 0;
    }
    
    @Path("hello")
    public Observable<Object> hello(final Observable<HttpObject> req, 
            final UntilRequestCompleted<Object> urc) {
//        final FullHttpResponse response = new DefaultFullHttpResponse(
//                HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND, Unpooled.buffer(0));
//
//        // Add 'Content-Length' header only for a keep-alive connection.
//        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
//        response.headers().set(HttpHeaderNames.LOCATION, "http://baidu.com/world");
//        response.headers().set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_STORE);
//        response.headers().set(HttpHeaderNames.PRAGMA, HttpHeaderValues.NO_CACHE);
        
        return Observable.just(new Redirectable("http://baidu.com/world"))
                .compose(urc)
                ;
    }

    @Path("hi")
    public Observable<String> hiAsString(final Observable<HttpObject> req,
            @QueryParam("name") final String name,
            @HeaderParam("User-Agent") final String ua,
            final UntilRequestCompleted<String> urc) {
        return req.compose(RxNettys.asHttpRequest())
//            .doOnNext(ParamUtil.injectHeaderParams(this))
//            .doOnNext(ParamUtil.injectQueryParams(this))
            .flatMap(new Func1<Object, Observable<String>>() {
                @Override
                public Observable<String> call(Object none) {
                    return Observable.just("hi, ", name, "'s ", ua);
                }})
            .compose(urc)
            ;
    }

    @Path("null")
    public Observable<String> returnNull(final Observable<HttpObject> req) {
        return null;
    }
    
    @Path("asjson")
    public Observable<String> asjson(final Observable<HttpObject> req,
            final ToFullHttpRequest tofull) {
        return req.compose(tofull)
            .map(ParamUtil.<DemoRequest>decodeJsonContentAs(DemoRequest.class))
            .flatMap(new Func1<DemoRequest, Observable<String>>() {
                @Override
                public Observable<String> call(final DemoRequest json) {
                    return Observable.just(json.toString());
                }});
    }
    
    @Path("foo")
    public Observable<String> foo(final Observable<HttpObject> req,
            final ToFullHttpRequest tofull) {
        return req.compose(RxNettys.asHttpRequest())
            .doOnNext(ParamUtil.injectHeaderParams(this))
            .doOnNext(ParamUtil.injectQueryParams(this))
            .compose(tofull)
            .flatMap(new Func1<Func0<FullHttpRequest>, Observable<String>>() {
                @Override
                public Observable<String> call(final Func0<FullHttpRequest> getfull) {
                    final FullHttpRequest fullreq = getfull.call();
                    
                    try {
                        LOG.info("fullreq: {}", fullreq);
                        return Observable.just("hi, ", _name, "'s ", _ua);
                    } finally {
                        fullreq.release();
                    }
                }});
    }
    
    //  TBD
    @Path("foo100")
    public Observable<Object> fooReply100continue(
            final HttpMethod httpmethod,
            @QueryParam("name") final String name,
            @HeaderParam("User-Agent") final String ua,
            final Observable<HttpObject> req,
            final ToFullHttpRequest tofull) {
        LOG.info("begin fooReply100continue using {} with ua {}", httpmethod, ua);
        try {
        return Observable.concat(
                req.compose(RxNettys.asHttpRequest())
//                .doOnNext(ParamUtil.injectHeaderParams(this))
//                .doOnNext(ParamUtil.injectQueryParams(this))
                .flatMap(new Func1<HttpRequest, Observable<Object>>() {
                    @Override
                    public Observable<Object> call(final HttpRequest msg) {
                        if (HttpUtil.is100ContinueExpected(msg)) {
                            LOG.info("sendback 100-continue");
//                            return Observable.just((HttpObject)new DefaultFullHttpResponse(
//                                    msg.protocolVersion(),
//                                    HttpResponseStatus.CONTINUE));
                            return ResponseUtil.statusOnly(100);
                        } else {
                            return Observable.empty();
                        }
                    }}),
                req.compose(tofull)
                .flatMap(new Func1<Func0<FullHttpRequest>, Observable<Object>>() {
                    @Override
                    public Observable<Object> call(final Func0<FullHttpRequest> getfull) {
                        final FullHttpRequest fullreq = getfull.call();
                        
                        try {
                            return Observable.just(ResponseUtil.respWithStatus(200), 
                                "hi, ", name, "'s ", ua,
                                ResponseUtil.emptyBody());
                        } finally {
                            fullreq.release();
                        }
                    }})
                );
        } finally {
            LOG.info("endof fooReply100continue");
        }
    }
    
    @HeaderParam("X-Forwarded-For")
    private String _peerip;
    
    @HeaderParam("User-Agent")
    private String _ua;
    
    @HeaderParam("expect")
    private String _expect;
    
    @QueryParam("name")
    private String _name;
}
