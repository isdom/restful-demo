package org.jocean.restfuldemo.ctrl;

import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.aliyun.ecs.MetadataAPI;
import org.jocean.svr.UntilRequestCompleted;
import org.jocean.svr.annotation.RpcFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import rx.Observable;

@Path("/newrest/")
@Controller
@Scope("prototype")
public class MetadataDemo {
    private static final Logger LOG = LoggerFactory.getLogger(MetadataDemo.class);

    @Path("meta/privateipv4")
    public Observable<String> private_ipv4(final UntilRequestCompleted<String> urc) {
        return metaapi.privateIpv4().call().compose(urc);
    }

    @Path("meta/hostname")
    public Observable<String> hostname(final UntilRequestCompleted<String> urc) {
        return metaapi.hostname().call().compose(urc);
    }

    @Path("meta/instance")
    public Observable<String> instance(final UntilRequestCompleted<String> urc) {
        return metaapi.instanceId().call().compose(urc);
    }

    @Path("meta/region")
    public Observable<String> region(final UntilRequestCompleted<String> urc) {
        return metaapi.regionId().call().compose(urc);
    }

    @Path("meta/ststoken")
    public Observable<Object> ststoken(
            @QueryParam("role") final String roleName,
            final UntilRequestCompleted<Object> urc) {
        return metaapi.getSTSToken().roleName(roleName).call().compose(urc);
    }

    @RpcFacade
    MetadataAPI metaapi;
}
