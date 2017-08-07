/**
 *
 */
package org.jocean.restfuldemo.bll;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;

import org.jocean.event.api.AbstractFlow;
import org.jocean.event.api.BizStep;
import org.jocean.event.api.annotation.OnEvent;
import org.jocean.restful.OutputReactor;
import org.jocean.restful.OutputSource;
//import org.jocean.wcdemo.api.BaseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;

public abstract class CORSFlow<FLOW> extends AbstractFlow<FLOW> implements
        OutputSource {
	
    private static final Logger LOG = 
            LoggerFactory.getLogger(CORSFlow.class);
    
    @OPTIONS
    @OnEvent(event = "checkCORS")
    private BizStep onOptions() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} Options({})/{}/{}",
                    this.getClass().getSimpleName(), 
                    this, 
                    currentEventHandler().getName(), 
                    currentEvent());
        }
        final FullHttpResponse resp = 
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.ACCEPTED);
        resp.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, _acrOrigin);
        resp.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, _acrMethod);
        resp.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, _acrHeaders);
        HttpUtil.setContentLength(resp, 0);
        
        _outputReactor.outputAsHttpResponse(resp);
        return null;
    }
    
    @POST
    @OnEvent(event = "initWithPost")
    private BizStep onHttpPost() throws Exception {
        return onHttpAccept();
    }

    protected void dumpRequest(final Object req) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("{} POST({})/{}/{}, req={}",
                    this.getClass().getSimpleName(), 
                    this, 
                    currentEventHandler().getName(), 
                    currentEvent(),
                    req);
        }
    }
    
    protected abstract BizStep onHttpAccept() throws Exception;
    
//    protected void sendbackResponse(final BaseResponse resp) {
//        if (null != _outputReactor) {
//            resp.setAccessControlAllowOrigin(_acrOrigin);
//            _outputReactor.output(resp);
//            _outputReactor = null;
//        }
//    }
    
    @Override
    public void setOutputReactor(final OutputReactor reactor) throws Exception {
        this._outputReactor = reactor;
    }
    
    @HeaderParam("Access-Control-Request-Headers")
    private String _acrHeaders;
    
    @HeaderParam("Access-Control-Request-Method")
    private String _acrMethod;

    @HeaderParam("Origin")
    private String _acrOrigin;
    
    protected OutputReactor _outputReactor;
}
