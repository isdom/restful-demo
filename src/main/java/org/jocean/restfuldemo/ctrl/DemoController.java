package org.jocean.restfuldemo.ctrl;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.jocean.aliyun.BlobRepo;
import org.jocean.aliyun.ccs.CCSChatAPI;
import org.jocean.aliyun.ccs.CCSChatUtil;
import org.jocean.aliyun.ecs.EcsAPI;
import org.jocean.aliyun.ecs.EcsAPI.DescribeInstanceRamRoleBuilder;
import org.jocean.aliyun.ecs.EcsAPI.DescribeInstanceStatusBuilder;
import org.jocean.aliyun.ecs.MetadataAPI;
import org.jocean.aliyun.ivision.IvisionAPI;
import org.jocean.aliyun.nls.NlsAPI;
import org.jocean.aliyun.nls.NlsAPI.AsrResponse;
import org.jocean.aliyun.nls.NlsmetaAPI;
import org.jocean.aliyun.nls.NlsmetaAPI.CreateTokenResponse;
import org.jocean.aliyun.oss.BlobRepoOverOSS;
import org.jocean.aliyun.sign.AliyunSigner;
import org.jocean.aliyun.sign.AliyunSigner2;
import org.jocean.bce.oauth.OAuthAPI;
import org.jocean.http.ByteBufSlice;
import org.jocean.http.ContentUtil;
import org.jocean.http.DoFlush;
import org.jocean.http.FullMessage;
import org.jocean.http.Interact;
import org.jocean.http.MessageBody;
import org.jocean.http.MessageUtil;
import org.jocean.http.RpcExecutor;
import org.jocean.http.RpcRunner;
import org.jocean.http.server.HttpServerBuilder.HttpTrade;
import org.jocean.idiom.BeanFinder;
import org.jocean.idiom.DisposableWrapper;
import org.jocean.idiom.DisposableWrapperUtil;
import org.jocean.idiom.jmx.MBeanRegister;
import org.jocean.idiom.jmx.MBeanRegisterAware;
import org.jocean.lbsyun.LbsyunUtil;
import org.jocean.netty.util.BufsInputStream;
import org.jocean.redis.RedisClient;
import org.jocean.redis.RedisUtil;
import org.jocean.restfuldemo.bean.DemoRequest;
import org.jocean.rpc.RpcDelegater;
import org.jocean.svr.ByteBufSliceUtil;
import org.jocean.svr.FinderUtil;
import org.jocean.svr.MultipartTransformer;
import org.jocean.svr.ResponseBean;
import org.jocean.svr.ResponseUtil;
import org.jocean.svr.TradeContext;
import org.jocean.svr.UntilRequestCompleted;
import org.jocean.svr.WithBody;
import org.jocean.svr.WithRawBody;
import org.jocean.svr.WithSlice;
import org.jocean.svr.ZipUtil;
import org.jocean.svr.ZipUtil.TozipEntity;
import org.jocean.svr.ZipUtil.ZipBuilder;
import org.jocean.wechat.AuthorizedMP;
import org.jocean.wechat.WXCommonAPI;
import org.jocean.wechat.WXCommonAPI.UploadTempMediaResponse;
import org.jocean.wechat.WechatAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.google.common.base.Charsets;
import com.google.common.io.BaseEncoding;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import rx.Observable;
import rx.Observable.Transformer;
import rx.functions.Action1;

@Path("/newrest/")
@Controller
@Scope("singleton")
public class DemoController implements MBeanRegisterAware {
    private static final Logger LOG = LoggerFactory.getLogger(DemoController.class);

    @Value("${xfyun_appid}")
    String _xfyun_appid;
    boolean _speechInited = false;
    boolean _IsEndOfSpeech = false;
    StringBuffer _recognizeResult = new StringBuffer();

