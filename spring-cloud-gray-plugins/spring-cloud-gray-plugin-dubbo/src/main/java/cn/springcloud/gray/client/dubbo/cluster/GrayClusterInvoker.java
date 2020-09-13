package cn.springcloud.gray.client.dubbo.cluster;

import cn.springcloud.gray.GrayClientHolder;
import cn.springcloud.gray.client.dubbo.GrayDubboHolder;
import cn.springcloud.gray.client.dubbo.configuration.properties.GrayDubboProperties;
import cn.springcloud.gray.client.dubbo.constants.GrayDubboConstants;
import cn.springcloud.gray.client.dubbo.servernode.InvokerExplainHelper;
import cn.springcloud.gray.commons.GrayRequestHelper;
import cn.springcloud.gray.request.GrayRequest;
import cn.springcloud.gray.routing.connectionpoint.RoutingConnectPointContext;
import cn.springcloud.gray.routing.connectionpoint.RoutingConnectionPoint;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.ClusterInvoker;
import org.apache.dubbo.rpc.cluster.Directory;
import org.apache.dubbo.rpc.service.GenericService;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author saleson
 * @date 2020-09-12 00:02
 */
public class GrayClusterInvoker<T> implements ClusterInvoker<T> {

    private final Directory<T> directory;

    private final Invoker<T> invoker;

    private Set<String> ignoreGrayServiceNames = new HashSet<>();


    public GrayClusterInvoker(Directory<T> directory, Invoker<T> invoker) {
        this.directory = directory;
        this.invoker = invoker;
        initIgnoreGrayServiceNames();
    }

    @Override
    public URL getRegistryUrl() {
        return directory.getUrl();
    }

    @Override
    public Directory<T> getDirectory() {
        return directory;
    }

    @Override
    public Class<T> getInterface() {
        return directory.getInterface();
    }

    @Override
    public Result invoke(Invocation invocation) throws RpcException {
        RoutingConnectionPoint routingConnectionPoint = GrayClientHolder.getRoutingConnectionPoint();
        if (ignoreGrayscale(invocation) || Objects.isNull(routingConnectionPoint)) {
            return this.invoker.invoke(invocation);
        }

        GrayRequest grayRequest = new GrayRequest();
        grayRequest.setServiceId(InvokerExplainHelper.getServiceId(invoker));
        grayRequest.setAttachment(GrayDubboConstants.INVOKE_INVOCATION, invocation);

        GrayRequestHelper.setPreviousServerInfoByInstanceLocalInfo(grayRequest);

        RoutingConnectPointContext connectPointContext = RoutingConnectPointContext.builder()
                .interceptroType(GrayDubboConstants.INTERCEPTRO_TYPE_DUBBO)
                .grayRequest(grayRequest).build();


        return routingConnectionPoint.execute(connectPointContext, () -> invoker.invoke(invocation));
    }

    @Override
    public URL getUrl() {
        return directory.getConsumerUrl();
    }

    @Override
    public boolean isAvailable() {
        return directory.isAvailable();
    }

    @Override
    public void destroy() {
        invoker.destroy();
    }


    protected boolean ignoreGrayscale(Invocation invocation) {
        String serviceName = invocation.getServiceName();
        if (ignoreGrayServiceNames.contains(serviceName)) {
            return true;
        }

        GrayDubboProperties grayDubboProperties = GrayDubboHolder.getGrayDubboProperties();
        if (Objects.isNull(grayDubboProperties)) {
            return false;
        }
        if (grayDubboProperties.getIgnoreGrayServiceNames().contains(serviceName)) {
            return true;
        }
        return false;
    }

    protected void initIgnoreGrayServiceNames() {
        ignoreGrayServiceNames.add(GenericService.class.getName());
    }


    public Set<String> getIgnoreGrayServiceNames() {
        return ignoreGrayServiceNames;
    }
}