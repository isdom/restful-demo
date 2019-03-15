package org.jocean.restfuldemo.ctrl;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.inject.Inject;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.jocean.aliyun.BlobRepo;
import org.jocean.aliyun.ccs.CCSChatAPI;
import org.jocean.aliyun.ccs.CCSChatUtil;
import org.jocean.aliyun.ecs.MetadataAPI;
import org.jocean.aliyun.oss.BlobRepoOverOSS;
import org.jocean.http.ByteBufSlice;
import org.jocean.http.DoFlush;
import org.jocean.http.FullMessage;
import org.jocean.http.MessageBody;
import org.jocean.http.MessageUtil;
import org.jocean.http.RpcExecutor;
import org.jocean.http.RpcRunner;
import org.jocean.idiom.BeanFinder;
import org.jocean.idiom.DisposableWrapper;
import org.jocean.idiom.DisposableWrapperUtil;
import org.jocean.lbsyun.LbsyunUtil;
import org.jocean.redis.RedisClient;
import org.jocean.redis.RedisUtil;
import org.jocean.restfuldemo.bean.DemoRequest;
import org.jocean.svr.FinderUtil;
import org.jocean.svr.ResponseBean;
import org.jocean.svr.ResponseUtil;
import org.jocean.svr.TradeContext;
import org.jocean.svr.UntilRequestCompleted;
import org.jocean.svr.WithBody;
import org.jocean.svr.WithRawBody;
import org.jocean.svr.WithSlice;
import org.jocean.svr.ZipUtil.TozipEntity;
import org.jocean.svr.ZipUtil.ZipBuilder;
import org.jocean.wechat.AuthorizedMP;
import org.jocean.wechat.WXCommonAPI;
import org.jocean.wechat.WXCommonAPI.UploadTempMediaResponse;
import org.jocean.wechat.WechatAPI;
//import org.jocean.wechat.WechatAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import io.netty.buffer.ByteBuf;
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
public class DemoController {
    private static final Logger LOG = LoggerFactory.getLogger(DemoController.class);

    @Value("${wx.appid}")
    String _appid;

    @Path("wx-ccs-lite")
    @POST
    public Observable<? extends Object> wx_ccs_lite(
            @QueryParam("name") final String name,
            @QueryParam("filename") final String filename,
            @QueryParam("tntInstId") final String tntInstId,
            @QueryParam("scene") final String scene,
            @HeaderParam("content-type") final String contentType,
            final Observable<MessageBody> bodys,
            final RpcExecutor executor,
            final BeanFinder finder) {

        final AtomicReference<Mac> macRef = new AtomicReference<>();
        final AtomicReference<String> mediaIdRef = new AtomicReference<>();

        return executor.execute( uploadMediaToWX(finder, this._appid, name, filename, bodys) )
            .doOnNext(resp -> LOG.info("upload temp media: {}", resp.getMediaId()))
            .doOnNext(resp -> mediaIdRef.set(resp.getMediaId()))
            .flatMap(resp -> executor.execute( getMediaFromWX(finder, this._appid, mediaIdRef.get()) ))
            .doOnNext(body -> LOG.info("get temp media for digest: {} / {}", body.contentType(), body.contentLength()))
            .flatMap(body -> finder.find(CCSChatAPI.class).doOnNext(ccs -> macRef.set(ccs.digestInstance())).map(ccs -> body))
            .flatMap(body -> digestBody(body, macRef.get()))
            .flatMap(last -> executor.execute( getMediaFromWX(finder, this._appid, mediaIdRef.get()) ))
            .doOnNext(body -> LOG.info("get temp media and upload to ccs: {} / {}", body.contentType(), body.contentLength()))
            .flatMap(body -> executor.execute(finder.find(CCSChatAPI.class)
                    .map(ccs -> ccs.uploadFile(tntInstId, scene, System.currentTimeMillis(), "image", filename,
                            Observable.just(body), macRef.get()))))
            .doOnNext(resp -> LOG.info("upload to ccs: {}", resp))
            .flatMap(resp -> executor.execute(finder.find(CCSChatAPI.class)
                    .map(ccs -> ccs.fetchFile(tntInstId, scene, System.currentTimeMillis(), resp.getFileKey()))))
            .map(body -> new MessageBody() {
                @Override
                public String contentType() {
                    return null != contentType ? contentType : body.contentType();
                }
                @Override
                public int contentLength() {
                    return body.contentLength();
                }
                @Override
                public Observable<? extends ByteBufSlice> content() {
                    return body.content();
                }})
        ;
    }