    public static <T> Transformer<MessageBody, BufsInputStream<DisposableWrapper<? extends ByteBuf>>> body2InputStream() {
        final BufsInputStream<DisposableWrapper<? extends ByteBuf>> is =
                new BufsInputStream<>(dwb -> dwb.unwrap(), dwb -> dwb.dispose());
        return bodys -> bodys.flatMap(body -> body.content().doOnNext(addBufsAndStep(is)).last().map(last -> {
            is.markEOS();
            return is;
        }));
    }

    private static Action1<ByteBufSlice> addBufsAndStep(final BufsInputStream<DisposableWrapper<? extends ByteBuf>> is) {
        return bbs -> {
            try {
                is.appendIterable(bbs.element());
            } finally {
                bbs.step();
            }
        };
    }

    /*
    // 听写监听器
    private final RecognizerListener recListener = new RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            LOG.info( "onBeginOfSpeech enter: *************开始录音*************");
        }

        @Override
        public void onEndOfSpeech() {
            LOG.info( "onEndOfSpeech enter" );
            _IsEndOfSpeech = true;
        }

        @Override
        public void onVolumeChanged(final int volume) {
            LOG.info( "onVolumeChanged enter" );
            if (volume > 0)
                LOG.info("*************音量值:" + volume + "*************");

        }

        @Override
        public void onResult(final RecognizerResult result, final boolean islast) {
            LOG.info( "onResult enter" );
            _recognizeResult.append(result.getResultString());

            if( islast ){
                LOG.info("识别结果为:" + _recognizeResult.toString());
                _IsEndOfSpeech = true;
                _recognizeResult.delete(0, _recognizeResult.length());
            }
        }

        @Override
        public void onError(final SpeechError error) {
            _IsEndOfSpeech = true;
            LOG.warn("*************" + error.getErrorCode()
                    + "*************");
        }

        @Override
        public void onEvent(final int eventType, final int arg1, final int agr2, final String msg) {
            LOG.info( "onEvent enter" );
        }
    };

    @Path("pcm2text")
    @POST
    public Observable<Object> pcm2text(final RpcExecutor executor, final Observable<MessageBody> getbody) {
        if (!_speechInited) {
            _speechInited = true;
            SpeechUtility.createUtility("appid=" + _xfyun_appid);
        }

        if (SpeechRecognizer.getRecognizer() == null)
            SpeechRecognizer.createRecognizer();


        return getbody.compose(body2InputStream()).map(is -> {

            _IsEndOfSpeech = false;

            final SpeechRecognizer recognizer = SpeechRecognizer.getRecognizer();
            recognizer.setParameter(SpeechConstant.AUDIO_SOURCE, "-1");
            //写音频流时，文件是应用层已有的，不必再保存
//          recognizer.setParameter(SpeechConstant.ASR_AUDIO_PATH, "./iat_test.pcm");
            recognizer.setParameter( SpeechConstant.RESULT_TYPE, "plain" );
            recognizer.startListening(recListener);

            final byte[] buffer = new byte[64*1024];
            try {
                int lenRead = buffer.length;
                while( buffer.length==lenRead && !_IsEndOfSpeech){
                    lenRead = is.read( buffer );
                    recognizer.writeAudio(buffer, 0, lenRead );
                }//end of while

                recognizer.stopListening();

            } catch (final Exception e) {
                LOG.warn("exception when pcm2text, detail: {}", ExceptionUtils.exception2detail(e));
            } finally {
            }

            return "OK.";
        });
    }
    */

    @Path("ivision/imagePredict")
    public Observable<? extends Object> ivisionImagePredict(final RpcExecutor executor,
            @QueryParam("dataurl") final String dataUrl,
            @QueryParam("modelid") final String modelId
            ) {
        return executor.submit(
                interacts -> interacts.compose(alisign()).compose(
                        RpcDelegater.build2(IvisionAPI.class).imagePredict().modelId(modelId).dataUrl(dataUrl).call()))
                .map(resp -> {
                    if (resp.getImagePredict().getStatus().equals("Success")) {
                        return JSON.parseObject(resp.getImagePredict().getPredictResult(),
                                IvisionAPI.PredictResults.class);
                    } else {
                        return resp;
                    }
                });
    }

