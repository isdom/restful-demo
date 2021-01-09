/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.jocean.restfuldemo.wslog;

import java.util.Locale;

import org.jocean.j2se.logback.BytesAppender;
import org.jocean.j2se.logback.OutputBytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.AttributeKey;

/**
 * Echoes uppercase content of text frames.
 */
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketFrameHandler.class);

    private static AttributeKey<OutputBytes> OUTPUT = AttributeKey.valueOf("LOG");

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);

        disableOutput(ctx);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx, final WebSocketFrame frame) throws Exception {
        // ping and pong frames already handled
        enableOutput(ctx);

        if (frame instanceof TextWebSocketFrame) {
            // Send the uppercase string back.
            final String request = ((TextWebSocketFrame) frame).text();
            ctx.channel().writeAndFlush(new TextWebSocketFrame(request.toUpperCase(Locale.US)));
        } else {
            final String message = "unsupported frame type: " + frame.getClass().getName();
            throw new UnsupportedOperationException(message);
        }
    }

    private boolean isOutputEnabled(final ChannelHandlerContext ctx) {
        return null != ctx.channel().attr(OUTPUT).get();
    }

    private void enableOutput(final ChannelHandlerContext ctx) {
        if (!isOutputEnabled(ctx)) {
            final OutputBytes output = bytes -> {
                if (null != bytes && ctx.channel().isActive()) {
                    ctx.channel().writeAndFlush(new TextWebSocketFrame(new String(bytes)));
//                    ctx.write(ctx.alloc().buffer(bytes.length).writeBytes(bytes));
//                    ctx.flush();
                }
            };
            ctx.channel().attr(OUTPUT).set(output);

            BytesAppender.addToRoot(ctx.channel().id().toString(), output);

            LOG.info("append WSAppender instance named:{}", ctx.channel().id().toString());
        }
    }

    private void disableOutput(final ChannelHandlerContext ctx) {
        final OutputBytes output = ctx.channel().attr(OUTPUT).get();
        if (null != output) {
            LOG.info("detach WSAppender instance named:{}", ctx.channel().id().toString());
            BytesAppender.detachFromRoot(ctx.channel().id().toString());
        }
    }
}