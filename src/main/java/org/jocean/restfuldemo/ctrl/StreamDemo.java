package org.jocean.restfuldemo.ctrl;

import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.CookieParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;

import org.jocean.http.WriteCtrl;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.svr.MutableResponseBean;
import org.jocean.svr.ResponseUtil;
import org.jocean.svr.WithSubscriber;
import org.jocean.svr.annotation.HandleError;
import org.jocean.svr.annotation.OnError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.google.common.base.Charsets;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action2;


@Path("/newrest/")
@Controller
@Scope("prototype")
public class StreamDemo {
    private static final Logger LOG = LoggerFactory.getLogger(StreamDemo.class);

    @HandleError(Exception.class)
    String handleAllError(final HttpRequest req, final Exception e) {
        LOG.warn("error when {}, detail: {}", req.uri(), e);
        return "error when " + req.uri() + "{\n" + ExceptionUtils.exception2detail(e) + "\n}";
    }

    @Path("stream/string1")
    @OnError({
        "org.jocean.restfuldemo.ctrl.ErrorHandler.handleException"
        ,"this.handleAllError"
        })
    public WithSubscriber<String> string1() {
        final AtomicInteger cnt = new AtomicInteger(0);
        return new WithSubscriber<String>() {

            @Override
            public String contentType() {
                return "text/plain";
            }

            @Override
            public void onSubscriber(final Subscriber<String> subscriber) {
                if (cnt.get() < 10) {
                    subscriber.onNext(cnt.addAndGet(1) + ", hello");
                } else {
                    subscriber.onCompleted();
                }
            }

            @Override
            public Action2<String, OutputStream> output() {
                return (s, out) -> {
                    try {
                        out.write(s.getBytes(Charsets.UTF_8));
                    } catch (final IOException e) {
                    }
                };
            }};
    }

    @Path("basic/testcookie2")
    @OnError({
        "org.jocean.restfuldemo.ctrl.ErrorHandler.handleException"
        ,"this.handleAllError"
        })
    public Object testcookie2(@CookieParam("hello") final String hello) {
        if (null == hello || hello.isEmpty()) {
            final MutableResponseBean resp = ResponseUtil.response();

            final Cookie cookie = new DefaultCookie("hello", UUID.randomUUID().toString());
            cookie.setPath("/");
            final String cookieStr = ServerCookieEncoder.STRICT.encode(cookie);

            resp.setStatus(200);
            resp.withHeader().setHeader(HttpHeaderNames.SET_COOKIE.toString(), cookieStr);

            return resp;
        } else {
            LOG.info("Cookie (hello) has set, value {}", hello);
            return "world";
        }
    }

    static class Response {
        @HeaderParam("set-cookie")
        String cookie;
    }

    @Path("basic/testcookie3")
    @OnError({
        "org.jocean.restfuldemo.ctrl.ErrorHandler.handleException"
        ,"this.handleAllError"
        })
    public Object testcookie3(@CookieParam("hello") final String hello) {
        if (null == hello || hello.isEmpty()) {
            final Response resp = new Response();

            final Cookie cookie = new DefaultCookie("hello", UUID.randomUUID().toString());
            cookie.setPath("/");
            resp.cookie = ServerCookieEncoder.STRICT.encode(cookie);

            return resp;
        } else {
            LOG.info("Cookie (hello) has set, value {}", hello);
            return "world";
        }
    }

    static class Response2 {
        @CookieParam("hello")
        String cookie;
    }

    @Path("basic/testcookie4")
    @OnError({
        "org.jocean.restfuldemo.ctrl.ErrorHandler.handleException"
        ,"this.handleAllError"
        })
    public Object testcookie4(@CookieParam("hello") final String hello) {
        if (null == hello || hello.isEmpty()) {
            final Response2 resp = new Response2();

            resp.cookie = UUID.randomUUID().toString();

            return resp;
        } else {
            LOG.info("Cookie (hello) has set, value {}", hello);
            return "world";
        }
    }

    static class Response3 {
        @CookieParam("cookie1")
        String cookie1;

        @CookieParam("cookie2")
        String cookie2;
    }

    @Path("basic/testcookie5")
    @OnError({
        "org.jocean.restfuldemo.ctrl.ErrorHandler.handleException"
        ,"this.handleAllError"
        })
    public Object testcookie5(
            @CookieParam("cookie1") final String cookie1,
            @CookieParam("cookie2") final String cookie2) {

        final Response3 resp = new Response3();

        if (null == cookie1 || cookie1.isEmpty()) {
            resp.cookie1 = UUID.randomUUID().toString();
        } else {
            LOG.info("Cookie (cookie1) has set, value {}", cookie1);
        }

        if (null == cookie2 || cookie2.isEmpty()) {
            resp.cookie2 = UUID.randomUUID().toString();
        } else {
            LOG.info("Cookie (cookie2) has set, value {}", cookie2);
        }

        return resp;
    }

    static class Response4 {
        @CookieParam("cookie1")
        String cookie1;

        @HeaderParam("set-cookie")
        String cookie2;
    }

    @Path("basic/testcookie6")
    @OnError({
        "org.jocean.restfuldemo.ctrl.ErrorHandler.handleException"
        ,"this.handleAllError"
        })
    public Object testcookie6(
            @CookieParam("cookie1") final String cookie1,
            @CookieParam("cookie2") final String cookie2) {

        final Response4 resp = new Response4();

        if (null == cookie1 || cookie1.isEmpty()) {
            resp.cookie1 = UUID.randomUUID().toString();
        } else {
            LOG.info("Cookie (cookie1) has set, value {}", cookie1);
        }

        if (null == cookie2 || cookie2.isEmpty()) {
            final Cookie cookie = new DefaultCookie("cookie2", UUID.randomUUID().toString());
            cookie.setPath("/");
            resp.cookie2 = ServerCookieEncoder.STRICT.encode(cookie);
        } else {
            LOG.info("Cookie (cookie2) has set, value {}", cookie2);
        }

        return resp;
    }

    // test with cmd:
    // wget -q -O - http://api.2gdt.com/newrest/basic/stream
    @Path("basic/stream")
    @OnError({
        "org.jocean.restfuldemo.ctrl.ErrorHandler.handleException"
        ,"this.handleAllError"
        })
    public Observable<String> teststream(final WriteCtrl ctrl) {
        ctrl.setFlushPerWrite(true);

        return Observable.unsafeCreate(subscriber -> {
            subscriber.onNext("hello,");

            Observable.timer(10, TimeUnit.SECONDS).subscribe(any -> {
                subscriber.onNext("world");
                Observable.timer(10, TimeUnit.SECONDS).subscribe(any2 -> {
                    subscriber.onNext("!");
                    subscriber.onCompleted();
                });
            });
        });
    }
}
