package ua.krupet;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.*;

/**
 * Created by krupet on 21.04.2015.
 */

/*
    causes exception if calls default constructor: init() must be called before placed to the pipeline.
    //public class DuplexStatisticsHandler extends CombinedChannelDuplexHandler {
 */

public class DuplexStatisticsHandler extends ChannelDuplexHandler {

    private static final String DB_DRIVER = "com.mysql.jdbc.Driver";
    private static final String DB_CONNECTION = "jdbc:mysql://localhost:3306/nettydb";
    private static final String DB_USER = "javamanager";
    private static final String DB_PASSWORD = "javamanager";

    private String requestIp;
    private Timestamp requestTime;

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
            if (uri.contains("redirect")) {
                redirect = true;
            }
            createRequestRecord(ipAddress, uri, timestamp, redirect);

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
//            System.out.println(resSize);
            System.out.println("\n\n" + msg.toString());
        }
        super.write(ctx, msg, promise);
    }

    private void createRequestRecord(String ipAddress, String uri, Timestamp timestamp, boolean redirect) {
        Connection dbConnection = null;
        PreparedStatement preparedStatement = null;

        String createTableSQL = "INSERT INTO connections (ip, uri, timestamp, redirect) VALUES (\'"
                + ipAddress + "\', \'" + uri + "\', \'" + timestamp + "\', \'" + redirect + "\');";

        try {
            dbConnection = getDBConnection();
            preparedStatement = dbConnection.prepareStatement(createTableSQL);

            preparedStatement.executeUpdate();

        } catch (SQLException e) {

            System.out.println(e.getMessage());

        } finally {
            try {
                if (preparedStatement != null)
                    preparedStatement.close();
            } catch (SQLException se2) {
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

            System.out.println(e.getMessage());

        }

        try {

            dbConnection = DriverManager.getConnection(
                    DB_CONNECTION, DB_USER, DB_PASSWORD);
            return dbConnection;

        } catch (SQLException e) {

            System.out.println(e.getMessage());

        }

        return dbConnection;
    }
}
