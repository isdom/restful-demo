package org.jocean.restfuldemo.bll2;

import javax.ws.rs.Path;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import rx.Observable;

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
        return Observable.just("hi, ", "hello", " world!");
    }

    @Path("null")
    public Observable<String> returnNull(final Observable<HttpObject> req) {
        return null;
    }
}
