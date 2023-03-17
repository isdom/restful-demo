package org.jocean.restfuldemo.ctrl;

import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.svr.annotation.RpcFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;


import gdt.session.SessionSPI;
import rx.Observable;


@Path("/newrest/")
@Controller
@Scope("prototype")
public class GdtDemo {
    private static final Logger LOG = LoggerFactory.getLogger(GdtDemo.class);

    @RpcFacade
    SessionSPI sessionspi;
    
    @Path("gdt/sid")
    public Observable<String> ask(@QueryParam("sid") final String sid) {
    	LOG.info("sid: {}", sid);
    	
    	return sessionspi.getBootRequest().sessionid(sid).call()
    			.doOnNext(req -> LOG.info("host: {}", req.getHost()))
    			.doOnNext(req -> LOG.info("uri: {}", req.getUri()))
    			.map(bootreq -> bootreq.toString());
    }
}
