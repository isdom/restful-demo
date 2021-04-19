package org.jocean.restfuldemo.ctrl;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.Path;

import org.jocean.idiom.ExceptionUtils;
import org.jocean.svr.WithSubscriber;
import org.jocean.svr.annotation.HandleError;
import org.jocean.svr.annotation.OnError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.google.common.base.Charsets;

import io.netty.handler.codec.http.HttpRequest;
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

    @Path("stream/endless")
    @OnError({
        "org.jocean.restfuldemo.ctrl.ErrorHandler.handleException"
        ,"this.handleAllError"
        })
    public WithSubscriber<String> endless() {
        final AtomicInteger cnt = new AtomicInteger(0);
        return new WithSubscriber<String>() {

            @Override
            public String contentType() {
                return "text/plain";
            }

            @Override
            public void onSubscriber(final Subscriber<String> subscriber) {
                Observable.timer(1, TimeUnit.SECONDS).subscribe(any ->  subscriber.onNext(cnt.addAndGet(1) + ", hello\n"));
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

}