    Transformer<Interact, Interact> applytoken() {
        return interacts ->
            _finder.find(RpcExecutor.class).flatMap(executor -> nlstoken(executor).map(resp -> resp.getNlsToken().getId()).flatMap(
                    token -> interacts.doOnNext( interact -> interact.onrequest( obj -> {
                        if (obj instanceof HttpRequest) {
                            final HttpRequest req = (HttpRequest)obj;
                            req.headers().set("X-NLS-Token", token);
//                            req.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM);
//                            HttpUtil.setContentLength(req, content.contentLength());
                        }
                    }))));
    }

    Transformer<Interact, Interact> appkey() {
        return interacts -> interacts.doOnNext( interact -> interact.paramAsQuery("appkey", _nlsAppkey));
    }

    Transformer<Interact, Interact> alisign() {
        return interacts -> _finder.find(AliyunSigner2.class).flatMap(signer -> {
            LOG.info("alisign: sign by {}", signer);
            return interacts.doOnNext(signer);
        });
    }

    @Path("wx/qrcode")
    public Observable<? extends ResponseBean> wxQrcode(
            final RpcExecutor executor,
            @QueryParam("appid") final String appid,
            @QueryParam("expire") final int expire,
            @QueryParam("scene") final String scene) {
        LOG.info("call ecs/wx.createVolatileQrcode  with appid:{}/expire:{}/scene:{}", appid, expire, scene);
        return executor.execute( Observable.zip( _finder.find(appid, AuthorizedMP.class), _finder.find(WXCommonAPI.class),
                (mp, wcapi)-> wcapi.createVolatileQrcode(mp.getAccessToken(), expire, scene)))
                .map(uri -> ResponseUtil.redirectOnly(uri));
    }

    @Path("ecs/stopInstance")
    public Observable<? extends Object> stopInstance(
            final RpcExecutor executor,
            @QueryParam("instance") final String instanceId,
            @QueryParam("force") final boolean force) {
        LOG.info("call ecs/stopInstance with instanceId:{}/force:{}", instanceId, force);
        return _finder.find(_signer, AliyunSigner.class).flatMap(signer -> executor.execute(
                runners -> runners.doOnNext(signer),
                RpcDelegater.build(EcsAPI.class).stopInstance()
                    .instanceId(instanceId)
                    .forceStop(force)
                    .call() ));
    }

    @Path("ecs/deleteInstance")
    public Observable<? extends Object> deleteInstance(
            final RpcExecutor executor,
            @QueryParam("instance") final String instanceId,
            @QueryParam("force") final boolean force) {
        LOG.info("call ecs/deleteInstance with instanceId:{}/force:{}", instanceId, force);
        return _finder.find(_signer, AliyunSigner.class).flatMap(signer -> executor.execute(
                runners -> runners.doOnNext(signer),
                RpcDelegater.build(EcsAPI.class).deleteInstance()
                .instanceId(instanceId)
                .force(force)
                .call() ));
    }

    @Path("ecs/startInstance")
    public Observable<? extends Object> startInstance(
            final RpcExecutor executor,
            @QueryParam("instance") final String instanceId) {
        return _finder.find(_signer, AliyunSigner.class).flatMap(signer -> executor.execute(
                runners -> runners.doOnNext(signer),
                RpcDelegater.build(EcsAPI.class).startInstance()
                .instanceId(instanceId)
                .call() ));
    }

