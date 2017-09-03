package org.jocean.restfuldemo.bll2;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.http.util.RxNettys;
import org.jocean.svr.ParamUtil;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import rx.Observable;
import rx.functions.Func1;

@Path("/newrest/")
public class DemoResource {

    @Path("hello")
    public Observable<HttpObject> hello(final Observable<HttpObject> req) {
        final FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND, Unpooled.buffer(0));

        // Add 'Content-Length' header only for a keep-alive connection.
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        response.headers().set(HttpHeaderNames.LOCATION, "http://baidu.com/world");
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_STORE);
        response.headers().set(HttpHeaderNames.PRAGMA, HttpHeaderValues.NO_CACHE);
        
        return Observable.just((HttpObject)response);
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
    
    @HeaderParam("X-Forwarded-For")
    private String _peerip;
    
    @HeaderParam("User-Agent")
    private String _ua;
    
    @QueryParam("name")
    private String _name;
}