    private Observable<? extends ByteBufSlice> digestBody(final MessageBody body, final Mac digest) {
        return body.content().doOnNext(bbs -> CCSChatUtil.updateDigest(digest, bbs.element()))
                .doOnNext(bbs -> {
                    for (final DisposableWrapper<? extends ByteBuf> dwb : bbs.element()) {
                        dwb.dispose();
                    }
                    bbs.step();
                }).last();
    }

    @Path("wx-ccs")
    @POST
    public Observable<? extends Object> wx_ccs(
            @QueryParam("name") final String name,
            @QueryParam("filename") final String filename,
            @QueryParam("tntInstId") final String tntInstId,
            @QueryParam("scene") final String scene,
            final Observable<MessageBody> bodys,
            final RpcExecutor executor,
            final BeanFinder finder) {

        final AtomicReference<Mac> macRef = new AtomicReference<>();
        final AtomicInteger bodySize = new AtomicInteger(0);

        return executor.execute( uploadMediaToWX(finder, this._appid, name, filename, bodys) )
            .doOnNext(resp -> LOG.info("upload temp media: {}", resp.getMediaId()))
            .flatMap(resp -> executor.execute( getMediaFromWX(finder, this._appid, resp.getMediaId()) ))
            .doOnNext(body -> LOG.info("get temp media: {} / {}", body.contentType(), body.contentLength()))
            .flatMap(body -> finder.find(CCSChatAPI.class).map(ccs -> {
                macRef.set( ccs.digestInstance() );

                final Observable<ByteBufSlice> content4digest = body.content().map(bbs -> {
                    final Iterable<DisposableWrapper<? extends ByteBuf>> slice = slice(bbs.element());

                    bodySize.addAndGet(CCSChatUtil.updateDigest(macRef.get(), bbs.element()).length);

                    return (ByteBufSlice)new ByteBufSlice() {
                        @Override
                        public void step() {
                            bbs.step();
                        }
                        @Override
                        public Iterable<? extends DisposableWrapper<? extends ByteBuf>> element() {
                            return slice;
                        }};
                });
                return (MessageBody)new MessageBody() {
                    @Override
                    public String contentType() {
                        return body.contentType();
                    }

                    @Override
                    public int contentLength() {
                        return body.contentLength();
                    }
                    @Override
                    public Observable<? extends ByteBufSlice> content() {
                        return content4digest;
                    }};
            }))
            .flatMap(body -> executor.execute(finder.find(BlobRepo.class)
                    .map(repo -> repo.putObject().objectName(name).content(body).build())))
            .doOnNext(putresult -> LOG.info("upload to oss: {}", putresult))
            .flatMap(putresult -> executor.execute(finder.find(BlobRepo.class)
                    .map(repo -> repo.getObject(putresult.objectName()))))
            .doOnNext(body -> LOG.info("get from oss: {} / {}", body.contentType(), body.contentLength()))
            .flatMap(body -> executor.execute(finder.find(CCSChatAPI.class)
                    .map(ccs -> ccs.uploadFile(tntInstId, scene, System.currentTimeMillis(), "image", filename,
                            Observable.just(body), macRef.get()))))
            .doOnNext(resp -> LOG.info("upload to ccs: {} and bodySize is: {}", resp, bodySize.get()))
        ;
    }

    private static Iterable<DisposableWrapper<? extends ByteBuf>> slice(
            final Iterable<? extends DisposableWrapper<? extends ByteBuf>> element) {
        final List<DisposableWrapper<? extends ByteBuf>> duplicated = new ArrayList<>();
        for (final DisposableWrapper<? extends ByteBuf> dwb : element) {
            duplicated.add(DisposableWrapperUtil.wrap(dwb.unwrap().slice(), dwb));
        }
        return duplicated;
    }

    private Observable<Transformer<RpcRunner, UploadTempMediaResponse>> uploadMediaToWX(final BeanFinder finder,
            final String appid, final String name, final String filename, final Observable<MessageBody> bodys) {
        return Observable.zip(finder.find(appid, AuthorizedMP.class), finder.find(WXCommonAPI.class),
                (mp, wcapi)-> wcapi.uploadTempMedia(mp.getAccessToken(), name, filename, bodys));
    }

    private Observable<Transformer<RpcRunner, MessageBody>> getMediaFromWX(final BeanFinder finder,
            final String appid, final String mediaId) {
        return Observable.zip(finder.find(appid, AuthorizedMP.class), finder.find(WXCommonAPI.class),
                (mp, wcapi)-> wcapi.getTempMedia(mp.getAccessToken(), mediaId));
    }

