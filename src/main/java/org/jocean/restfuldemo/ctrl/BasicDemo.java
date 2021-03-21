package org.jocean.restfuldemo.ctrl;

import java.util.UUID;

import javax.ws.rs.CookieParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;

import org.jocean.http.WriteCtrl;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.svr.MutableResponseBean;
import org.jocean.svr.ResponseUtil;
import org.jocean.svr.annotation.HandleError;
import org.jocean.svr.annotation.OnError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;


@Path("/newrest/")
@Controller
@Scope("prototype")
public class BasicDemo {
    private static final Logger LOG = LoggerFactory.getLogger(BasicDemo.class);

    @HandleError(Exception.class)
    String handleAllError(final HttpRequest req, final Exception e) {
        LOG.warn("error when {}, detail: {}", req.uri(), e);
        return "error when " + req.uri() + "{\n" + ExceptionUtils.exception2detail(e) + "\n}";
    }

    @Path("basic/testcookie1")
    @OnError({
        "org.jocean.restfuldemo.ctrl.ErrorHandler.handleException"
        ,"this.handleAllError"
        })
    public Object testcookie1(@CookieParam("hello") final String hello,
            final WriteCtrl writeCtrl) {
        if (null == hello || hello.isEmpty()) {
            writeCtrl.sending().subscribe( obj -> {
                if (obj instanceof HttpResponse) {
                    final HttpResponse response = (HttpResponse)obj;
                    final Cookie cookie = new DefaultCookie("hello", UUID.randomUUID().toString());
                    cookie.setPath("/");
                    final String cookieStr = ServerCookieEncoder.STRICT.encode(cookie);
                    response.headers().set(HttpHeaderNames.SET_COOKIE, cookieStr);
                }
            });
        } else {
            LOG.info("Cookie (hello) has set, value {}", hello);
        }
        return "world";
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
        }

        if (null == cookie2 || cookie2.isEmpty()) {
            resp.cookie2 = UUID.randomUUID().toString();
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
        }

        if (null == cookie2 || cookie2.isEmpty()) {
            final Cookie cookie = new DefaultCookie("cookie2", UUID.randomUUID().toString());
            cookie.setPath("/");
            resp.cookie2 = ServerCookieEncoder.STRICT.encode(cookie);
        }
        return resp;
    }
}
