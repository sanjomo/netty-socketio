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

import codes.oss.socketio.listener.*;
import codes.oss.socketio.listener.*;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.uring.IoUring;
import io.netty.channel.uring.IoUringIoHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import codes.oss.socketio.namespace.Namespace;
import codes.oss.socketio.namespace.NamespacesHub;

/**
 * Fully thread-safe.
 *
 */
public class SocketIOServer implements ClientListeners {

    private static final Logger log = LoggerFactory.getLogger(SocketIOServer.class);

    private final Configuration configCopy;
    private final Configuration configuration;

    private final NamespacesHub namespacesHub;
    private final SocketIONamespace mainNamespace;

    private SocketIOChannelInitializer pipelineFactory = new SocketIOChannelInitializer();

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public SocketIOServer(Configuration configuration) {
        this.configuration = configuration;
        this.configCopy = new Configuration(configuration);
        namespacesHub = new NamespacesHub(configCopy);
        mainNamespace = addNamespace(Namespace.DEFAULT_NAME);
    }

    public void setPipelineFactory(SocketIOChannelInitializer pipelineFactory) {
        this.pipelineFactory = pipelineFactory;
    }

    /**
     * Get all clients connected to default namespace
     *
     * @return clients collection
     */
    public Collection<SocketIOClient> getAllClients() {
        return namespacesHub.get(Namespace.DEFAULT_NAME).getAllClients();
    }

    /**
     * Get client by uuid from default namespace
     *
     * @param uuid - id of client
     * @return client
     */
    public SocketIOClient getClient(UUID uuid) {
        return namespacesHub.get(Namespace.DEFAULT_NAME).getClient(uuid);
    }

    /**
     * Get all namespaces
     *
     * @return namespaces collection
     */
    public Collection<SocketIONamespace> getAllNamespaces() {
        return namespacesHub.getAllNamespaces();
    }

    public BroadcastOperations getBroadcastOperations() {
        Collection<SocketIONamespace> namespaces = namespacesHub.getAllNamespaces();
        List<BroadcastOperations> list = new ArrayList<>();
        BroadcastOperations broadcast = null;
        if( namespaces != null && !namespaces.isEmpty()) {
            for( SocketIONamespace n : namespaces ) {
                broadcast = n.getBroadcastOperations();
                list.add( broadcast );
            }
        }
        return new MultiRoomBroadcastOperations( list );
    }

    /**
     * Get broadcast operations for clients within
     * rooms by <code>rooms'</code> names
     *
     * @param rooms rooms' names
     * @return broadcast operations
     */
    public BroadcastOperations getRoomOperations(String... rooms) {
        Collection<SocketIONamespace> namespaces = namespacesHub.getAllNamespaces();
        List<BroadcastOperations> list = new ArrayList<>();
        BroadcastOperations broadcast = null;
        if( namespaces != null && !namespaces.isEmpty()) {
            for( SocketIONamespace n : namespaces ) {
				for ( String room : rooms ) {
                    broadcast = n.getRoomOperations( room );
                    list.add( broadcast );
                }
			}
        }
        return new MultiRoomBroadcastOperations( list );
    }

    /**
     * Start server
     */
    public void start() {
        startAsync().syncUninterruptibly();
    }

    /**
     * Start server asynchronously
     * 
     * @return void
     */
    public Future<Void> startAsync() {
        log.info("Session store / pubsub factory used: {}", configCopy.getStoreFactory());
        initGroups();

        pipelineFactory.start(configCopy, namespacesHub);

        Class<? extends ServerChannel> channelClass = NioServerSocketChannel.class;
        if (configCopy.isUseLinuxNativeEpoll()) {
            channelClass = EpollServerSocketChannel.class;
        }

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
        .channel(channelClass)
        .childHandler(pipelineFactory);
        applyConnectionOptions(b);

        InetSocketAddress addr = new InetSocketAddress(configCopy.getPort());
        if (configCopy.getHostname() != null) {
            addr = new InetSocketAddress(configCopy.getHostname(), configCopy.getPort());
        }

        return b.bind(addr).addListener((FutureListener<Void>) future -> {
            if (future.isSuccess()) {
                log.info("SocketIO server started at port: {}", configCopy.getPort());
            } else {
                log.error("SocketIO server start failed at port: {}!", configCopy.getPort());
            }
        });
    }

