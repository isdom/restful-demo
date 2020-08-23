package org.jocean.restfuldemo.ctrl;

import java.util.UUID;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.aliyun.ecs.MetadataAPI;
import org.jocean.aliyun.oss.OssAPI;
import org.jocean.aliyun.sign.Signer4OSS;
import org.jocean.http.DoFlush;
import org.jocean.http.FullMessage;
import org.jocean.http.Interact;
import org.jocean.http.MessageBody;
import org.jocean.svr.ResponseUtil;
import org.jocean.svr.annotation.RpcFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import rx.Observable;
import rx.Observable.Transformer;

@Path("/newrest/")
@Controller
@Scope("prototype")
public class OssDemo {
    // private static final Logger LOG = LoggerFactory.getLogger(OssDemo.class);

    @Value("${oss.endpoint}")
    private String _ossEndpoint;

    @Value("${oss.bucket}")
    private String _ossBucket;

    @Value("${upload.path}")
    private String _uploadPath;

    @RpcFacade
    MetadataAPI.STSTokenBuilder  getststoken;

    Transformer<Interact, Interact> alisign_sts_oss() {
        return interacts -> getststoken.roleName(_role).call()
                .flatMap(stsresp -> interacts.doOnNext( interact -> interact.onsending(
                        Signer4OSS.signRequest(stsresp.getAccessKeyId(), stsresp.getAccessKeySecret(), stsresp.getSecurityToken()))));
    }

    @RpcFacade("this.alisign_sts_oss()")
    OssAPI oss;

    @Value("${role}")
    String _role;

    @Path("oss/getslink")
    public Observable<FullMessage<HttpResponse>> getslink(
            @QueryParam("symlink") final String symlink
            ) {
        return oss.getSymlink().bucket(_ossBucket).endpoint(_ossEndpoint).symlinkObject(symlink).call();
    }

    @Path("oss/putslink")
    public Observable<FullMessage<HttpResponse>> putslink(
            @QueryParam("symlink") final String symlink,
            @QueryParam("target") final String target
            ) {
        return oss.putSymlink()
            .bucket(_ossBucket)
            .endpoint(_ossEndpoint)
            .symlinkObject(symlink)
            .targetObject(target)
            .call();
    }

    @Path("oss/delobj")
    public Observable<FullMessage<HttpResponse>> deleteObject(
            @QueryParam("obj") final String object
            ) {
        return oss.deleteObject()
            .bucket(_ossBucket)
            .endpoint(_ossEndpoint)
            .object(object)
            .call();
    }

    @Path("oss/copyobj")
    public Observable<FullMessage<HttpResponse>> copyObject(
            @QueryParam("dest") final String dest,
            @QueryParam("sourcePath") final String sourcePath
            ) {
        return oss.copyObject()
            .bucket(_ossBucket)
            .endpoint(_ossEndpoint)
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
        return oss.listObjects()
            .bucket(_ossBucket)
            .endpoint(_ossEndpoint)
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
        return oss.getObjectMeta()
            .bucket(_ossBucket)
            .endpoint(_ossEndpoint)
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
                .concatWith(oss.putObject()
                        .bucket(_ossBucket)
                        .endpoint(_ossEndpoint)
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
