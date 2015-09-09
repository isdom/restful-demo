/**
 *
 */
package org.jocean.restfuldemo.flow;

import javax.inject.Inject;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.jocean.event.api.AbstractFlow;
import org.jocean.event.api.BizStep;
import org.jocean.event.api.EventUtils;
import org.jocean.event.api.annotation.OnEvent;
import org.jocean.http.rosa.SignalClient;
import org.jocean.http.util.RxNettys;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.restful.OutputReactor;
import org.jocean.restful.OutputSource;
import org.jocean.restfuldemo.bean.DemoRequest;
import org.jocean.restfuldemo.bean.DemoResponse;
import org.jocean.restfuldemo.bean.outbound.OutboundRequest;
import org.jocean.restfuldemo.bean.outbound.OutboundResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Path("/welcome")
public class DemoFlow extends AbstractFlow<DemoFlow> implements
        OutputSource {
	
    private static final Logger LOG = 
            LoggerFactory.getLogger(DemoFlow.class);

    @GET
    @OnEvent(event = "initWithGet")
    private BizStep onHttpGet() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    "DemoFlow Get({})/{}/{}, QueryParams: req={}",
                    this, currentEventHandler().getName(), currentEvent(),
                    this._request);
        }
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
        return this.onHttpAccept();
    }

    private BizStep onHttpAccept() throws Exception {

        final OutboundRequest outbound = new OutboundRequest();
        
        this._signalClient.defineInteraction(outbound)
        .compose(RxNettys.<OutboundResponse>filterProgress())
        .subscribe(EventUtils.receiver2observer(
            selfEventReceiver(),
            "onOutboundResponse",
            "onOutboundError"));
        
        return WAIT4OUTAPI;
    }
    
    final BizStep WAIT4OUTAPI = new BizStep("out.wait") {
        @OnEvent(event = "onOutboundError")
        private BizStep onOutboundError(final Throwable e) throws Exception {
            LOG.warn("exception when onOutboundError, detail:{}", 
                    ExceptionUtils.exception2detail(e));
            final DemoResponse response = new DemoResponse();
            response.setMessage(_action + ":" + _request.getName() + "/" + e.toString());
            
            if (null != _outputReactor) {
                _outputReactor.output(response);
            }
            return null;
        }
        
        @OnEvent(event = "onOutboundResponse")
        private BizStep onOutboundResponse(final OutboundResponse outresponse) 
                throws Exception {
            LOG.warn("onOutboundResponse {}", outresponse);
            
            final DemoResponse response = new DemoResponse();
            response.setMessage(_action + ":" + _request.getName() + "/" + outresponse.toString());
            
            if (null != _outputReactor) {
                _outputReactor.output(response);
            }
            return null;
        }
    }
    .freeze();
    
    @Override
    public void setOutputReactor(final OutputReactor reactor) throws Exception {
        this._outputReactor = reactor;
    }
    
    public void setAction(final String action) {
        this._action = action;
    }
    
    @BeanParam
    private DemoRequest _request;
    
    private String _action;

    private OutputReactor _outputReactor;
    
    @Inject
    private SignalClient _signalClient;
}
