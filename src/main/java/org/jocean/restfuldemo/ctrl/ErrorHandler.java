package org.jocean.restfuldemo.ctrl;

import org.jocean.aliyun.oss.OssException;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.svr.annotation.HandleError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.netty.handler.codec.http.HttpRequest;

@Component
public class ErrorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ErrorHandler.class);

    @HandleError(OssException.class)
    String handleOssException(final HttpRequest req, final OssException osserror) {
        LOG.warn("handle oss error when {}, detail: {}", req.uri(), osserror.error());
        return "handle oss error when " + req.uri() + "{\n" + osserror.error().toString() + "\n}";
    }

    @HandleError(Exception.class)
    String handleException(final HttpRequest req, final Exception error) {
        LOG.warn("error when {}, detail: {}", req.uri(), ExceptionUtils.exception2detail(error));
        return "error when " + req.uri() + "{\n" + ExceptionUtils.exception2detail(error) + "\n}";
    }
}
