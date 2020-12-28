package org.jocean.restfuldemo.ctrl;

import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.aliyun.oss.OssAPI;
import org.jocean.aliyun.oss.OssBucket;
import org.jocean.aliyun.oss.OssException;
import org.jocean.aliyun.sts.STSCredentials;
import org.jocean.http.DoFlush;
import org.jocean.http.FullMessage;
import org.jocean.http.MessageBody;
import org.jocean.svr.ResponseUtil;
import org.jocean.svr.annotation.RpcFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import rx.Observable;


@Path("/newrest/")
@Controller
@Scope("prototype")
public class OssDemo {
    private static final Logger LOG = LoggerFactory.getLogger(OssDemo.class);

    @Inject
    @Named("${ecs.id}-stsc")
    STSCredentials _stsc;

    @Inject
    @Named("ossbucket-${oss.bucket}")
    private OssBucket _bucket;

    @Value("${upload.path}")
    private String _uploadPath;

    @RpcFacade
    OssAPI oss;

    @Path("oss/getobj")
    public Observable<? extends Object> getobj(@QueryParam("obj") final String object) {
        return oss.getObject()
                .signer(_stsc.ossSigner())
                .bucket(_bucket)
                .object(object)
                .call()
                .<Object>map(fullresp -> fullresp)
                .doOnError( e -> LOG.warn("error when getobj, detail: {}", ((OssException)e).error()))
                .onErrorReturn(e -> ((OssException)e).error().toString());
    }

    @Path("oss/getslink")
    public Observable<FullMessage<HttpResponse>> getslink(
            @QueryParam("symlink") final String symlink
            ) {
        return oss.getSymlink().bucket(_bucket).symlinkObject(symlink).call();
    }

    @Path("oss/putslink")
    public Observable<FullMessage<HttpResponse>> putslink(
            @QueryParam("symlink") final String symlink,
            @QueryParam("target") final String target
            ) {
        return oss.putSymlink()
            .bucket(_bucket)
            .symlinkObject(symlink)
            .targetObject(target)
            .call();
    }

    @Path("oss/delobj")
    public Observable<FullMessage<HttpResponse>> deleteObject(@QueryParam("obj") final String object) {
        return oss.deleteObject()
            .bucket(_bucket)
            .object(object)
            .call();
    }

    @Path("oss/copyobj")
    public Observable<FullMessage<HttpResponse>> copyObject(
            @QueryParam("dest") final String dest,
            @QueryParam("sourcePath") final String sourcePath
            ) {
        return oss.copyObject()
            .bucket(_bucket)
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
            .bucket(_bucket)
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
            .bucket(_bucket)
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
                .bucket(_bucket)
                .object(_uploadPath + "/" + UUID.randomUUID().toString().replaceAll("-", ""))
                .body(getbody)
                .call()
                .map(result -> result.toString()))
                .doOnError( e -> LOG.warn("error when uploadNew, detail: {}", ((OssException)e).error()))
                .onErrorReturn(e -> ((OssException)e).error().toString())
                ;
    }

    private Observable<Object> handle100Continue(final HttpRequest request) {
        return HttpUtil.is100ContinueExpected(request)
            ? Observable.<Object>just(ResponseUtil.response().setStatus(100), DoFlush.Util.flushOnly())
            : Observable.empty();
    }
}
