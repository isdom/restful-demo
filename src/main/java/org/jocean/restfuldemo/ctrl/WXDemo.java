package org.jocean.restfuldemo.ctrl;

import java.io.IOException;
import java.io.OutputStream;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.http.ContentEncoder;
import org.jocean.http.MessageBody;
import org.jocean.http.RpcExecutor;
import org.jocean.idiom.DisposableWrapper;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.netty.util.BufsInputStream;
import org.jocean.svr.annotation.HandleError;
import org.jocean.wechat.WXProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.common.primitives.Bytes;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import rx.Observable;
import rx.functions.Action2;


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

    @Path("wx/img_sec_check")
    @POST
    public Observable<Object> ImgSecCheck(
            @QueryParam("access_token") final String access_token,
            @QueryParam("filename") final String filename,
            @QueryParam("name") final String name,
            @QueryParam("boundary") final String boundary,
            @QueryParam("mime") final String mime,
            final Observable<MessageBody> getbody,
            final RpcExecutor rpc
            ) {
        final BufsInputStream<DisposableWrapper<? extends ByteBuf>> is =
                new BufsInputStream<>(dwb -> dwb.unwrap(), dwb -> dwb.dispose());

        return getbody.flatMap(body -> body.content().doOnNext(bbs -> {
                    try {
                        is.appendIterable(bbs.element());
                    } finally {
                        bbs.step();
                    }
                })).flatMap(bbs -> Observable.empty(), e -> Observable.error(e), () -> {
                    is.markEOS();
                    byte[] content = new byte[0];
                    try {
                        content = ByteStreams.toByteArray(is);
                    } catch (final IOException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }

//                    final String contentType = "multipart/form-data; boundary=" + boundary;

                    final StringBuilder sb = new StringBuilder();
                    sb.append("--");
                    sb.append(boundary);
                    sb.append("\r\n");
                    sb.append("Content-Disposition: form-data; name=\"" + name +"\"; filename=\"" +filename+ "\"");
                    sb.append("\r\n");
                    sb.append("Content-Type: " + mime);
                    sb.append("\r\n\r\n");

                    final byte[] body = Bytes.concat(sb.toString().getBytes(Charsets.UTF_8), content, ("\r\n--" + boundary + "--\r\n").getBytes(Charsets.UTF_8));

                    final ContentEncoder to_formdata = new ContentEncoder() {
                        @Override
                        public String contentType() {
                            return "multipart/form-data; boundary=" + boundary;
                        }
                        @Override
                        public Action2<Object, OutputStream> encoder() {
                            return (bean, os) -> {
                                final byte[] bytes = (byte[])bean;
                                try {
                                    os.write(bytes);
                                } catch (final IOException e) {
                                    e.printStackTrace();
                                }
                            };
                        }};

                    return rpc.submit(inters -> inters.flatMap(interact ->
                            interact.method(HttpMethod.POST)
                            .uri("https://api.weixin.qq.com")
                            .path("/wxa/img_sec_check")
                            .paramAsQuery("access_token", access_token)
                            .body(body, to_formdata)
                            .responseAs(WXProtocol.WXAPIResponse.class) ));
                });
    }
}
