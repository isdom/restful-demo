package org.jocean.restfuldemo.ctrl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
import org.jocean.idiom.DisposableWrapper;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.netty.util.BufsInputStream;
import org.jocean.svr.ResponseUtil;
import org.jocean.svr.annotation.HandleError;
import org.jocean.svr.annotation.OnError;
import org.jocean.svr.annotation.RpcFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import com.google.common.io.ByteStreams;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import rx.Observable;


@Path("/newrest/")
@Controller
@Scope("prototype")
public class ChatgptDemo {
    private static final Logger LOG = LoggerFactory.getLogger(ChatgptDemo.class);

    @Value("${upload.path}")
    String _uploadPath;

    @HandleError(Exception.class)
    String handleAllError(final HttpRequest req, final Exception e) {
        LOG.warn("error when {}, detail: {}", req.uri(), e);
        return "error when " + req.uri() + "{\n" + ExceptionUtils.exception2detail(e) + "\n}";
    }

    @Path("chatgpt/ask")
    @OnError({
        "this.handleAllError"
        })
    public String ask(@QueryParam("a") final String question) {
        return question;
    }
}
