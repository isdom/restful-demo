package org.jocean.restfuldemo.bll2;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.http.util.RxNettys;
import org.jocean.svr.ParamUtil;
import org.jocean.svr.ToFullHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import rx.Observable;
import rx.functions.Func0;
import rx.functions.Func1;

@Path("/newrest/")
public class DemoResource {

    private static final Logger LOG
        = LoggerFactory.getLogger(DemoResource.class);
    
    @Path("hello")
    public Observable<HttpObject> hello(final Observable<HttpObject> req) {
        final FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND, Unpooled.buffer(0));

        // Add 'Content-Length' header only for a keep-alive connection.
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        response.headers().set(HttpHeaderNames.LOCATION, "http://baidu.com/world");
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_STORE);
        response.headers().set(HttpHeaderNames.PRAGMA, HttpHeaderValues.NO_CACHE);
        
        return Observable.just((HttpObject)response).delaySubscription(req.last());
    }

    @Path("hi")
    public Observable<String> hiAsString(final Observable<HttpObject> req) {
        return req.compose(RxNettys.asHttpRequest())
            .doOnNext(ParamUtil.injectHeaderParams(this))
            .doOnNext(ParamUtil.injectQueryParams(this))
            .flatMap(new Func1<Object, Observable<String>>() {
                @Override
                public Observable<String> call(Object none) {
                    return Observable.just("hi, ", _name, "'s ", _ua);
                }});
    }

    @Path("null")
    public Observable<String> returnNull(final Observable<HttpObject> req) {
        return null;
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
    public Observable<HttpObject> fooReply100continue(final Observable<HttpObject> req,
            final ToFullHttpRequest tofull) {
        LOG.info("begin fooReply100continue");
        try {
        return Observable.concat(
                req.compose(RxNettys.asHttpRequest())
                .doOnNext(ParamUtil.injectHeaderParams(this))
                .doOnNext(ParamUtil.injectQueryParams(this))
                .flatMap(new Func1<HttpRequest, Observable<HttpObject>>() {
                    @Override
                    public Observable<HttpObject> call(final HttpRequest msg) {
                        if (HttpUtil.is100ContinueExpected(msg)) {
                            LOG.info("sendback 100-continue");
                            return Observable.just((HttpObject)new DefaultFullHttpResponse(
                                    msg.protocolVersion(),
                                    HttpResponseStatus.CONTINUE));
                        } else {
                            return Observable.empty();
                        }
                    }}),
                req.compose(tofull)
                .flatMap(new Func1<Func0<FullHttpRequest>, Observable<HttpObject>>() {
                    @Override
                    public Observable<HttpObject> call(final Func0<FullHttpRequest> getfull) {
                        final FullHttpRequest fullreq = getfull.call();
                        
                        try {
                            LOG.info("fullreq: {}", fullreq);
                            final FullHttpResponse response = new DefaultFullHttpResponse(
                                    HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND, Unpooled.buffer(0));
    
                            // Add 'Content-Length' header only for a keep-alive connection.
                            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
                            response.headers().set(HttpHeaderNames.LOCATION, "http://baidu.com/world");
                            response.headers().set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_STORE);
                            response.headers().set(HttpHeaderNames.PRAGMA, HttpHeaderValues.NO_CACHE);
                            
                            return Observable.just((HttpObject)response);
    //                        return Observable.just("hi, ", _name, "'s ", _ua);
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
