package org.jocean.restfuldemo.bll;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.core.HttpHeaders;

import org.jocean.http.DoFlush;
import org.jocean.svr.MessageResponse;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.netflix.hystrix.metric.consumer.HystrixDashboardStream;
import com.netflix.hystrix.serial.SerialHystrixDashboardData;

import io.netty.handler.codec.http.LastHttpContent;
import rx.Observable;

@Controller
@Scope("singleton")
public class HystrixMetricsStreamController {

    static class StreamResponse implements MessageResponse {

        @Override
        public int status() {
            return 200;
        }

        @HeaderParam(HttpHeaders.CONTENT_TYPE)
        private final String contentType = "text/event-stream;charset=UTF-8";

        @HeaderParam(HttpHeaders.CACHE_CONTROL)
        private final String cacheControl = "no-cache, no-store, max-age=0, must-revalidate";

        @HeaderParam("Pragma")
        private final String Pragma = "no-cache";
    }

    @Path("/hystrix.stream")
    @GET
    public Observable<Object> getStream() {
        return hystrixDashboard();
//        return Observable.switchOnNext(Observable.just(hystrixDashboard(),
//                Observable.timer(1, TimeUnit.MINUTES).ignoreElements()));
    }

    private Observable<Object> hystrixDashboard() {
        final long begin = System.currentTimeMillis();
        return Observable.<Object>just(new StreamResponse())
                .concatWith(HystrixDashboardStream.getInstance().observe()
                        .concatMap(dashboardData -> Observable
                                .from(SerialHystrixDashboardData.toMultipleJsonStrings(dashboardData)))
                        .flatMap(str -> {
                            if (System.currentTimeMillis()  - begin < 10 * 1000) {
                                return Observable.just(
                                    new StringBuilder().append("data: ").append(str).append("\n\n").toString(),
                                    DoFlush.Util.flushOnly());
                            } else {
                                return Observable.error(new RuntimeException());
                            }
                        }))
                .onErrorResumeNext(Observable.just(LastHttpContent.EMPTY_LAST_CONTENT))
                ;
    }
}
