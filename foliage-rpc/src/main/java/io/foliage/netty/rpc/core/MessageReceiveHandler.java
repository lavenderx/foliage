package io.foliage.netty.rpc.core;

import io.foliage.netty.rpc.protocol.MessageRequest;
import io.foliage.netty.rpc.protocol.MessageResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.Map;

public class MessageReceiveHandler extends ChannelInboundHandlerAdapter {

    private final Map<String, Object> handlerMap;

    public MessageReceiveHandler(Map<String, Object> handlerMap) {
        this.handlerMap = handlerMap;
    }

    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        MessageRequest request = (MessageRequest) msg;
        MessageResponse response = new MessageResponse();
        MessageReceiveInitializeTask recvTask = new MessageReceiveInitializeTask(request, response, handlerMap);
        //不要阻塞nio线程，复杂的业务逻辑丢给专门的线程池
        MessageReceiveExecutor.submit(recvTask, ctx, request, response);
    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        //网络有异常要关闭通道
        ctx.close();
    }
}
