/**
 *
 */
package org.jocean.restfuldemo.bll;

import java.util.Map;

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
            LOG.debug("JumpFlow Get({})/{}/{}, QueryParams: req={}",
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
        final FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND, Unpooled.buffer(0));

        // Add 'Content-Length' header only for a keep-alive connection.
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        response.headers().set(HttpHeaderNames.LOCATION, this._defaultRedirecturi);
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_STORE);
        response.headers().set(HttpHeaderNames.PRAGMA, HttpHeaderValues.NO_CACHE);
        for (Map.Entry<String, String> entry : _ua2redirecturi.entrySet()) {
            if ( _ua.contains(entry.getKey()) ) {
                response.headers().set(HttpHeaderNames.LOCATION, entry.getValue());
                break;
            }
        }
        this._outputReactor.outputAsHttpResponse(response);
        return null;
    }
    
    public void setDefaultRedirecturi(String redirecturi) {
        this._defaultRedirecturi = redirecturi;
    }
    
    public void setUa2uri(final Map<String, String> ua2redirecturi) {
        this._ua2redirecturi = ua2redirecturi;
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
    
    private String _defaultRedirecturi;
    
    private Map<String, String> _ua2redirecturi;
    
    private OutputReactor _outputReactor;
}
