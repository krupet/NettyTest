package ua.krupet;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.handler.traffic.TrafficCounter;
import io.netty.util.AttributeKey;

/**
 * Created by krupet on 21.04.2015.
 */
public class ChannelSpeedCheckHandler extends ChannelTrafficShapingHandler {

    public static AttributeKey<Long> CONN_SPEED = AttributeKey.valueOf("CONN_SPEED");

    private long throughput;

    public ChannelSpeedCheckHandler(long checkInterval) {
        super(checkInterval);
    }

    @Override
    protected void doAccounting(TrafficCounter counter) {
        throughput = counter.lastReadThroughput();
        counter.stop();
        System.out.println("throughput: " + throughput);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ctx.channel().attr(CONN_SPEED).set(throughput);
        System.out.println("throughput: " + throughput);
        byte[] byteReq = (msg.toString()).getBytes("UTF-8");
        long resSize = byteReq.length;
        System.out.println("msg: " + resSize);
        super.channelRead(ctx, msg);
    }
}
