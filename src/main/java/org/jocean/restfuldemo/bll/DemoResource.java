package org.jocean.restfuldemo.bll;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.jocean.aliyun.oss.BlobRepoOverOSS;
import org.jocean.http.ByteBufSlice;
import org.jocean.http.DoFlush;
import org.jocean.http.FullMessage;
import org.jocean.http.InteractBuilder;
import org.jocean.http.MessageBody;
import org.jocean.http.MessageUtil;
import org.jocean.http.RpcRunner;
import org.jocean.idiom.BeanFinder;
import org.jocean.idiom.DisposableWrapper;
import org.jocean.idiom.DisposableWrapperUtil;
import org.jocean.lbsyun.LbsyunAPI;
import org.jocean.redis.RedisClient;
import org.jocean.redis.RedisUtil;
import org.jocean.restfuldemo.bean.DemoRequest;
import org.jocean.svr.FinderUtil;
import org.jocean.svr.ResponseUtil;
import org.jocean.svr.TradeContext;
import org.jocean.svr.UntilRequestCompleted;
import org.jocean.svr.WithBody;
import org.jocean.svr.WithRawBody;
import org.jocean.svr.WithSlice;
import org.jocean.svr.ZipUtil.TozipEntity;
import org.jocean.svr.ZipUtil.ZipBuilder;
import org.jocean.wechat.WechatAPI;
//import org.jocean.wechat.WechatAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixObservableCommand;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import rx.Observable;
import rx.Observable.Transformer;

@Path("/newrest/")
@Controller
@Scope("singleton")
public class DemoResource {
    private static final Logger LOG
        = LoggerFactory.getLogger(DemoResource.class);

    @Path("download")
    public WithBody download(@QueryParam("key") final String key, final InteractBuilder ib) {
        final Observable<RpcRunner> rpcs = FinderUtil.rpc(this._finder).ib(ib).runner();

        return new WithRawBody() {
            @Override
            public Observable<? extends MessageBody> body() {
                return rpcs.compose(_repo.getObject(key));
            }};
    }

    @Path("ipv2")
    public Observable<Object>  getCityByIpV2(
            @QueryParam("ip") final String ip,
            final InteractBuilder ib) {
        final Observable<RpcRunner> rpcs = FinderUtil.rpc(this._finder).ib(ib).runner();
        return _finder.find(LbsyunAPI.class).flatMap(
                api -> {
                    return new HystrixObservableCommand<LbsyunAPI.PositionResponse>(HystrixObservableCommand.Setter
                            .withGroupKey(HystrixCommandGroupKey.Factory.asKey("GetCityByIpV2"))
                            .andCommandKey(HystrixCommandKey.Factory.asKey("GetCityByIpV2"))) {
                        @Override
                        protected Observable<LbsyunAPI.PositionResponse> construct() {
                            return rpcs.compose(api.ip2position(ip, LbsyunAPI.COOR_GCJ02));
                        }
                    }.toObservable();
                })
                .map(resp -> ResponseUtil.responseAsJson(200, resp));
    }

    @SuppressWarnings("unchecked")
    @Path("helloredis")
    public Observable<Object> helloredis() {
        return this._finder.find(RedisClient.class)
                .flatMap(redis->redis.getConnection())
                .compose(RedisUtil.interactWithRedis(
                        RedisUtil.cmdSet("demo_key", "new hello, redis").nx().build(),
                        RedisUtil.ifOKThenElse(
                            RedisUtil.cmdGet("demo_key"),
                            RedisUtil.error("set failed.")
                            ),
                        resp->RedisUtil.cmdDel("demo_key")
                        ))
                .map(resp->resp.toString());
    }

    @Path("qrcode/{wpa}")
    public Observable<Object> qrcode(@PathParam("wpa") final String wpa, final InteractBuilder ib) {
        final Observable<RpcRunner> rpcs = FinderUtil.rpc(this._finder).ib(ib).runner();

        return this._finder.find(wpa, WechatAPI.class)
                .flatMap(api-> rpcs.compose(api.createVolatileQrcode(2592000, "ABC")))
                .map(location->ResponseUtil.redirectOnly(location));
    }

