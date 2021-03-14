package org.jocean.restfuldemo.ctrl;

import java.util.UUID;

import javax.ws.rs.CookieParam;
import javax.ws.rs.Path;

import org.jocean.http.WriteCtrl;
import org.jocean.idiom.ExceptionUtils;
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

    @Path("basic/testcookie")
    @OnError({
//        "org.jocean.restfuldemo.ctrl.OssDemo.handleOssException"
//        ,"org.jocean.restfuldemo.ctrl.OssDemo.handleAllError"
        "org.jocean.restfuldemo.ctrl.ErrorHandler.handleOssException"
        ,"this.handleAllError"
        })
    public Object testcookie(@CookieParam("hello") final String hello,
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

}
