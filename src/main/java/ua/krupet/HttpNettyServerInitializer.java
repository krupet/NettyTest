package ua.krupet;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;

/**
 * Created by krupet on 20.04.2015.
 */
public class HttpNettyServerInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();

        p.addLast("counter", new ChannelSpeedCheckHandler(1000L));
        p.addLast("decoder", new HttpRequestDecoder());
        p.addLast(new HttpObjectAggregator(1048576));
        p.addLast("encoder", new HttpResponseEncoder());
        p.addLast("duplex statistic handler",new DuplexStatisticsHandler());
        p.addLast("request handler",new RequestHandler());
    }
}
