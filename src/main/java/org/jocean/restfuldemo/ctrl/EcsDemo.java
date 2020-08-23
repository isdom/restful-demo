package org.jocean.restfuldemo.ctrl;

import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.jocean.aliyun.ecs.EcsAPI;
import org.jocean.aliyun.ecs.EcsAPI.DescribeInstanceRamRoleBuilder;
import org.jocean.aliyun.ecs.EcsAPI.DescribeInstanceStatusBuilder;
import org.jocean.aliyun.ecs.MetadataAPI;
import org.jocean.aliyun.sign.SignerV1;
import org.jocean.http.Interact;
import org.jocean.svr.annotation.RpcFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import rx.Observable;
import rx.Observable.Transformer;

@Path("/newrest/")
@Controller
@Scope("prototype")
public class EcsDemo {
    private static final Logger LOG = LoggerFactory.getLogger(EcsDemo.class);

    @RpcFacade({"aliyun.default"})
    EcsAPI ecs;

    @RpcFacade
    MetadataAPI.STSTokenBuilder  getststoken;

    @Value("${role}")
    String _role;

    Transformer<Interact, Interact> alisign_sts() {
        return interacts -> getststoken.roleName(_role).call()
                .flatMap(stsresp -> interacts.doOnNext( interact -> interact.onsending(
                        SignerV1.signRequest(stsresp.getAccessKeyId(), stsresp.getAccessKeySecret(), stsresp.getSecurityToken()))));
    }

    @Path("ecs/buy1")
    public Observable<? extends Object> buyPostPaid(
            @RpcFacade("this.alisign_sts()") final EcsAPI api,
            @QueryParam("dryRun") final boolean dryRun,
            @QueryParam("region") final String regionId,
            @QueryParam("zone") final String zoneId,
            @QueryParam("instanceType") final String instanceType,
            @QueryParam("imageId") final String imageId,
            @QueryParam("securityGroupId") final String securityGroupId,
//            @QueryParam("instanceName") final String instanceName,
//            @QueryParam("hostName") final String hostName,
//            @QueryParam("description") final String description,
            @QueryParam("vSwitchId") final String vSwitchId,
            @QueryParam("keyPairName") final String keyPairName,
            @QueryParam("ramRoleName") final String ramRoleName) {
        return api.createInstance()
                .dryRun(dryRun)
                .imageId(imageId)
                .instanceType(instanceType)
                .regionId(regionId)
                .zoneId(zoneId)
                .securityGroupId(securityGroupId)
                .internetMaxBandwidthOut(0)
//                .internetChargeType("PayByTraffic")
//                .instanceName(instanceName)
//                .hostName(hostName)
                .systemDiskSize(20)
                .systemDiskCategory("cloud_efficiency")
                .ioOptimized("optimized")
//                .description(description)
                .vSwitchId(vSwitchId)
                .useAdditionalService(true)
                .instanceChargeType("PostPaid")
                .spotStrategy("NoSpot")
                .keyPairName(keyPairName)
                .ramRoleName(ramRoleName)
                .securityEnhancementStrategy("Active")
                .call();
    }

    @Path("ecs/stopInstance")
    public Observable<? extends Object> stopInstance(
            @QueryParam("instance") final String instanceId,
            @QueryParam("force") final boolean force) {
        LOG.info("call ecs/stopInstance with instanceId:{}/force:{}", instanceId, force);
        return ecs.stopInstance().instanceId(instanceId).forceStop(force).call();
    }

    @Path("ecs/deleteInstance")
    public Observable<? extends Object> deleteInstance(
            @QueryParam("instance") final String instanceId,
            @QueryParam("force") final boolean force) {
        LOG.info("call ecs/deleteInstance with instanceId:{}/force:{}", instanceId, force);
        return ecs.deleteInstance()
                .instanceId(instanceId)
                .force(force)
                .call();
    }

    @Path("ecs/startInstance")
    public Observable<? extends Object> startInstance(
            @QueryParam("instance") final String instanceId) {
        return ecs.startInstance().instanceId(instanceId).call();
    }