    @Path("wxmedia")
    @POST
    public Observable<Object> wxmedia(
            @QueryParam("name") final String name,
            @QueryParam("filename") final String filename,
            final Observable<MessageBody> getbody,
            final RpcExecutor executor,
            final BeanFinder finder) {

        return executor.execute( finder.find(_appid, AuthorizedMP.class).flatMap(mp -> finder.find(WXCommonAPI.class)
                .map(wcapi -> wcapi.uploadTempMedia(mp.getAccessToken(), name, filename, getbody))))
            .doOnNext(resp -> LOG.info("upload temp media: {}", resp.getMediaId()))
            .flatMap(resp -> executor.execute(finder.find(_appid, AuthorizedMP.class).flatMap(mp -> finder.find(WXCommonAPI.class)
                    .map(wcapi -> wcapi.getTempMedia(mp.getAccessToken(), resp.getMediaId())))))
        ;
    }

    @Path("private-ipv4")
    public Observable<String> private_ipv4(final RpcExecutor executor,
            final BeanFinder finder,
            final UntilRequestCompleted<String> urc) {
        return executor.execute(finder.find(MetadataAPI.class).map(api -> api.getPrivateIpv4())).compose(urc);
    }

    @Path("hostname")
    public Observable<String> hostname(final RpcExecutor executor,
            final BeanFinder finder,
            final UntilRequestCompleted<String> urc) {
        return executor.execute(finder.find(MetadataAPI.class).map(api -> api.getHostname())).compose(urc);
    }

    @Path("instance")
    public Observable<String> instance(final RpcExecutor executor,
            final BeanFinder finder,
            final UntilRequestCompleted<String> urc) {
        return executor.execute(finder.find(MetadataAPI.class).map(api -> api.getInstanceId())).compose(urc);
    }

    @Path("region")
    public Observable<String> region(final RpcExecutor executor,
            final BeanFinder finder,
            final UntilRequestCompleted<String> urc) {
        return executor.execute(finder.find(MetadataAPI.class).map(api -> api.getRegionId())).compose(urc);
    }

    @Path("sts-token")
    public Observable<Object> ststoken(final RpcExecutor executor,
            final BeanFinder finder,
            @QueryParam("role") final String roleName,
            final UntilRequestCompleted<Object> urc) {
        return executor.execute(finder.find(MetadataAPI.class).map(api -> api.getSTSToken(roleName))).compose(urc);
    }

    @Path("echo")
    public Observable<String> echo(@QueryParam("s") final String s, @QueryParam("delay") final int delay, final UntilRequestCompleted<String> urc) {
        return Observable.just(s).delay(delay, TimeUnit.MILLISECONDS).compose(urc);
    }

    @Path("listobj")
    public Observable<String> list( @QueryParam("prefix") final String prefix,
            final RpcExecutor executor, final BeanFinder finder) {
        return executor.execute(finder.find(BlobRepoOverOSS.class).map(repo -> repo.listObjects(prefix)))
                .map(listing -> listing.toString());
    }

    @Path("redirect")
    public ResponseBean redirect() {
        return ResponseUtil.redirectOnly("http://www.baidu.com");
    }

    @Path("download")
    public WithBody download(@QueryParam("key") final String key, final RpcExecutor executor, final BeanFinder finder) {
        return new WithRawBody() {
            @Override
            public Observable<? extends MessageBody> body() {
                return executor.execute(_repo.getObject(key));
            }};
    }

    @Path("ipv2")
    public Observable<Object>  getCityByIpV2(@QueryParam("ip") final String ip,
            final RpcExecutor executor,
            final BeanFinder finder) {
        return executor.execute(LbsyunUtil.ip2position(finder, ip))
                .map(resp -> ResponseUtil.responseAsJson(200, resp));
    }

    @SuppressWarnings("unchecked")
    @Path("helloredis")
    public Observable<Object> helloredis(final BeanFinder finder) {
        return finder.find(RedisClient.class)
                .flatMap(redis->redis.getConnection())
                .compose(RedisUtil.interacts(
                        RedisUtil.cmdSet("demo_key", "new hello, redis").nx().build(),
                        RedisUtil.ifOKThenElse(
                            RedisUtil.cmdGet("demo_key"),
                            RedisUtil.error("set failed.")
                            ),
                        resp->RedisUtil.cmdDel("demo_key")
                        ))
                .map(resp->resp.toString());
    }

    @SuppressWarnings("unchecked")
    @Path("redis_get")
    public Observable<Object> redisGet(final BeanFinder finder, @QueryParam("key") final String key) {
        return finder.find(RedisClient.class)
                .flatMap(redis->redis.getConnection())
                .compose(RedisUtil.interacts(RedisUtil.cmdGet(key)))
                .map(resp-> RedisUtil.isNull(resp) ? "key(" + key + ") not exist" : RedisUtil.dumpAggregatedRedisMessage(resp));
    }