    protected void applyConnectionOptions(ServerBootstrap bootstrap) {
        SocketConfig config = configCopy.getSocketConfig();
        bootstrap.childOption(ChannelOption.TCP_NODELAY, config.isTcpNoDelay());
        if (config.getTcpSendBufferSize() != -1) {
            bootstrap.childOption(ChannelOption.SO_SNDBUF, config.getTcpSendBufferSize());
        }
        if (config.getTcpReceiveBufferSize() != -1) {
            bootstrap.childOption(ChannelOption.SO_RCVBUF, config.getTcpReceiveBufferSize());
            bootstrap.childOption(ChannelOption.RCVBUF_ALLOCATOR, new FixedRecvByteBufAllocator(config.getTcpReceiveBufferSize()));
        }
        //default value @see WriteBufferWaterMark.DEFAULT
        if (config.getWriteBufferWaterMarkLow() != -1 && config.getWriteBufferWaterMarkHigh() != -1) {
            bootstrap.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(
                    config.getWriteBufferWaterMarkLow(), config.getWriteBufferWaterMarkHigh()
            ));
        }

        bootstrap.childOption(ChannelOption.SO_KEEPALIVE, config.isTcpKeepAlive());
        bootstrap.childOption(ChannelOption.SO_LINGER, config.getSoLinger());

        bootstrap.option(ChannelOption.SO_REUSEADDR, config.isReuseAddress());
        bootstrap.option(ChannelOption.SO_BACKLOG, config.getAcceptBackLog());
    }

    protected void initGroups() {
        if(configCopy.isUseLinuxNativeIOUring() && IoUring.isAvailable()){
            bossGroup = new MultiThreadIoEventLoopGroup(configCopy.getBossThreads(), IoUringIoHandler.newFactory());
            workerGroup = new MultiThreadIoEventLoopGroup(configCopy.getWorkerThreads(), IoUringIoHandler.newFactory());
        }
        else if (configCopy.isUseLinuxNativeEpoll() && Epoll.isAvailable()) {
            bossGroup = new MultiThreadIoEventLoopGroup(configCopy.getBossThreads(), EpollIoHandler.newFactory());
            workerGroup = new MultiThreadIoEventLoopGroup(configCopy.getWorkerThreads(), EpollIoHandler.newFactory());
        } else {
            bossGroup = new MultiThreadIoEventLoopGroup(configCopy.getBossThreads(), NioIoHandler.newFactory());
            workerGroup = new MultiThreadIoEventLoopGroup(configCopy.getWorkerThreads(), NioIoHandler.newFactory());
        }
    }

    /**
     * Stop server
     */
    public void stop() {
        bossGroup.shutdownGracefully().syncUninterruptibly();
        workerGroup.shutdownGracefully().syncUninterruptibly();

        pipelineFactory.stop();
        log.info("SocketIO server stopped");
    }

    public SocketIONamespace addNamespace(String name) {
        return namespacesHub.create(name);
    }

    public SocketIONamespace getNamespace(String name) {
        return namespacesHub.get(name);
    }

    public void removeNamespace(String name) {
        namespacesHub.remove(name);
    }

    /**
     * Allows to get configuration provided
     * during server creation. Further changes on
     * this object not affect server.
     *
     * @return Configuration object
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public void addMultiTypeEventListener(String eventName, MultiTypeEventListener listener, Class<?>... eventClass) {
        mainNamespace.addMultiTypeEventListener(eventName, listener, eventClass);
    }

    @Override
    public <T> void addEventListener(String eventName, Class<T> eventClass, DataListener<T> listener) {
        mainNamespace.addEventListener(eventName, eventClass, listener);
    }

    @Override
    public void addEventInterceptor(EventInterceptor eventInterceptor) {
        mainNamespace.addEventInterceptor(eventInterceptor);

    }


    @Override
    public void removeAllListeners(String eventName) {
        mainNamespace.removeAllListeners(eventName);
    }

    @Override
    public void addDisconnectListener(DisconnectListener listener) {
        mainNamespace.addDisconnectListener(listener);
    }

    @Override
    public void addConnectListener(ConnectListener listener) {
        mainNamespace.addConnectListener(listener);
    }

    @Override
    public void addPingListener(PingListener listener) {
        mainNamespace.addPingListener(listener);
    }
    @Override
    public void addPongListener(PongListener listener) {
        mainNamespace.addPongListener(listener);
    }

    @Override
    public void addListeners(Object listeners) {
        mainNamespace.addListeners(listeners);
    }

    @Override
    public <L> void addListeners(Iterable<L> listeners) {
        mainNamespace.addListeners(listeners);
    }

    @Override
    public void addListeners(Object listeners, Class<?> listenersClass) {
        mainNamespace.addListeners(listeners, listenersClass);
    }


}
