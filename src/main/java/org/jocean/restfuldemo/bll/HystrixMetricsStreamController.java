package org.jocean.restfuldemo.bll;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.core.HttpHeaders;

import org.jocean.http.DoFlush;
import org.jocean.svr.WithStatus;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.netflix.hystrix.metric.consumer.HystrixDashboardStream;
import com.netflix.hystrix.serial.SerialHystrixDashboardData;

import rx.Observable;

@Controller
@Scope("singleton")
public class HystrixMetricsStreamController {

    static class StreamResponse implements WithStatus {

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
    }

    private Observable<Object> hystrixDashboard() {
        return Observable.<Object>just(new StreamResponse())
                .concatWith(HystrixDashboardStream.getInstance().observe()
                        .concatMap(dashboardData -> Observable
                                .from(SerialHystrixDashboardData.toMultipleJsonStrings(dashboardData)))
                        .flatMap(str -> {
                            return Observable.just(
                                new StringBuilder().append("data: ").append(str).append("\n\n").toString(),
                                DoFlush.Util.flushOnly());
                        }));
    }
}