    /*
    @Path("ecs/createInstance")
    public Observable<? extends Object> createInstance(
            final RpcExecutor executor,
            @QueryParam("region") final String regionId,
            @QueryParam("instanceType") final String instanceType,
            @QueryParam("imageId") final String imageId,
            @QueryParam("securityGroupId") final String securityGroupId,
            @QueryParam("instanceName") final String instanceName,
            @QueryParam("hostName") final String hostName,
            @QueryParam("description") final String description,
            @QueryParam("vSwitchId") final String vSwitchId,
            @QueryParam("spotPriceLimit") final float spotPriceLimit,
            @QueryParam("keyPairName") final String keyPairName,
            @QueryParam("ramRoleName") final String ramRoleName) {
        return _finder.find(_signer, AliyunSigner.class).flatMap(signer -> executor.execute(
                runners -> runners.doOnNext(signer),
                RpcDelegater.build(EcsAPI.class).createInstance()
                .dryRun(true)
                .imageId(imageId)
                .instanceType(instanceType)
                .regionId(regionId)
                .securityGroupId(securityGroupId)
                .internetMaxBandwidthOut(0)
//                .internetChargeType("PayByTraffic")
                .instanceName(instanceName)
                .hostName(hostName)
                .systemDiskSize(20)
                .systemDiskCategory("cloud_efficiency")
                .ioOptimized("optimized")
                .description(description)
                .vSwitchId(vSwitchId)
                .useAdditionalService(true)
                .instanceChargeType("PostPaid")
                .spotStrategy("SpotWithPriceLimit")
                .spotPriceLimit(spotPriceLimit)
                .keyPairName(keyPairName)
                .ramRoleName(ramRoleName)
                .securityEnhancementStrategy("Active")
                .call() ));
    }
    */

    @Path("ecs/describeSpotPriceHistory")
    public Observable<? extends Object> describeSpotPriceHistory(
            @QueryParam("region") final String regionId,
            @QueryParam("instanceType") final String instanceType
            ) {
        return ecs.describeSpotPriceHistory()
                    .regionId(regionId)
                    .instanceType(instanceType)
                    .networkType("vpc")
                    .call();
    }

    @Path("ecs/describeInstances")
    public Observable<? extends Object> ecsDescribeInstances(
            @QueryParam("region") final String regionId,
            @QueryParam("vpc") final String vpcId,
            @QueryParam("instancename") final String instanceName
            ) {
        return ecs.describeInstances()
                    .regionId(regionId)
                    .vpcId(vpcId)
                    .instanceName(instanceName)
                    .call();
    }

    @Path("ecs/describeInstanceStatus")
    public Observable<? extends Object> ecsDescribeInstanceStatus(
            @QueryParam("region") final String regionId,
            @QueryParam("pageidx") final String pageidx,
            @QueryParam("pagesize") final String pagesize
            ) {

        final DescribeInstanceStatusBuilder builder = ecs.describeInstanceStatus().regionId(regionId);
        if (null != pageidx && null != pagesize) {
            builder.pageNumber( Integer.parseInt(pageidx));
            builder.pageSize(Integer.parseInt(pagesize));
        }

        return builder.call();
    }

    @Path("ecs/describeUserData")
    public Observable<? extends Object> ecsDescribeUserData(
            @QueryParam("instance") final String instance,
            @QueryParam("region") final String regionId) {
        return ecs.describeUserData()
                    .instanceId(instance)
                    .regionId(regionId)
                    .call();
    }

    @Path("ecs/describeInstanceRamRole")
    public Observable<? extends Object> ecsDescribeInstanceRamRole(
            @QueryParam("region") final String regionId,
            @QueryParam("instances") final String instances,
            @QueryParam("ramrole") final String ramrole,
            @QueryParam("pageidx") final String pageidx,
            @QueryParam("pagesize") final String pagesize
            ) {
        final DescribeInstanceRamRoleBuilder builder = ecs.describeInstanceRamRole().regionId(regionId);

        if (null != instances) {
            builder.instanceIds(instances);
        }

        if (null != ramrole) {
            builder.ramRoleName(ramrole);
        }

        if (null != pageidx) {
            builder.pageNumber(Integer.parseInt(pageidx));
        }

        if (null != pagesize) {
            builder.pageSize(Integer.parseInt(pagesize));
        }

        return builder.call();
    }
}