    @Path("ecs/createInstance")
    public Observable<? extends Object> createInstance(
            final RpcExecutor executor,
            @QueryParam("region") final String regionId,
            @QueryParam("instanceType") final String instanceType,
            @QueryParam("imageId") final String imageId,
            @QueryParam("securityGroupId") final String securityGroupId,
            @QueryParam("instanceName") final String instanceName,
            @QueryParam("hostName") final String hostName,
            @QueryParam("description") final String description,
            @QueryParam("vSwitchId") final String vSwitchId,
            @QueryParam("spotPriceLimit") final float spotPriceLimit,
            @QueryParam("keyPairName") final String keyPairName,
            @QueryParam("ramRoleName") final String ramRoleName) {
        return _finder.find(_signer, AliyunSigner.class).flatMap(signer -> executor.execute(
                runners -> runners.doOnNext(signer),
                RpcDelegater.build(EcsAPI.class).createInstance()
                .dryRun(true)
                .imageId(imageId)
                .instanceType(instanceType)
                .regionId(regionId)
                .securityGroupId(securityGroupId)
                .internetMaxBandwidthOut(0)
//                .internetChargeType("PayByTraffic")
                .instanceName(instanceName)
                .hostName(hostName)
                .systemDiskSize(20)
                .systemDiskCategory("cloud_efficiency")
                .ioOptimized("optimized")
                .description(description)
                .vSwitchId(vSwitchId)
                .useAdditionalService(true)
                .instanceChargeType("PostPaid")
                .spotStrategy("SpotWithPriceLimit")
                .spotPriceLimit(spotPriceLimit)
                .keyPairName(keyPairName)
                .ramRoleName(ramRoleName)
                .securityEnhancementStrategy("Active")
                .call() ));
    }

    @Path("ecs/describeSpotPriceHistory")
    public Observable<? extends Object> describeSpotPriceHistory(final RpcExecutor executor,
            @QueryParam("region") final String regionId,
            @QueryParam("instanceType") final String instanceType
            ) {
        return _finder.find(_signer, AliyunSigner.class).flatMap(signer -> executor.execute(
                runners -> runners.doOnNext(signer),
                RpcDelegater.build(EcsAPI.class).describeSpotPriceHistory()
                    .regionId(regionId)
                    .instanceType(instanceType)
                    .networkType("vpc")
                    .call()));
    }

    @Path("ecs/describeInstances")
    public Observable<? extends Object> ecsDescribeInstances(final RpcExecutor executor,
            @QueryParam("region") final String regionId,
            @QueryParam("vpc") final String vpcId,
            @QueryParam("instancename") final String instanceName
            ) {
        return _finder.find(_signer, AliyunSigner.class).flatMap(signer -> executor.execute(
                runners -> runners.doOnNext(signer),
                RpcDelegater.build(EcsAPI.class).describeInstances()
                    .regionId(regionId)
                    .vpcId(vpcId)
                    .instanceName(instanceName)
                    .call()));
    }

    @Path("ecs/describeInstanceStatus")
    public Observable<? extends Object> ecsDescribeInstanceStatus(
            final RpcExecutor executor,
            @QueryParam("region") final String regionId,
            @QueryParam("pageidx") final String pageidx,
            @QueryParam("pagesize") final String pagesize
            ) {

        final DescribeInstanceStatusBuilder builder = RpcDelegater.build(EcsAPI.class).describeInstanceStatus().regionId(regionId);
        if (null != pageidx && null != pagesize) {
            builder.pageNumber( Integer.parseInt(pageidx));
            builder.pageSize(Integer.parseInt(pagesize));
        }

        return _finder.find(_signer, AliyunSigner.class).flatMap(signer -> executor.execute(
                runners -> runners.doOnNext(signer),builder.call()));
    }

    @Path("ecs/describeUserData")
    public Observable<? extends Object> ecsDescribeUserData(
            final RpcExecutor executor,
            @QueryParam("instance") final String instance,
            @QueryParam("region") final String regionId) {
        return _finder.find(_signer, AliyunSigner.class).flatMap(signer -> executor.execute(
                runners -> runners.doOnNext(signer),
                RpcDelegater.build(EcsAPI.class).describeUserData()
                    .instanceId(instance)
                    .regionId(regionId)
                    .call()));
    }