    @Path("qrcode/{wpa}")
    public Observable<Object> qrcode(@PathParam("wpa") final String wpa, final RpcExecutor executor, final BeanFinder finder) {
        return executor.execute(finder.find(wpa, WechatAPI.class).map(api-> api.createVolatileQrcode(2592000, "ABC")))
                .map(location->ResponseUtil.redirectOnly(location));
    }

    @Path("metaof/{obj}")
    public Observable<String> getSimplifiedObjectMeta(@PathParam("obj") final String objname, final RpcExecutor executor,
            final BeanFinder finder) {
        return executor.execute(_repo.getSimplifiedObjectMeta(objname))
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
    public Observable<Object> asjson(final Observable<MessageBody> omb, final UntilRequestCompleted<Object> urc) {
        return omb.flatMap(body -> MessageUtil.<DemoRequest>decodeJsonAs(body, DemoRequest.class))
                .map(req -> ResponseUtil.responseAsJson(200, req)).compose(urc);
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
            final ZipBuilder zb,
            final BeanFinder finder) {

        tctx.writeCtrl().sended().subscribe(msg -> DisposableWrapperUtil.dispose(msg));

        final AtomicInteger unzipedSize = new AtomicInteger(0);

        tctx.endable().doOnEnd(() -> LOG.info("total unziped size is: {}", unzipedSize.get()));

        final Observable<RpcRunner> rpcs = FinderUtil.rpc(finder).ib(tctx.interactBuilder()).runner();

        return new BinaryResponse("1.zip") {
            @Override
            public String contentType() {
                return HttpHeaderValues.APPLICATION_OCTET_STREAM.toString();
            }

            @Override
            public Observable<? extends ByteBufSlice> slices() {
                return rpcs.compose(fetch(uri)).flatMap(fullmsg -> fullmsg.body()).<TozipEntity>map(body -> new TozipEntity() {
                    @Override
                    public String entryName() {
                        return "123.txt";
                    }
                    @Override
                    public Observable<? extends ByteBufSlice> body() {
                        return body.content().doOnNext( bbs -> {
                            LOG.debug("=========== source slice: {}", bbs);
//                            final List<? extends DisposableWrapper<? extends ByteBuf>> dwbs = Observable.from(bbs.element()).toList().toBlocking().single();
//                            LOG.debug("=========== source to zip begin");
//                            for (final DisposableWrapper<? extends ByteBuf> dwb : dwbs) {
//                                LOG.debug("source to zip:\r\n{}", ByteBufUtil.prettyHexDump(dwb.unwrap()));
//                            }
//                            LOG.debug("=========== source to zip end");
                        });
                    }})
                .compose(zb.zip(8192,512))
                .doOnNext( bbs -> {
                    LOG.debug("=========== zipped slice: {}", bbs);
//                    final List<? extends DisposableWrapper<? extends ByteBuf>> dwbs = Observable.from(bbs.element()).toList().toBlocking().single();
//                    LOG.debug("------------ zipped begin");
//                    for (final DisposableWrapper<? extends ByteBuf> dwb : dwbs) {
//                        LOG.debug("zipped:\r\n{}", ByteBufUtil.prettyHexDump(dwb.unwrap()));
//                    }
//                    LOG.debug("------------ zipped end");
                })
                .compose(zb.unzip(8192, 512))
                .flatMap(entity -> {
                    LOG.debug("=========== unzip zip entity: {}", entity.entry());
                    return entity.body();
                })
                .doOnNext( bbs -> {
                    LOG.debug("=========== unzipped slice: {}", bbs);
                    final List<? extends DisposableWrapper<? extends ByteBuf>> dwbs = Observable.from(bbs.element()).toList().toBlocking().single();
                    for (final DisposableWrapper<? extends ByteBuf> dwb : dwbs) {
                        unzipedSize.addAndGet( dwb.unwrap().readableBytes() );
                    }
                });
            }
        };
    }

    private Transformer<RpcRunner, FullMessage<HttpResponse>> fetch(final String uri) {
        return runners -> runners.flatMap(runner -> runner.execute(interact -> interact.uri(uri).path("/").response()));
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
            final Observable<MessageBody> getbody,
            final RpcExecutor executor,
            final BeanFinder finder) {

        final AtomicInteger idx = new AtomicInteger(0);

        final Observable<Object> prefix = handle100Continue(request);
        return prefix.concatWith(getbody.flatMap(body -> {
            LOG.debug(idx.get() + ": MessageBody {}", body);
            if (body.contentType().startsWith(HttpHeaderValues.APPLICATION_JSON.toString())) {
                return MessageUtil.decodeJsonAs(body, DemoRequest.class).map(req -> req.toString());
            } else {
                return executor.execute(finder.find(BlobRepoOverOSS.class).map(repo ->
                    repo.putObject().content(body).objectName(Integer.toString(idx.get())).build()))
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
    RpcExecutor _executor;
}