    @Path("metaof/{obj}")
    public Observable<String> getSimplifiedObjectMeta(@PathParam("obj") final String objname, final InteractBuilder ib) {
        final Observable<RpcRunner> rpcs = FinderUtil.rpc(this._finder).ib(ib).runner();

        return rpcs.compose(_repo.getSimplifiedObjectMeta(objname))
            .map(meta -> {
                LOG.info("meta:{}", meta);
                if (null != meta.getLastModified()) {
                    final Instant last = meta.getLastModified().toInstant();
                    final Instant lastDay = last.truncatedTo(ChronoUnit.DAYS);
                    final Instant now = Instant.now();
                    final Instant nowDay = now.truncatedTo(ChronoUnit.DAYS);
                    final Duration duration = Duration.between(lastDay, nowDay);
                    return "last:" + last
                        +" \nlastDay:" + lastDay
                        + "\nnow:" + now
                        + "\nnowDay:" + nowDay
                        + "\nDuration in days:" + duration.toDays();
                } else {
                    return "Not exist";
                }
            });
    }

    @Path("from/{begin}/to/{end}")
    public Observable<String> pathparam(@PathParam("begin") final String begin, @PathParam("end") final String end,
            final Observable<MessageBody> omb) {
        LOG.info("from {} to {}", begin, end);
        return omb.flatMap(body -> MessageUtil.<String>decodeContentAs(body.content(),
                (buf, cls) -> MessageUtil.parseContentAsString(buf), String.class));
    }

    @Path("hello")
    @OPTIONS
    @POST
    public Observable<Object> hello(
            final HttpMethod method,
            final UntilRequestCompleted<Object> urc) {
        return Observable.just(ResponseUtil.redirectOnly("http://baidu.com/world"))
                .compose(urc)
                ;
    }

    @Path("hi")
    public Observable<String> hiAsString(
            @QueryParam("name") final String name,
            @HeaderParam("User-Agent") final String ua,
            final UntilRequestCompleted<String> urc) {
        return Observable.just("hi, ", name, "'s ", ua).compose(urc);
    }

    static class Formed {

        @QueryParam("name")
        public String name;

