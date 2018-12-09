package org.jocean.restfuldemo.other;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

import org.jocean.http.WriteCtrl;
import org.jocean.idiom.DisposableWrapperUtil;
import org.jocean.idiom.Stepable;
import org.jocean.svr.WithStepable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.netflix.hystrix.metric.consumer.HystrixDashboardStream;
import com.netflix.hystrix.serial.SerialHystrixDashboardData;

import io.netty.util.CharsetUtil;
import rx.Observable;
import rx.functions.Action2;

@Controller
@Scope("singleton")
public class HystrixMetricsStreamController {

    private static final Logger LOG = LoggerFactory.getLogger(HystrixMetricsStreamController.class);

    void start() {
        this._workers = Executors.newFixedThreadPool(1);
    }

    void stop() {
        this._workers.shutdown();
    }

    @Path("/hystrix.stream")
    @GET
    public WithStepable<Stepable<String>> getStream(final WriteCtrl writeCtrl) {

        writeCtrl.sended().subscribe(obj -> DisposableWrapperUtil.dispose(obj));

        return new WithStepable<Stepable<String>>() {
            @Override
            public String contentType() {
                return "text/event-stream;charset=UTF-8";
            }

            @Override
            public Observable<Stepable<String>> stepables() {
                return HystrixDashboardStream.getInstance().observe()
                        .concatMap(dashboardData -> Observable
                                .from(SerialHystrixDashboardData.toMultipleJsonStrings(dashboardData)))
                        .map(str -> new Stepable<String>() {
                            @Override
                            public void step() {
                                LOG.info("call Stepable<String>'s step()");
                            }

                            @Override
                            public String element() {
                                return new StringBuilder().append("data: ").append(str).append("\n\n").toString();
                            }
                        });
            }

            @Override
            public Action2<Stepable<String>, OutputStream> output() {
                return (ss, out) -> {
                    try {
                        out.write(ss.element().getBytes(CharsetUtil.UTF_8));
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    }
                };
            }
        };
    }

    private ExecutorService _workers;
}
