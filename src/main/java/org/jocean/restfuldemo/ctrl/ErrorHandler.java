package org.jocean.restfuldemo.ctrl;

import org.jocean.aliyun.oss.OssException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ErrorHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ErrorHandler.class);

    String handleOssException(final OssException osserror) {
        LOG.warn("handle oss error detail: {}", osserror.error());
        return osserror.error().toString();
    }
}
