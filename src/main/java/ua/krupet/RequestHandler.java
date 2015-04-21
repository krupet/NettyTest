package ua.krupet;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaders.Names.*;
import static io.netty.handler.codec.http.HttpResponseStatus.MOVED_PERMANENTLY;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
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
            if (request.getUri().contains("redirect")){

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

                    sendHelloWorld(ctx);
                } else {
                    buf.setLength(0);
                    buf.append("WRONG NEIGHBORHOOD, SUGAR!\r\n");

//                    TODO: bad request or not supported!
                }

                if (!sendHelloWorld(ctx)) {
                    // If keep-alive is off, close the connection once the content is fully written.
                    ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                }
            }
        }
    }

    private void sendRedirect(ChannelHandlerContext ctx) {
        String redirectUrl = null;

        /*
            this is overhead but i will fix it!
            i suppose here would be only one url.
         */
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.getUri());
        Map<String, List<String>> params = queryStringDecoder.parameters();

        if (!params.isEmpty()) {
            List<String> vals = params.get("url");
            redirectUrl = vals.get(0);
//            TODO: bad request if redirectUrl is not valid!
//            for (String val : vals) {
//                redirectUrl = val;
//            }
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

    private boolean sendHelloWorld(ChannelHandlerContext ctx) {
        boolean keepAlive = HttpHeaders.isKeepAlive(request);

        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK,
                Unpooled.copiedBuffer(buf.toString(), CharsetUtil.UTF_8));

        response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");

        if (keepAlive) {
            response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
            response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
        }

        ctx.write(response);
        return keepAlive;
    }
}
