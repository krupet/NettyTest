package ua.krupet;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.apache.commons.validator.routines.UrlValidator;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.List;
import java.util.Map;

/**
 * Created by krupet on 21.04.2015.
 */

/*
    causes exception if calls default constructor: init() must be called before placed to the pipeline.
    //public class DuplexStatisticsHandler extends CombinedChannelDuplexHandler {
 */

public class DuplexStatisticsHandler extends ChannelDuplexHandler {

    /*
        Defining a thread safe Set of all connected channels
        TODO: need to think how to pass it properly via constructor from ServerInitializer
        and have access from other handlers
        but this is seems as overhead - i could just create some class with volatile
        counter field and pass it to other handlers from ServerInitializer via constructor???
     */
    static final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    public static AttributeKey<Long> CONN_NUMBER = AttributeKey.valueOf("CONN_NUMBER");

    private static final String DB_DRIVER = "com.mysql.jdbc.Driver";
    private static final String DB_CONNECTION = "jdbc:mysql://localhost:3306/nettydb";
    private static final String DB_USER = "javamanager";
    private static final String DB_PASSWORD = "javamanager";

    private String requestIp;
    private Timestamp requestTime;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        channels.add(ctx.channel());
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;

            /*
                getting remote IP
            */
            InetSocketAddress socketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
            InetAddress inetaddress = socketAddress.getAddress();
            String ipAddress = requestIp = inetaddress.getHostAddress();

            /*
                getting uri of request
            */
            String uri = request.getUri();
            /*
            getting current time
            */
            long time = System.currentTimeMillis();
            Timestamp timestamp = requestTime = new java.sql.Timestamp(time);

            /*
                getting request size (max is 1024 if not using aggregator???)
                need to add HttpRequestAggregator and use FullHttpRequest
                need to be tested!
            */
            byte[] byteReq = (msg.toString()).getBytes("UTF-8");
            long reqSize = byteReq.length;

            /*
                here i would decide redirect or not
            */
            boolean redirect = false;
            String redirectUrl = null;
            UrlValidator urlValidator = new UrlValidator();
            if (uri.contains("redirect?url=")) {
                QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.getUri());
                Map<String, List<String>> params = queryStringDecoder.parameters();

                if (!params.isEmpty()) {
                    List<String> vals = params.get("url");
                    if (!vals.isEmpty()) {
                        redirectUrl = vals.get(0);
                    }
                }

                if (!urlValidator.isValid(redirectUrl)) {
                    redirect = true;
                }
            }
            createRequestRecord(ipAddress, uri, timestamp, redirect, reqSize);

            /*
                Getting number of current connections
             */
            long numberOfCurrentConnections = channels.size();
            /*
                pass this value to other handlers via channel
            */
            ctx.channel().attr(CONN_NUMBER).set(numberOfCurrentConnections);

            /*
                Sending message to the next handler
            */
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof FullHttpResponse) {
            /*
                Getting the response size
             */
            byte[] byteReq = (msg.toString()).getBytes("UTF-8");
            long resSize = byteReq.length;
        }
        super.write(ctx, msg, promise);
    }

    private void createRequestRecord(String ipAddress, String uri, Timestamp timestamp, boolean redirect, long reqSize) {

        String createRequestRecordQuery = "INSERT INTO connections1 (ip, uri, timestamp, redirect, get_bytes) VALUES (\'"
                + ipAddress + "\', \'" + uri + "\', \'" + timestamp + "\', \'" + redirect + "\', \'" + reqSize + "\');";

        insertIntoTable(createRequestRecordQuery);
    }

    private void insertIntoTable(String query) {
        Connection dbConnection = null;
        PreparedStatement preparedStatement = null;

        try {
            dbConnection = getDBConnection();
            preparedStatement = dbConnection.prepareStatement(query);

            preparedStatement.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (preparedStatement != null)
                    preparedStatement.close();
            } catch (SQLException se2) {
                se2.printStackTrace();
            }
            try {
                if (dbConnection != null)
                    dbConnection.close();
            } catch (SQLException se) {
                se.printStackTrace();
            }
        }
    }

    private static Connection getDBConnection() {
        Connection dbConnection = null;

        try {

            Class.forName(DB_DRIVER);

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        try {
            dbConnection = DriverManager.getConnection(
                    DB_CONNECTION, DB_USER, DB_PASSWORD);
            return dbConnection;

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return dbConnection;
    }
}
