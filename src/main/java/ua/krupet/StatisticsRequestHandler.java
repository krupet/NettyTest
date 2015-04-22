package ua.krupet;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.AttributeKey;

import java.sql.*;

/**
 * Created by krupet on 22.04.2015.
 */
public class StatisticsRequestHandler extends SimpleChannelInboundHandler<Object> {

    public static AttributeKey<Long> CONN_NUMBER = AttributeKey.valueOf("CONN_NUMBER");

    private final StringBuilder buf = new StringBuilder();

    private static final String DB_DRIVER = "com.mysql.jdbc.Driver";
    private static final String DB_CONNECTION = "jdbc:mysql://localhost:3306/nettydb";
    private static final String DB_USER = "javamanager";
    private static final String DB_PASSWORD = "javamanager";

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            if (request.getUri().contains("status")){
                long totalAmountOfRequests = getTotalAmountOfRequest();
                System.out.println(totalAmountOfRequests);

                long numberOfUniqueRequests = getnumberOfUniqueRequests();
            }

            ctx.fireChannelRead(msg);
        }
    }

    private long getnumberOfUniqueRequests() {
        return 0;
    }

    private long getTotalAmountOfRequest() {
        long totalAmount = 0L;
        Connection dbConnection = null;
        PreparedStatement preparedStatement = null;

        String TotalAmountOfRequestQuery = "SELECT COUNT(*) FROM connections1";

        try {
            dbConnection = getDBConnection();
            preparedStatement = dbConnection.prepareStatement(TotalAmountOfRequestQuery);

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
