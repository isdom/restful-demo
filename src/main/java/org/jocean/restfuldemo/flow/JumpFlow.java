/**
 *
 */
package org.jocean.restfuldemo.flow;

import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.jocean.event.api.AbstractFlow;
import org.jocean.event.api.BizStep;
import org.jocean.event.api.annotation.OnEvent;
import org.jocean.restful.OutputReactor;
import org.jocean.restful.OutputSource;
import org.jocean.restfuldemo.bean.DemoRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;


@Path("/welcome/jump")
public class JumpFlow extends AbstractFlow<JumpFlow> implements
        OutputSource {
	
    private static final Logger LOG = 
            LoggerFactory.getLogger(JumpFlow.class);

    @GET
    @OnEvent(event = "initWithGet")
    private BizStep onHttpGet() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("DemoFlow Get({})/{}/{}, QueryParams: req={}",
                    this, currentEventHandler().getName(), currentEvent(),
                    this._request);
        }
        setEndReason("success.get");
        return this.onHttpAccept();
    }

    @POST
    @OnEvent(event = "initWithPost")
    private BizStep onHttpPost() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "DemoFlow POST({})/{}/{}, QueryParams: req={}",
                    this, currentEventHandler().getName(), currentEvent(),
                    this._request);
        }
        setEndReason("success.post");
        return this.onHttpAccept();
    }

    private BizStep onHttpAccept() throws Exception {
        if (_ua.contains("MicroMessenger")) {
            final FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND, Unpooled.buffer(0));

            // Add 'Content-Length' header only for a keep-alive connection.
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
            response.headers().set(HttpHeaderNames.LOCATION, this._wechatRedirecturi);
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_STORE);
            response.headers().set(HttpHeaderNames.PRAGMA, HttpHeaderValues.NO_CACHE);
            this._outputReactor.outputAsHttpResponse(response);
        } else if (_ua.contains("AlipayClient")) {
            final FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND, Unpooled.buffer(0));

            // Add 'Content-Length' header only for a keep-alive connection.
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
            response.headers().set(HttpHeaderNames.LOCATION, this._alipayRedirecturi);
            response.headers().set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_STORE);
            response.headers().set(HttpHeaderNames.PRAGMA, HttpHeaderValues.NO_CACHE);
            this._outputReactor.outputAsHttpResponse(response);
        } else {
            this._outputReactor.output(null);
        }
        
        return null;
    }
    
    public void setWechatRedirecturi(String redirecturi) {
        this._wechatRedirecturi = redirecturi;
    }
    
    public void setAlipayRedirecturi(String redirecturi) {
        this._alipayRedirecturi = redirecturi;
    }
    
    @Override
    public void setOutputReactor(final OutputReactor reactor) throws Exception {
        this._outputReactor = reactor;
    }
    
    @HeaderParam("X-Forwarded-For")
    private String _peerip;
    
    @HeaderParam("User-Agent")
    private String _ua;
    
    @BeanParam
    private DemoRequest _request;
    
    private String _wechatRedirecturi;
    private String _alipayRedirecturi;
    
    private OutputReactor _outputReactor;
}
