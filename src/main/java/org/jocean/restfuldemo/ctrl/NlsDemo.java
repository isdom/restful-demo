package org.jocean.restfuldemo.ctrl;

import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import org.jocean.aliyun.ecs.MetadataAPI;
import org.jocean.aliyun.nls.NlsAPI;
import org.jocean.aliyun.nls.NlsAPI.AsrResponse;
import org.jocean.aliyun.nls.NlsmetaAPI;
import org.jocean.aliyun.nls.NlsmetaAPI.CreateTokenResponse;
import org.jocean.aliyun.sign.SignerV1;
import org.jocean.http.Interact;
import org.jocean.http.MessageBody;
import org.jocean.svr.annotation.RpcFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import io.netty.handler.codec.http.HttpRequest;
import rx.Observable;
import rx.Observable.Transformer;

@Path("/newrest/")
@Controller
@Scope("prototype")
public class NlsDemo {
    private static final Logger LOG = LoggerFactory.getLogger(NlsDemo.class);

    @Value("${nls.appkey}")
    String _nlsAppkey;

    Transformer<Interact, Interact> appkey() {
        return interacts -> interacts.doOnNext( interact -> interact.paramAsQuery("appkey", _nlsAppkey));
    }

    @Value("${role}")
    String _role;

    @RpcFacade
    MetadataAPI.STSTokenBuilder  getststoken;

    Transformer<Interact, Interact> alisign_sts() {
        return interacts -> getststoken.roleName(_role).call()
                .flatMap(stsresp -> interacts.doOnNext( interact -> interact.onsending(
                        SignerV1.signRequest(stsresp.getAccessKeyId(), stsresp.getAccessKeySecret(), stsresp.getSecurityToken()))));
    }

    @RpcFacade("this.alisign_sts()")
    NlsmetaAPI nlsmeta;

    @Path("nls/token")
    @GET
    public Observable<CreateTokenResponse> nlstoken() {
        return nlsmeta.createToken().call();
    }

    Transformer<Interact, Interact> applytoken() {
        return interacts -> nlstoken().map(resp -> resp.getNlsToken().getId())
                .flatMap(token -> interacts.doOnNext(interact -> interact.onrequest(obj -> {
                    if (obj instanceof HttpRequest) {
                        final HttpRequest req = (HttpRequest) obj;
                        req.headers().set("X-NLS-Token", token);
                    }
                })));
    }

    @RpcFacade({"this.applytoken()", "this.appkey()"})
    NlsAPI nlsapi;

    @Path("nls/asr")
    @OPTIONS
    @POST
    public Observable<AsrResponse> nlsasr(final Observable<MessageBody> getbody) {
        return nlsapi.streamAsrV1()
            .body(getbody.doOnNext( body -> LOG.info("nlsasr get body {} inside @RpcFacade", body)))
            .call();
    }
}
