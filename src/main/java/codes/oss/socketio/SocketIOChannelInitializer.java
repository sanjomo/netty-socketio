/**
 * Copyright (c) 2012-2023 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package codes.oss.socketio;

import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import codes.oss.socketio.ack.AckManager;
import codes.oss.socketio.handler.AuthorizeHandler;
import codes.oss.socketio.handler.ClientHead;
import codes.oss.socketio.handler.ClientsBox;
import codes.oss.socketio.handler.EncoderHandler;
import codes.oss.socketio.handler.InPacketHandler;
import codes.oss.socketio.handler.PacketListener;
import codes.oss.socketio.handler.WrongUrlHandler;
import codes.oss.socketio.namespace.NamespacesHub;
import codes.oss.socketio.protocol.JsonSupport;
import codes.oss.socketio.protocol.PacketDecoder;
import codes.oss.socketio.protocol.PacketEncoder;
import codes.oss.socketio.scheduler.CancelableScheduler;
import codes.oss.socketio.scheduler.HashedWheelTimeoutScheduler;
import codes.oss.socketio.store.StoreFactory;
import codes.oss.socketio.store.pubsub.DisconnectMessage;
import codes.oss.socketio.store.pubsub.PubSubType;
import codes.oss.socketio.transport.PollingTransport;
import codes.oss.socketio.transport.WebSocketTransport;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.ssl.SslHandler;

public class SocketIOChannelInitializer extends ChannelInitializer<Channel> implements DisconnectableHub {

    public static final String SOCKETIO_ENCODER = "socketioEncoder";
    public static final String WEB_SOCKET_TRANSPORT_COMPRESSION = "webSocketTransportCompression";
    public static final String WEB_SOCKET_TRANSPORT = "webSocketTransport";
    public static final String WEB_SOCKET_AGGREGATOR = "webSocketAggregator";
    public static final String XHR_POLLING_TRANSPORT = "xhrPollingTransport";
    public static final String AUTHORIZE_HANDLER = "authorizeHandler";
    public static final String PACKET_HANDLER = "packetHandler";
    public static final String HTTP_ENCODER = "httpEncoder";
    public static final String HTTP_COMPRESSION = "httpCompression";
    public static final String HTTP_AGGREGATOR = "httpAggregator";
    public static final String HTTP_REQUEST_DECODER = "httpDecoder";
    public static final String SSL_HANDLER = "ssl";

    public static final String RESOURCE_HANDLER = "resourceHandler";
    public static final String WRONG_URL_HANDLER = "wrongUrlBlocker";

    private static final Logger log = LoggerFactory.getLogger(SocketIOChannelInitializer.class);

    private AckManager ackManager;

    private ClientsBox clientsBox = new ClientsBox();
    private AuthorizeHandler authorizeHandler;
    private PollingTransport xhrPollingTransport;
    private WebSocketTransport webSocketTransport;
    private EncoderHandler encoderHandler;
    private WrongUrlHandler wrongUrlHandler;

    private CancelableScheduler scheduler = new HashedWheelTimeoutScheduler();

    private InPacketHandler packetHandler;
    private SSLContext sslContext;
    private Configuration configuration;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        scheduler.update(ctx);
    }

    public void start(Configuration configuration, NamespacesHub namespacesHub) {
        this.configuration = configuration;

        ackManager = new AckManager(scheduler);

        JsonSupport jsonSupport = configuration.getJsonSupport();
        PacketEncoder encoder = new PacketEncoder(configuration, jsonSupport);
        PacketDecoder decoder = new PacketDecoder(jsonSupport, ackManager);

        String connectPath = configuration.getContext() + "/";

        boolean isSsl = configuration.getKeyStore() != null;
        if (isSsl) {
            try {
                sslContext = createSSLContext(configuration);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        StoreFactory factory = configuration.getStoreFactory();
        authorizeHandler = new AuthorizeHandler(connectPath, scheduler, configuration, namespacesHub, factory, this, ackManager, clientsBox);
        factory.init(namespacesHub, authorizeHandler, jsonSupport);
        xhrPollingTransport = new PollingTransport(decoder, authorizeHandler, clientsBox);
        webSocketTransport = new WebSocketTransport(isSsl, authorizeHandler, configuration, scheduler, clientsBox);

        PacketListener packetListener = new PacketListener(ackManager, namespacesHub, xhrPollingTransport, scheduler);


        packetHandler = new InPacketHandler(packetListener, decoder, namespacesHub, configuration.getExceptionListener());

        try {
            encoderHandler = new EncoderHandler(configuration, encoder);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        wrongUrlHandler = new WrongUrlHandler();
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        addSslHandler(pipeline);
        addSocketioHandlers(pipeline);
    }

    /**
     * Adds the ssl handler
     *
     * @param pipeline - channel pipeline
     */
    protected void addSslHandler(ChannelPipeline pipeline) {
        if (sslContext != null) {
            SSLEngine engine = sslContext.createSSLEngine();
            engine.setUseClientMode(false);
            if (configuration.isNeedClientAuth() &&(configuration.getTrustStore() != null)) {
                engine.setNeedClientAuth(true);
            }
            pipeline.addLast(SSL_HANDLER, new SslHandler(engine));
        }
    }

    /**
     * Adds the socketio channel handlers
     *
     * @param pipeline - channel pipeline
     */
    protected void addSocketioHandlers(ChannelPipeline pipeline) {
        pipeline.addLast(HTTP_REQUEST_DECODER, new HttpRequestDecoder(configuration.getHttpDecoderConfig()));
        pipeline.addLast(HTTP_AGGREGATOR, new HttpObjectAggregator(configuration.getMaxHttpContentLength()) {
            @Override
            protected Object newContinueResponse(HttpMessage start, int maxContentLength,
                    ChannelPipeline pipeline) {
                return null;
            }

        });
        pipeline.addLast(HTTP_ENCODER, new HttpResponseEncoder());

        if (configuration.isHttpCompression()) {
            pipeline.addLast(HTTP_COMPRESSION, new HttpContentCompressor());
        }

        pipeline.addLast(PACKET_HANDLER, packetHandler);

        pipeline.addLast(AUTHORIZE_HANDLER, authorizeHandler);
        pipeline.addLast(XHR_POLLING_TRANSPORT, xhrPollingTransport);
        if (configuration.isWebsocketCompression()) {
            pipeline.addLast(WEB_SOCKET_TRANSPORT_COMPRESSION, new WebSocketServerCompressionHandler());
        }
        pipeline.addLast(WEB_SOCKET_TRANSPORT, webSocketTransport);

        pipeline.addLast(SOCKETIO_ENCODER, encoderHandler);

        pipeline.addLast(WRONG_URL_HANDLER, wrongUrlHandler);
    }

    private SSLContext createSSLContext(Configuration configuration) throws Exception {
        TrustManager[] managers = null;
        if (configuration.getTrustStore() != null) {
            KeyStore ts = KeyStore.getInstance(configuration.getTrustStoreFormat());
            ts.load(configuration.getTrustStore(), configuration.getTrustStorePassword().toCharArray());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);
            managers = tmf.getTrustManagers();
        }

        KeyStore ks = KeyStore.getInstance(configuration.getKeyStoreFormat());
        ks.load(configuration.getKeyStore(), configuration.getKeyStorePassword().toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(configuration.getKeyManagerFactoryAlgorithm());
        kmf.init(ks, configuration.getKeyStorePassword().toCharArray());

        SSLContext serverContext = SSLContext.getInstance(configuration.getSSLProtocol());
        serverContext.init(kmf.getKeyManagers(), managers, null);
        return serverContext;
    }

    @Override
    public void onDisconnect(ClientHead client) {
        ackManager.onDisconnect(client);
        authorizeHandler.onDisconnect(client);
        configuration.getStoreFactory().onDisconnect(client);

        configuration.getStoreFactory().pubSubStore().publish(PubSubType.DISCONNECT, new DisconnectMessage(client.getSessionId()));

        log.debug("Client with sessionId: {} disconnected", client.getSessionId());
    }

    public void stop() {
        StoreFactory factory = configuration.getStoreFactory();
        factory.shutdown();
        scheduler.shutdown();
    }

}
