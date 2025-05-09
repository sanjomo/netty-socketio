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

import codes.oss.socketio.misc.IterableCollection;
import codes.oss.socketio.protocol.EngineIOVersion;
import codes.oss.socketio.protocol.Packet;
import codes.oss.socketio.protocol.PacketType;
import codes.oss.socketio.store.StoreFactory;
import codes.oss.socketio.store.pubsub.DispatchMessage;
import codes.oss.socketio.store.pubsub.PubSubType;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Author: liangjiaqi
 * Date: 2020/8/8 6:08 PM
 */
public class SingleRoomBroadcastOperations implements BroadcastOperations {
    private final String namespace;
    private final String room;
    private final Iterable<SocketIOClient> clients;
    private final StoreFactory storeFactory;

    public SingleRoomBroadcastOperations(String namespace, String room, Iterable<SocketIOClient> clients, StoreFactory storeFactory) {
        super();
        this.namespace = namespace;
        this.room = room;
        this.clients = clients;
        this.storeFactory = storeFactory;
    }

    private void dispatch(Packet packet) {
        this.storeFactory.pubSubStore().publish(
                PubSubType.DISPATCH,
                new DispatchMessage(this.room, packet, this.namespace));
    }

    @Override
    public Collection<SocketIOClient> getClients() {
        return new IterableCollection<SocketIOClient>(clients);
    }

    @Override
    public void send(Packet packet) {
        for (SocketIOClient client : clients) {
            packet.setEngineIOVersion(client.getEngineIOVersion());
            client.send(packet);
        }
        dispatch(packet);
    }

    @Override
    public <T> void send(Packet packet, BroadcastAckCallback<T> ackCallback) {
        for (SocketIOClient client : clients) {
            client.send(packet, ackCallback.createClientCallback(client));
        }
        ackCallback.loopFinished();
    }

    @Override
    public void disconnect() {
        for (SocketIOClient client : clients) {
            client.disconnect();
        }
    }

    @Override
    public void sendEvent(String name, SocketIOClient excludedClient, Object... data) {
		Predicate<SocketIOClient> excludePredicate = (socketIOClient) -> Objects.equals(
				socketIOClient.getSessionId(), excludedClient.getSessionId()
		);
        sendEvent(name, excludePredicate, data);
    }

    @Override
    public void sendEvent(String name, Predicate<SocketIOClient> excludePredicate, Object... data) {
        Packet packet = new Packet(PacketType.MESSAGE, EngineIOVersion.UNKNOWN);
        packet.setSubType(PacketType.EVENT);
        packet.setName(name);
        packet.setData(Arrays.asList(data));

        for (SocketIOClient client : clients) {
            packet.setEngineIOVersion(client.getEngineIOVersion());
            if (excludePredicate.test(client)) {
                continue;
            }
            client.send(packet);
        }
        dispatch(packet);
    }

    @Override
    public void sendEvent(String name, Object... data) {
        Packet packet = new Packet(PacketType.MESSAGE, EngineIOVersion.UNKNOWN);
        packet.setSubType(PacketType.EVENT);
        packet.setName(name);
        packet.setData(Arrays.asList(data));
        send(packet);
    }

    @Override
    public <T> void sendEvent(String name, Object data, BroadcastAckCallback<T> ackCallback) {
        for (SocketIOClient client : clients) {
            client.sendEvent(name, ackCallback.createClientCallback(client), data);
        }
        ackCallback.loopFinished();
    }

    @Override
    public <T> void sendEvent(String name, Object data, SocketIOClient excludedClient, BroadcastAckCallback<T> ackCallback) {
		Predicate<SocketIOClient> excludePredicate = (socketIOClient) -> Objects.equals(
				socketIOClient.getSessionId(), excludedClient.getSessionId()
		);
		sendEvent(name, data, excludePredicate, ackCallback);
    }

	@Override
	public <T> void sendEvent(String name, Object data, Predicate<SocketIOClient> excludePredicate, BroadcastAckCallback<T> ackCallback) {
		for (SocketIOClient client : clients) {
			if (excludePredicate.test(client)) {
				continue;
			}
			client.sendEvent(name, ackCallback.createClientCallback(client), data);
		}
		ackCallback.loopFinished();
	}
}