        @QueryParam("sex")
        public String sex;

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append("Formed [name=").append(name).append(", sex=").append(sex).append("]");
            return builder.toString();
        }
    }

    @Path("wwwform")
    public Observable<String> wwwform(final Observable<MessageBody> omb) {
        return omb.flatMap(body -> MessageUtil.decodeContentAs(body.content(),
                        MessageUtil::unserializeAsX_WWW_FORM_URLENCODED, Formed.class))
                .map(formed -> formed.toString());
    }

    @Path("null")
    public Observable<String> returnNull(final Observable<HttpObject> req) {
        return null;
    }

    @Path("asjson")
    public Observable<Object> asjson(final Observable<MessageBody> omb) {
        return omb.flatMap(body -> MessageUtil.<DemoRequest>decodeJsonAs(body, DemoRequest.class))
                .map(req -> ResponseUtil.responseAsJson(200, req));
    }

    static abstract class BinaryResponse implements WithSlice {

        public BinaryResponse(final String filename) {
            this._contentDisposition = "attachment; filename=" + filename;
        }

        @HeaderParam("content-disposition")
        private final String _contentDisposition;

    }

    @Path("proxy")
    public BinaryResponse proxy(
            @QueryParam("uri") final String uri,
            final TradeContext tctx,
            final ZipBuilder zb) {

        tctx.writeCtrl().sended().subscribe(msg -> DisposableWrapperUtil.dispose(msg));

        final AtomicInteger unzipedSize = new AtomicInteger(0);

        tctx.terminable().doOnTerminate(() -> LOG.info("total unziped size is: {}", unzipedSize.get()));

        final Observable<RpcRunner> rpcs = FinderUtil.rpc(this._finder).ib(tctx.interactBuilder()).runner();

        return new BinaryResponse("1.zip") {
            @Override
            public String contentType() {
                return HttpHeaderValues.APPLICATION_OCTET_STREAM.toString();
            }

            @Override
            public Observable<ByteBufSlice> slices() {
                return (Observable<ByteBufSlice>) rpcs.compose(fetch(uri)).flatMap(fullmsg -> fullmsg.body()).<TozipEntity>map(body -> new TozipEntity() {
                    @Override
                    public String entryName() {
                        return "123.txt";
                    }
                    @Override
                    public Observable<? extends ByteBufSlice> body() {
                        return body.content().doOnNext( bbs -> {
                            LOG.debug("=========== source slice: {}", bbs);
                            final List<? extends DisposableWrapper<? extends ByteBuf>> dwbs = bbs.element().toList().toBlocking().single();
                            LOG.debug("=========== source to zip begin");
                            for (final DisposableWrapper<? extends ByteBuf> dwb : dwbs) {
                                LOG.debug("source to zip:\r\n{}", ByteBufUtil.prettyHexDump(dwb.unwrap()));
                            }
                            LOG.debug("=========== source to zip end");
                        });
                    }})
                .compose(zb.zip(8192,512))
                .doOnNext( bbs -> {
                    LOG.debug("=========== zipped slice: {}", bbs);
                    final List<? extends DisposableWrapper<? extends ByteBuf>> dwbs = bbs.element().toList().toBlocking().single();
                    LOG.debug("------------ zipped begin");
                    for (final DisposableWrapper<? extends ByteBuf> dwb : dwbs) {
                        LOG.debug("zipped:\r\n{}", ByteBufUtil.prettyHexDump(dwb.unwrap()));
                    }
                    LOG.debug("------------ zipped end");
                })
                .compose(zb.unzip(8192, 512))
                .flatMap(entity -> {
                    LOG.debug("=========== unzip zip entity: {}", entity.entry());
                    return entity.body();
                })
                .doOnNext( bbs -> {
                    LOG.debug("=========== unzipped slice: {}", bbs);
                    final List<? extends DisposableWrapper<? extends ByteBuf>> dwbs = bbs.element().toList().toBlocking().single();
                    for (final DisposableWrapper<? extends ByteBuf> dwb : dwbs) {
                        unzipedSize.addAndGet( dwb.unwrap().readableBytes() );
                    }
                });
            }
        };
    }

    private Transformer<RpcRunner, FullMessage<HttpResponse>> fetch(final String uri) {
        return rpcs -> rpcs.flatMap(rpc -> rpc.execute(interact -> interact.uri(uri).path("/").execution())
                .flatMap(interaction -> interaction.execute()));
    }

    public HttpResponse resp(final String filename) {
        final HttpResponse resp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        HttpUtil.setTransferEncodingChunked(resp, true);
        resp.headers().set(HttpHeaderNames.CONTENT_DISPOSITION, "attachment; filename=" + filename);
        return resp;
    }

    @Path("foo")
    public Observable<String> foo(
            @QueryParam("name") final String name,
            @HeaderParam("user-agent") final String ua,
            @HeaderParam("x-forwarded-for") final String peerip) {
        return Observable.just("hi, ", name, "'s ", ua, ",from:", peerip);
    }

    @Path("upload")
    @POST
    public Observable<Object> upload(
            final HttpRequest request,
            final Observable<MessageBody> omb,
            final InteractBuilder ib) {

        final AtomicInteger idx = new AtomicInteger(0);
        final Observable<RpcRunner> rpcs = FinderUtil.rpc(this._finder).ib(ib).runner();

        final Observable<Object> prefix = handle100Continue(request);
        return prefix.concatWith(omb.flatMap(body -> {
            LOG.debug(idx.get() + ": MessageBody {}", body);
            if (body.contentType().startsWith(HttpHeaderValues.APPLICATION_JSON.toString())) {
                return MessageUtil.decodeJsonAs(body, DemoRequest.class).map(req -> req.toString());
            } else {
                return rpcs.compose(_repo.putObject().content(body).objectName(Integer.toString(idx.get())).build())
                    .map(key-> ResponseUtil.responseAsText(200,
                            "\r\n["
                        + idx.getAndIncrement()
                        + "] upload:" + body.contentType()
                        + " and saved as key("
                        + key + ")"));
            }
        }));
    }

    private Observable<Object> handle100Continue(final HttpRequest request) {
        return HttpUtil.is100ContinueExpected(request)
            ? Observable.<Object>just(ResponseUtil.response().setStatus(100), DoFlush.Util.flushOnly())
            : Observable.empty();
    }

    @Inject
    private BlobRepoOverOSS _repo;

    @Inject
    private BeanFinder _finder;
}
