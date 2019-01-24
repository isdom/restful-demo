package org.jocean.restfuldemo.uds;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerDomainSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultThreadFactory;

public class UDSDemo {

    static final EventLoopGroup EPOLL_BOSS_GROUP =
            new EpollEventLoopGroup(1, new DefaultThreadFactory("epoll-boss", false));

    static final EventLoopGroup EPOLL_WORKER_GROUP =
            new EpollEventLoopGroup(1, new DefaultThreadFactory("epoll-worker", false));

    Channel _bindChannel;

    public void start() throws InterruptedException {
        final EchoServerHandler serverHandler = new EchoServerHandler();

        final ServerBootstrap sb = new ServerBootstrap().group(EPOLL_BOSS_GROUP, EPOLL_WORKER_GROUP)
                        .channel(EpollServerDomainSocketChannel.class)
                        .option(ChannelOption.SO_BACKLOG, 100)
                        .handler(new LoggingHandler(LogLevel.INFO))
                        .childHandler(new ChannelInitializer<Channel>() {
                            @Override
                            protected void initChannel(final Channel channel) throws Exception {
                                final ChannelPipeline p = channel.pipeline();
                                p.addLast(new LoggingHandler(LogLevel.INFO));
                                p.addLast(serverHandler);
                            }});
        final DomainSocketAddress localAddress = new DomainSocketAddress("./demo.socket");

        final ChannelFuture future = sb.bind(localAddress);
        future.sync();

        _bindChannel = future.channel();
    }

    public void stop() throws InterruptedException {
        if (_bindChannel != null) {
            _bindChannel.close().sync();
        }
    }
}