    @Path("ecs/describeInstanceRamRole")
    public Observable<? extends Object> ecsDescribeInstanceRamRole(
            final RpcExecutor executor,
            @QueryParam("region") final String regionId,
            @QueryParam("instances") final String instances,
            @QueryParam("ramrole") final String ramrole,
            @QueryParam("pageidx") final String pageidx,
            @QueryParam("pagesize") final String pagesize
            ) {
        final DescribeInstanceRamRoleBuilder builder = RpcDelegater.build(EcsAPI.class).describeInstanceRamRole().regionId(regionId);

        if (null != instances) {
            builder.instanceIds(instances);
        }

        if (null != ramrole) {
            builder.ramRoleName(ramrole);
        }

        if (null != pageidx) {
            builder.pageNumber(Integer.parseInt(pageidx));
        }

        if (null != pagesize) {
            builder.pageSize(Integer.parseInt(pagesize));
        }

        return _finder.find(_signer, AliyunSigner.class).flatMap(signer -> executor.execute(
                runners -> runners.doOnNext(signer), builder.call()));
    }


    @Path("bce/accesstoken")
    public Observable<? extends Object> bceAccessToken(final RpcExecutor executor) {
        return executor.execute(_finder.find(OAuthAPI.class).map(api -> api.getAccessToken() ));
    }

    @Path("nls/asr")
    @OPTIONS
    @POST
    public Observable<AsrResponse> nlsasr(
            final RpcExecutor executor,
            final Observable<MessageBody> getbody) {
        return getbody.flatMap(body -> executor.submit(interacts -> interacts.compose(applytoken()).compose(appkey())
                .compose(RpcDelegater.build2(NlsAPI.class).streamAsrV1().body(body).call())));
    }

    @Path("nls/token")
    @GET
    public Observable<CreateTokenResponse> nlstoken(final RpcExecutor executor) {
        return executor.submit(
                interacts -> interacts.compose(alisign()).compose(
                        RpcDelegater.build2(NlsmetaAPI.class).createToken().call()));
    }

    static interface ImageTag {
        @JSONField(name="confidence")
        public int getConfidence();

        @JSONField(name="confidence")
        public void setConfidence(final int confidence);

        @JSONField(name="value")
        public String getValue();

        @JSONField(name="value")
        public void setValue(final String value);
    }

    static interface ImageTagResponse {
        @JSONField(name="errno")
        public int getErrno();

        @JSONField(name="errno")
        public void setErrno(final int errno);

        @JSONField(name="err_msg")
        public String getErrmsg();

        @JSONField(name="err_msg")
        public void setErrmsg(final String errmsg);

        @JSONField(name="request_id")
        public String getRequestId();

        @JSONField(name="request_id")
        public void setRequestId(final String requestId);

        @JSONField(name="tags")
        public ImageTag[] getTags();

        @JSONField(name="tags")
        public void setTags(final ImageTag[] tags);
    }

    static class ImageTagRequest {
        @JSONField(name="type")
        public int getType() {
            return _type;
        }

        @JSONField(name="image_url")
        public String getUrl() {
            return _url;
        }

        @JSONField(name="image_url")
        public void setUrl(final String url) {
            this._url = url;
        }

        private final int _type = 0;
        private String _url;
    }
    /*
     * 计算MD5+BASE64
     */
    public static String MD5Base64(final String s) {
        if (s == null)
            return null;
        String encodeStr = "";
        final byte[] utfBytes = s.getBytes();
        MessageDigest mdTemp;
        try {
            mdTemp = MessageDigest.getInstance("MD5");
            mdTemp.update(utfBytes);
            encodeStr = BaseEncoding.base64().encode(mdTemp.digest());
        } catch (final Exception e) {
            throw new Error("Failed to generate MD5 : " + e.getMessage());
        }
        return encodeStr;
    }

