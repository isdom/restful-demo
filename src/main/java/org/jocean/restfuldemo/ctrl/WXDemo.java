package org.jocean.restfuldemo.ctrl;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.http.ByteBufSlice;
import org.jocean.http.MessageBody;
import org.jocean.http.RpcExecutor;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.svr.ByteBufSliceUtil;
import org.jocean.svr.annotation.HandleError;
import org.jocean.wechat.WXProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.google.common.base.Charsets;

import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.internal.PlatformDependent;
import rx.Observable;


@Path("/newrest/")
@Controller
@Scope("prototype")
public class WXDemo {
    private static final Logger LOG = LoggerFactory.getLogger(WXDemo.class);

    @HandleError(Exception.class)
    String handleAllError(final HttpRequest req, final Exception e) {
        LOG.warn("error when {}, detail: {}", req.uri(), e);
        return "error when " + req.uri() + "{\n" + ExceptionUtils.exception2detail(e) + "\n}";
    }

    /**
    *
    * @return a newly generated Delimiter (either for DATA or MIXED)
    */
   private static String getNewMultipartDelimiter() {
       // construct a generated delimiter
       return Long.toHexString(PlatformDependent.threadLocalRandom().nextLong());
   }

    @Path("wx/img_sec_check")
    @POST
    public Observable<Object> ImgSecCheck(
            @QueryParam("access_token") final String access_token,
            @QueryParam("filename") final String filename,
            @QueryParam("name") final String name,
            @QueryParam("mime") final String mime,
            @HeaderParam("content-length") final int contentLength,
            final Observable<MessageBody> getbody,
            final RpcExecutor rpc
            ) {
        final String boundary = getNewMultipartDelimiter();
        return getbody.flatMap(body -> {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("--");
                    sb.append(boundary);
                    sb.append("\r\n");
                    sb.append("Content-Disposition: form-data; name=\"" + name +"\"; filename=\"" +filename+ "\"");
                    sb.append("\r\n");
                    sb.append("Content-Type: " + mime);
                    sb.append("\r\n\r\n");

                    final byte[] prefix = sb.toString().getBytes(Charsets.UTF_8);
                    final byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(Charsets.UTF_8);

                    return rpc.submit(inters -> inters.flatMap(interact ->
                            interact.oninitiator(initiator -> initiator.writeCtrl().writability().subscribe(isWritable -> LOG.info("ImgSecCheck==>writability:{}", isWritable) ))
                            .method(HttpMethod.POST)
                            .uri("https://api.weixin.qq.com")
                            .path("/wxa/img_sec_check")
                            .paramAsQuery("access_token", access_token)
                            .body(Observable.<MessageBody>just(new MessageBody() {

                                @Override
                                public HttpHeaders headers() {
                                    return EmptyHttpHeaders.INSTANCE;
                                }

                                @Override
                                public String contentType() {
                                    return "multipart/form-data; boundary=" + boundary;
                                }

                                @Override
                                public int contentLength() {
                                    return prefix.length + contentLength + suffix.length;
                                }

                                @Override
                                public Observable<? extends ByteBufSlice> content() {
                                    return Observable.just(ByteBufSliceUtil.wrappedSlice(prefix))
                                            .concatWith(body.content())
                                            .concatWith(Observable.just(ByteBufSliceUtil.wrappedSlice(suffix)));
                                }}))
                            .responseAs(WXProtocol.WXAPIResponse.class) ));
                });
    }
}
