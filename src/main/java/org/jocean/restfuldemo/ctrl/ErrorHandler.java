package org.jocean.restfuldemo.ctrl;

import org.jocean.aliyun.oss.OssException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.netty.handler.codec.http.HttpRequest;

@Component
public class ErrorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ErrorHandler.class);

    String handleOssException(final OssException osserror, final HttpRequest req) {
        LOG.warn("handle oss error when {}, detail: {}", req.uri(), osserror.error());
        return osserror.error().toString();
    }
}
