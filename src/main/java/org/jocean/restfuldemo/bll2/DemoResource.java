package org.jocean.restfuldemo.bll2;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.http.util.RxNettys;
import org.jocean.restfuldemo.bean.DemoRequest;
import org.jocean.svr.Interceptors;
import org.jocean.svr.MessageBody;
import org.jocean.svr.MessageDecoder;
import org.jocean.svr.MessageResponse;
import org.jocean.svr.ParamUtil;
import org.jocean.svr.ResponseUtil;
import org.jocean.svr.ToFullHttpRequest;
import org.jocean.svr.UntilRequestCompleted;
import org.jocean.svr.CORSInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import rx.Observable;
import rx.functions.Func0;
import rx.functions.Func1;

@Path("/newrest/")
@Interceptors({CORSInterceptor.class})
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
    @POST
    public Observable<Object> hello(
            final HttpMethod method,
            final Observable<HttpObject> req, 
            final UntilRequestCompleted<Object> urc) {
        if (method.equals(HttpMethod.OPTIONS)) {
            return ResponseUtil.acceptCORS(req);
        } else {
            return Observable.just(new Redirectable("http://baidu.com/world"))
                    .compose(urc)
                    ;
        }
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
    public Observable<Object> asjson(final Observable<MessageDecoder> omd) {
        return omd.flatMap(new Func1<MessageDecoder, Observable<Object>>() {
            @Override
            public Observable<Object> call(final MessageDecoder decoder) {
                return ResponseUtil.responseAsJson(200, decoder.decodeJsonAs(DemoRequest.class));
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
            @HeaderParam("user-agent") final String ua,
            @HeaderParam("content-length") final String size,
            final Observable<HttpObject> req,
            final ToFullHttpRequest tofull) {
        return req.compose(tofull)
            .flatMap(new Func1<Func0<FullHttpRequest>, Observable<Object>>() {
                @Override
                public Observable<Object> call(final Func0<FullHttpRequest> getfull) {
                    final FullHttpRequest fullreq = getfull.call();
                    try {
                        return Observable.just(ResponseUtil.respWithStatus(200), 
                            httpmethod.toString(),
                            "/",
                            "hi, ", 
                            name, 
                            "'s ", 
                            ua,
                            ResponseUtil.emptyBody());
                    } finally {
                        fullreq.release();
                    }
                }})
            .compose(ResponseUtil.handleExpect100(req, new Func1<HttpRequest, Integer>() {
                @Override
                public Integer call(final HttpRequest r) {
                    LOG.info("sendback 100-continue");
                    if (Integer.parseInt(size) >= 1028) {
                        return 417;
                    } else {
                        return 100;
                    }
                }}));
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
