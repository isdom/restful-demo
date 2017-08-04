/**
 *
 */
package org.jocean.restfuldemo.bll;

import javax.inject.Inject;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.jocean.event.api.AbstractFlow;
import org.jocean.event.api.BizStep;
import org.jocean.event.api.EventUtils;
import org.jocean.event.api.annotation.OnEvent;
import org.jocean.http.rosa.SignalClient;
import org.jocean.idiom.ExceptionUtils;
import org.jocean.restful.OutputReactor;
import org.jocean.restful.OutputSource;
import org.jocean.restfuldemo.bean.DemoRequest;
import org.jocean.restfuldemo.bean.DemoResponse;
import org.jocean.restfuldemo.bean.outbound.OutboundRequest;
import org.jocean.restfuldemo.bean.outbound.OutboundResponse;
import org.jocean.restfuldemo.service.DemoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


//@Path("/welcome/{roomno}")
@Path("/welcome/simple")
public class DemoFlow extends AbstractFlow<DemoFlow> implements
        OutputSource {
	
    private static final Logger LOG = 
            LoggerFactory.getLogger(DemoFlow.class);

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
//        final DemoResponse response = new DemoResponse();
//        response.setMessage("room(" + _roomno + ")/your ip is :" + _peerip + "/" 
//                + _service.getAction() + ":" + _request.getName() + "/" );
//        
//        if (null != _outputReactor) {
//            _outputReactor.output(response);
//        }
//        return null;
        return this.onHttpAccept();
    }

    private BizStep onHttpAccept() throws Exception {

        final OutboundRequest outbound = new OutboundRequest();
        
        this._signalClient.interaction().request(outbound).<OutboundResponse>build()
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
            response.setMessage("room(" + _roomno + "):" 
                    + _service.getAction() + ":" + _request.getName() + "/" + e.toString());
            
            if (null != _outputReactor) {
                _outputReactor.output(response);
            }
            setEndReason("failure.outbound_error");
            return null;
        }
        
        @OnEvent(event = "onOutboundResponse")
        private BizStep onOutboundResponse(final OutboundResponse outresponse) 
                throws Exception {
            LOG.info("onOutboundResponse {}", outresponse);
            
            final DemoResponse response = new DemoResponse();
            response.setMessage("room(" + _roomno + ")/your ip is :" + _peerip + "/" 
                    + _service.getAction() + ":" + _request.getName() + "/" + outresponse.toString());
            
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
    
    @HeaderParam("X-Forwarded-For")
    private String _peerip;
    
    @BeanParam
    private DemoRequest _request;
    
    @PathParam("roomno")
    private String _roomno;
    
    private OutputReactor _outputReactor;
    
    @Inject
    private SignalClient _signalClient;

    @Inject
    private DemoService _service;
}
