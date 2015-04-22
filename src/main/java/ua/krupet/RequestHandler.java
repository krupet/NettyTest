package ua.krupet;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.apache.commons.validator.routines.UrlValidator;

import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Created by krupet on 20.04.2015.
 */
public class RequestHandler extends SimpleChannelInboundHandler<Object> {

    private HttpRequest request;

    /** Buffer that stores the response content */
    private final StringBuilder buf = new StringBuilder();

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HttpRequest) {
            HttpRequest request = this.request = (HttpRequest) msg;
            if (request.getUri().contains("redirect?url=")){

                sendRedirect(ctx);

            } else {
                if (request.getUri().contains("hello")){
                    buf.setLength(0);
                    buf.append("HELLO WORLD!\r\n");

                /*
                    BAD IDEA, BUT WORKING - causes troubles with multi threading!
                    I don't invent something better yet
                */
                    try {
                        Thread.sleep(10000L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    sendResponsePlainText(ctx, OK);
                } else {
                    buf.setLength(0);
                    buf.append("This type of request is not supported!\r\n");
                    buf.append("Please check requested URL or query string!\r\n");

                    sendResponsePlainText(ctx, NOT_IMPLEMENTED);
                }

                sendResponsePlainText(ctx, OK);
            }
        }
    }

    private void sendResponsePlainText(ChannelHandlerContext ctx, HttpResponseStatus notImplemented) {
        boolean keepAlive = HttpHeaders.isKeepAlive(request);

        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, notImplemented,
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


    private void sendRedirect(ChannelHandlerContext ctx) {
        String redirectUrl = null;
        UrlValidator urlValidator = new UrlValidator();

        /*
            this is overhead but i will fix it!
            i suppose here would be only one url.
         */
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.getUri());
        Map<String, List<String>> params = queryStringDecoder.parameters();

        if (!params.isEmpty()) {
            List<String> vals = params.get("url");
            if (!vals.isEmpty()) {
                redirectUrl = vals.get(0);
            }
        }

        if (!urlValidator.isValid(redirectUrl)) {
            buf.setLength(0);
            buf.append("Please check requested URL - it is not valid!\r\n");
            sendResponsePlainText(ctx, BAD_REQUEST);
        }

        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, MOVED_PERMANENTLY);
        response.headers().set(LOCATION, redirectUrl);
        response.headers().set(CONTENT_LENGTH, response.content().readableBytes());

        boolean keepAlive = HttpHeaders.isKeepAlive(request);

        if (!keepAlive) {
            ctx.write(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
            ctx.write(response);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        buf.setLength(0);
        buf.append("Internal server error!\r\n");
        buf.append(cause.getMessage()).append("\r\n");
        sendResponsePlainText(ctx, INTERNAL_SERVER_ERROR);
    }
}
