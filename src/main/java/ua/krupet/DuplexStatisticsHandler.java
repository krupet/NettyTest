package ua.krupet;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.*;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.apache.commons.validator.routines.UrlValidator;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.*;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

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

    private HttpRequest httpRequest;
    private final StringBuilder buf = new StringBuilder();

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
            HttpRequest request = httpRequest = (HttpRequest) msg;

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

                if (urlValidator.isValid(redirectUrl)) {
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


            if (request.getUri().contains("status")){

                buf.setLength(0);
                buf.append("STATISTICS!:\r\n\n");
                buf.append("===================================\r\n");
                /*
                    Getting number of all requests
                 */
                String totalAmountOfRequestQuery = "SELECT COUNT(*) FROM connections1";
                long totalAmountOfRequests = getTotalAmountRequest(totalAmountOfRequestQuery);
                buf.append("Total amount of all requests: ").append(totalAmountOfRequests).append(";\r\n");

                /*
                    Getting number of unique requests (1 unique request by 1 ip)
                 */
                String numberOfUniqueRequestsQuery = "SELECT COUNT(DISTINCT uri) FROM connections1;";
                long numberOfUniqueRequests = getTotalAmountRequest(numberOfUniqueRequestsQuery);
                buf.append("Total amount of unique requests: ").append(numberOfUniqueRequests).append(";\r\n\n");

                /*
                    Counter of requests on every IP as a table
                 */
                String ipStatistics = "SELECT ip, COUNT(DISTINCT uri), MAX(TIMESTAMP(timestamp)) FROM connections1 GROUP BY ip;";
                buf.append("Count of requests on every IP:\r\n");
                buf.append("ip").append("\t").append("count").append("\t").append("time").append(";\r\n");
                buf.append("===================================\r\n");
                getIpStatistics(ipStatistics);
                buf.append("===================================\r\n\n\n");

                /*
                    Number of redirects by the uri as a table
                 */
                String numberOfRedirects = "SELECT uri, COUNT(uri) FROM connections1 WHERE redirect = \'true\' GROUP BY uri;";
                buf.append("Number of redirects by the uri:\r\n");
                buf.append("uri").append("\t").append("count").append(";\r\n");
                buf.append("===================================\r\n");
                getRedirects(numberOfRedirects);
                buf.append("===================================\r\n\n\n");

                /*
                    Getting number of connected channels ie users
                 */
                long numberOfOpenedConnections = channels.size();
                buf.append("Number of connected channels: ").append(numberOfOpenedConnections).append(";\r\n\n\n");

                /*
                    Getting log of last 16 requests as a table
                 */
                String log = "SELECT * FROM connections1 ORDER BY timestamp DESC LIMIT 16;";
                buf.append("Log of last 16 requests:\r\n");
                buf.append("id").append("\t")
                        .append("ip").append("\t")
                        .append("uri").append("\t")
                        .append("time").append("\t")
                        .append("redirect").append("\t")
                        .append("getByes").append("\t")
                        .append("sentByes").append(";\r\n");
                buf.append("===================================\r\n");
                getLog(log);
                buf.append("===================================\r\n");

                sendResponsePlainText(ctx, OK);

            } else {
                /*
                    Sending message to the next handler
                */
                ctx.fireChannelRead(msg);
            }
        }
    }

    private void sendResponsePlainText(ChannelHandlerContext ctx, HttpResponseStatus status) {
        boolean keepAlive = HttpHeaders.isKeepAlive(httpRequest);

        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status,
                Unpooled.copiedBuffer(buf.toString(), CharsetUtil.UTF_8));

        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");

        if (!keepAlive) {
            ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        } else {
            response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
            response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }

        ctx.write(response);
    }

    private void getLog(String query) {
        Connection dbConnection = null;
        PreparedStatement preparedStatement = null;

        try {
            dbConnection = getDBConnection();
            preparedStatement = dbConnection.prepareStatement(query);

            ResultSet resultSet = preparedStatement.executeQuery();
            while(resultSet.next()){
                long id = resultSet.getLong(1);
                String ip = resultSet.getString(2);
                String uri = resultSet.getString(3);
                String time = resultSet.getString(4);
                String redirect = resultSet.getString(5);
                long getByes = resultSet.getLong(6);
                long sentByes = resultSet.getLong(7);
                buf.append(id).append("\t")
                        .append(ip).append("\t")
                        .append(uri).append("\t")
                        .append(time).append("\t")
                        .append(redirect).append("\t")
                        .append(getByes).append("\t")
                        .append(sentByes).append(";\r\n");
            }

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

    private void getRedirects(String query) {
        Connection dbConnection = null;
        PreparedStatement preparedStatement = null;

        try {
            dbConnection = getDBConnection();
            preparedStatement = dbConnection.prepareStatement(query);

            ResultSet resultSet = preparedStatement.executeQuery();
            while(resultSet.next()){
                String uri = resultSet.getString(1);
                long count = resultSet.getLong(2);
                buf.append(uri).append("\t").append(count).append(";\r\n");
            }

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

    private void getIpStatistics(String query) {
        Connection dbConnection = null;
        PreparedStatement preparedStatement = null;

        try {
            dbConnection = getDBConnection();
            preparedStatement = dbConnection.prepareStatement(query);

            ResultSet resultSet = preparedStatement.executeQuery();
            while(resultSet.next()){
                String ip = resultSet.getString(1);
                long count = resultSet.getLong(2);
                String time = resultSet.getString(3);
                buf.append(ip).append("\t").append(count).append("\t").append(time).append(";\r\n");
            }

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

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof FullHttpResponse) {
            /*
                Getting the response size
             */
            byte[] byteReq = (msg.toString()).getBytes("UTF-8");
            long resSize = byteReq.length;

            String addResponseSizeQuery = "UPDATE connections1 SET sent_bytes = \'" + resSize +
                    "\' WHERE ip = \'" + requestIp + "\' AND timestamp = \'" + requestTime + "\';";

            insertIntoTable(addResponseSizeQuery);
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

    private long getTotalAmountRequest(String query) {
        long totalAmount = 0L;
        Connection dbConnection = null;
        PreparedStatement preparedStatement = null;

        try {
            dbConnection = getDBConnection();
            preparedStatement = dbConnection.prepareStatement(query);

            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                totalAmount = resultSet.getLong(1);
            } else {
                System.out.println("error: could not get the record counts");
            }

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

        return totalAmount;
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }
}