    /*
     * 计算 HMAC-SHA1
     */
    public static String HMACSha1(final String data, final String key) {
        String result;
        try {
            final SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), "HmacSHA1");
            final Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signingKey);
            result = BaseEncoding.base64().encode(mac.doFinal(data.getBytes()));
        } catch (final Exception e) {
            throw new Error("Failed to generate HMAC : " + e.getMessage());
        }
        return result;
    }

    /*
     * 等同于javaScript中的 new Date().toUTCString();
     */
    public static String toGMTString(final Date date) {
        final SimpleDateFormat df = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z", Locale.UK);
        df.setTimeZone(new java.util.SimpleTimeZone(0, "GMT"));
        return df.format(date);
    }

    public Transformer<RpcRunner, ImageTagResponse> imagetag (final String url, final String ak_id, final String ak_secret) {
        return runners -> runners.flatMap(runner -> runner.name("aliyun.imagetag").execute(interact -> {
            try {
                final ImageTagRequest req = new ImageTagRequest();

                req.setUrl(url);

                final String body = JSON.toJSONString(req);
                final byte[] bodyBytes = body.getBytes(Charsets.UTF_8);
                final ByteBufSlice bbs = ByteBufSliceUtil.wrappedSlice(bodyBytes);

                final MessageBody msgbody = new MessageBody() {

                    @Override
                    public HttpHeaders headers() {
                        return EmptyHttpHeaders.INSTANCE;
                    }

                    @Override
                    public String contentType() {
                        return MediaType.APPLICATION_JSON;
                    }

                    @Override
                    public int contentLength() {
                        return bodyBytes.length;
                    }

                    @Override
                    public Observable<? extends ByteBufSlice> content() {
                        return Observable.just(bbs);
                    }};

                /*
                 * http header 参数
                 */
                final String method = "POST";
                final String accept = MediaType.APPLICATION_JSON;
                final String content_type = MediaType.APPLICATION_JSON;
                final String path = "/image/tag";
                final String date = toGMTString(new Date());
                // 1.对body做MD5+BASE64加密
                final String bodyMd5 = MD5Base64(body);
                final String stringToSign = method + "\n" + accept + "\n" + bodyMd5 + "\n" + content_type + "\n" + date + "\n"
                        + path;
                // 2.计算 HMAC-SHA1
                final String signature = HMACSha1(stringToSign, ak_secret);
                // 3.得到 authorization header
                final String authHeader = "Dataplus " + ak_id + ":" + signature;

                return interact.method(HttpMethod.POST)
                        .uri("https://dtplus-cn-shanghai.data.aliyuncs.com")
                        .path(path)
                        .body(Observable.just(msgbody))
                        .onrequest(obj -> {
                            if (obj instanceof HttpRequest) {
                                final HttpRequest httpreq = (HttpRequest)obj;
                                httpreq.headers().set(HttpHeaderNames.ACCEPT, accept);
                                httpreq.headers().set(HttpHeaderNames.DATE, date);
                                httpreq.headers().set(HttpHeaderNames.AUTHORIZATION, authHeader);
                            }
                        })
                        .responseAs(ContentUtil.ASJSON, ImageTagResponse.class);
            } catch (final Exception e) {
                return Observable.error(e);
            }
        }));
    }

    @Path("imgtag")
    @GET
    public Observable<ImageTagResponse> imgtag(@QueryParam("url") final String imgurl, final RpcExecutor executor) {
        return executor.execute( imagetag(imgurl, _ak_id, _ak_secret) );
    }

    public interface TaskMBean {
        public void start();
        public void addMonitor(Monitor monitor);
    }

    public class Task implements TaskMBean {

        @Override
        public void start() {
            _shared.subscribe(v -> {});
        }

        @Override
        public void addMonitor(final Monitor monitor) {
            _shared.subscribe(v -> monitor.append(System.currentTimeMillis() + "\r\n"));
        }

        final Observable<Long> _shared = Observable.timer(1, TimeUnit.SECONDS).share();
    }

    @Override
    public void setMBeanRegister(final MBeanRegister register) {
//        final Observable<Long> shared = Observable.timer(1, TimeUnit.SECONDS).share();

        register.registerMBean("name=task", new Task());
    }

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
                }
                @Override
                public HttpHeaders headers() {
                    return EmptyHttpHeaders.INSTANCE;
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
                    }

                    @Override
                    public HttpHeaders headers() {
                        return EmptyHttpHeaders.INSTANCE;
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
                return executor.execute(_finder.find(BlobRepo.class).map(repo -> repo.getObject(key)));
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
        return executor.execute(_finder.find(BlobRepo.class).map( repo -> repo.getSimplifiedObjectMeta(objname)))
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

        tctx.haltable().doOnHalt(() -> LOG.info("total unziped size is: {}", unzipedSize.get()));

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

    @Path("uploadlines")
    @POST
    public Observable<Object> uploadlines( final HttpRequest request, final HttpTrade trade) {
        return trade.inbound().flatMap(fmsg -> fmsg.body())
                .flatMap(body -> body.content())
                .compose(ByteBufSliceUtil.asLineSlice())
                .doOnNext(slice -> {
                    try {
                        for (final String line : slice.element() ) {
                            LOG.debug("Line: {}", line);
                        }
                    }
                    finally {
                        slice.step();
                    }
                }).last().map(slice -> "OK");
    }

    @Path("proxy_pwd")
    public BinaryResponse proxy_passwd(
            @QueryParam("uri") final String uri,
            @QueryParam("pwd") final String pwd,
            final TradeContext tctx,
            final RpcExecutor executor) {

        tctx.writeCtrl().sended().subscribe(msg -> DisposableWrapperUtil.dispose(msg));

        return new BinaryResponse("1.zip") {
            @Override
            public String contentType() {
                return HttpHeaderValues.APPLICATION_OCTET_STREAM.toString();
            }

            @Override
            public Observable<? extends ByteBufSlice> slices() {
                return executor.execute(fetch(uri)).flatMap(fullmsg -> fullmsg.body()).<TozipEntity>map(body -> new TozipEntity() {
                    @Override
                    public String entryName() {
                        return "123.txt";
                    }
                    @Override
                    public Observable<? extends ByteBufSlice> body() {
                        return body.content().doOnNext( bbs -> {
                            LOG.debug("=========== source slice: {}", bbs);
                        });
                    }})
                .compose(ZipUtil.zipEntitiesWithPassword(tctx.allocatorBuilder().build(8192), tctx.haltable(), 512, dwb -> dwb.dispose(), pwd));
            }
        };
    }

    @Path("upload_ziplines")
    @POST
    public Observable<Object> upload_ziplines(final HttpTrade trade, final ZipBuilder zipBuilder, final TradeContext tctx) {
        return trade.inbound().flatMap(fmsg -> fmsg.body())
                .flatMap(body -> {
                    final String contentType = body.headers().get(HttpHeaderNames.CONTENT_TYPE);
                    final String multipartDataBoundary = MultipartTransformer.getBoundary(contentType);
                    return body.content().compose(new MultipartTransformer(tctx.allocatorBuilder().build(8192), multipartDataBoundary));
                })
                .flatMap(body -> {
                    LOG.info("multipart headers: {}", body.headers());
                    return body.content();
                })
//                .compose(zipBuilder.unzip(8192, 512))
                .compose(ZipUtil.unzipToEntitiesNew(tctx.allocatorBuilder().build(8192), trade, 8192, dwb -> dwb.dispose()))
                .flatMap(entity -> {
                    LOG.info("unzip entity {}", entity.entry().getName());
                    return entity.body();
                })
                .compose(ByteBufSliceUtil.asLineSlice())
                .doOnNext(slice -> {
                    try {
                        for (final String line : slice.element() ) {
                            LOG.info("Line: {}", line);
                        }
                    }
                    finally {
                        slice.step();
                    }
                }).last().map(slice -> "OK");
    }

    private Observable<Object> handle100Continue(final HttpRequest request) {
        return HttpUtil.is100ContinueExpected(request)
            ? Observable.<Object>just(ResponseUtil.response().setStatus(100), DoFlush.Util.flushOnly())
            : Observable.empty();
    }

    @Value("${ak_secret}")
    private String _ak_secret;

    @Value("${ak_id}")
    private String _ak_id;

    @Value("${signer.name}")
    private String _signer;

    @Value("${nls.appkey}")
    String _nlsAppkey;

    @Inject
    private BeanFinder _finder;

    @Inject
    RpcExecutor _executor;
}
