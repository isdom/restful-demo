package org.jocean.restfuldemo.ctrl;

import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.aliyun.oss.OssAPI;
import org.jocean.aliyun.oss.OssBucket;
import org.jocean.aliyun.sts.STSCredentials;
import org.jocean.http.DoFlush;
import org.jocean.http.FullMessage;
import org.jocean.http.Interact;
import org.jocean.http.MessageBody;
import org.jocean.http.MessageUtil;
import org.jocean.svr.ResponseUtil;
import org.jocean.svr.annotation.RpcFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import rx.Observable;
import rx.Observable.Transformer;

@Path("/newrest/")
@Controller
@Scope("prototype")
public class OssDemo {
    // private static final Logger LOG = LoggerFactory.getLogger(OssDemo.class);

    @Inject
    @Named("${ecs.id}-stsc")
    STSCredentials _stsc;

    @Inject
    @Named("ossbucket-${oss.bucket}")
    private OssBucket _bucket;

    @Value("${upload.path}")
    private String _uploadPath;

    Transformer<Interact, Interact> alisign_sts_oss() {
        return _stsc.ossSigner();
    }

    @RpcFacade("this.alisign_sts_oss()")
    OssAPI oss;

    @Path("oss/getobj")
    public Observable<Object> getobj(@QueryParam("obj") final String object) {
        return _bucket.apply(oss.getObject()).object(object).call().flatMap(fullresp -> {
            if (fullresp.message().status().equals(HttpResponseStatus.OK)) {
                return Observable.just(fullresp);
            } else {
                return fullresp.body().flatMap(body -> MessageUtil.decodeContentAs(body.content(), (is, cls) -> MessageUtil.parseContentAsString(is), String.class) );
            }
        });
    }

    @Path("oss/getslink")
    public Observable<FullMessage<HttpResponse>> getslink(
            @QueryParam("symlink") final String symlink
            ) {
        return _bucket.apply(oss.getSymlink()).symlinkObject(symlink).call();
    }

    @Path("oss/putslink")
    public Observable<FullMessage<HttpResponse>> putslink(
            @QueryParam("symlink") final String symlink,
            @QueryParam("target") final String target
            ) {
        return _bucket.apply(oss.putSymlink())
            .symlinkObject(symlink)
            .targetObject(target)
            .call();
    }

    @Path("oss/delobj")
    public Observable<FullMessage<HttpResponse>> deleteObject(@QueryParam("obj") final String object) {
        return _bucket.apply(oss.deleteObject())
            .object(object)
            .call();
    }

    @Path("oss/copyobj")
    public Observable<FullMessage<HttpResponse>> copyObject(
            @QueryParam("dest") final String dest,
            @QueryParam("sourcePath") final String sourcePath
            ) {
        return _bucket.apply(oss.copyObject())
            .destObject(dest)
            .source(sourcePath)
            .call();
    }

    @Path("oss/listobj")
    public Observable<String> list(
            @QueryParam("prefix") final String prefix,
            @QueryParam("marker") final String marker,
            @QueryParam("delimiter") final String delimiter,
            @QueryParam("encodingType") final String encodingType,
            @QueryParam("maxKeys") final String maxKeys
            ) {
        return _bucket.apply(oss.listObjects())
            .prefix(prefix)
            .marker(marker)
            .delimiter(delimiter)
            .encodingType(encodingType)
            .maxKeys(maxKeys)
            .call()
            .map(listing -> listing.toString());
    }

    @Path("oss/meta")
    public Observable<String> ossmeta(
            @QueryParam("obj") final String objname
            ) {
        return _bucket.apply(oss.getObjectMeta())
            .object(objname)
            .call()
            .map( fullmsg -> fullmsg.message().headers().toString() );
    }

    @Path("oss/upload")
    @POST
    public Observable<Object> uploadNew(
            final HttpRequest request,
            final Observable<MessageBody> getbody
            ) {
        return handle100Continue(request)
                .concatWith(_bucket.apply(oss.putObject())
                        .object(_uploadPath + "/" + UUID.randomUUID().toString().replaceAll("-", ""))
                        .body(getbody)
                        .call());
    }

    private Observable<Object> handle100Continue(final HttpRequest request) {
        return HttpUtil.is100ContinueExpected(request)
            ? Observable.<Object>just(ResponseUtil.response().setStatus(100), DoFlush.Util.flushOnly())
            : Observable.empty();
    }

    /*
    private Transformer<Interact, PutObjectResult> putObject(
            final String bucketName,
            final String objname,
            final Observable<MessageBody> getbody) {
        return interacts -> interacts.flatMap( interact -> interact.name("oss.putObject").method(HttpMethod.PUT)
                .uri(uri4bucket(bucketName))
                .path("/" + objname)
                .body(getbody)
                .response()
                .<HttpResponse>flatMap(resp -> {
                    // https://help.aliyun.com/document_detail/32005.html?spm=a2c4g.11186623.6.1090.DeJEv5
                    final String contentType = resp.message().headers().get(HttpHeaderNames.CONTENT_TYPE);
                    if (null != contentType && contentType.startsWith("application/xml")) {
                        return OSSUtil.extractAndReturnOSSError(resp, "putObject error");
                    } else {
                        return Observable.just(resp.message());
                    }
                })
                .map(resp -> resp.headers().get(HttpHeaderNames.ETAG)).<PutObjectResult>map(etag -> {
                    final String unquotes_etag = etag.replaceAll("\"", "");
                    return new PutObjectResult() {
                        @Override
                        public String objectName() {
                            return objname;
                        }

                        @Override
                        public String etag() {
                            return unquotes_etag;
                        }
                    };
                }));
    }

    private String uri4bucket(final String bucketName) {
        return "http://" + bucketName + "." + _ossEndpoint;
    }
    */
}
