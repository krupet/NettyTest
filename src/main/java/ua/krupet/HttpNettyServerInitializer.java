package ua.krupet;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import org.apache.commons.dbcp2.BasicDataSource;

import javax.sql.DataSource;

/**
 * Created by krupet on 20.04.2015.
 */
public class HttpNettyServerInitializer extends ChannelInitializer<SocketChannel> {


    private static final String DB_DRIVER = "com.mysql.jdbc.Driver";
    private static final String DB_CONNECTION = "jdbc:mysql://localhost:3306/nettydb";
    private static final String DB_USER = "javadev";
    private static final String DB_PASSWORD = "javadev";

    @Override
    public void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();

        p.addLast("counter", new ChannelSpeedCheckHandler(1000L));
        p.addLast("decoder", new HttpRequestDecoder());
        p.addLast(new HttpObjectAggregator(1048576));
        p.addLast("encoder", new HttpResponseEncoder());
        p.addLast("duplex statistic handler",new DuplexStatisticsHandler(setupDataSource()));
//        p.addLast("duplex statistic handler",new DuplexStatisticsHandler());
        p.addLast("request handler",new RequestHandler());
    }

    public static DataSource setupDataSource() {

        BasicDataSource ds = new BasicDataSource();
        ds.setDriverClassName(DB_DRIVER);
        ds.setUrl(DB_CONNECTION);
        ds.setUsername(DB_USER);
        ds.setPassword(DB_PASSWORD);

        return ds;
    }
}
