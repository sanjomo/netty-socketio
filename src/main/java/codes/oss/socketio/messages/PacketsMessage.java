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
package codes.oss.socketio.messages;

import io.netty.buffer.ByteBuf;

import codes.oss.socketio.Transport;
import codes.oss.socketio.handler.ClientHead;

public class PacketsMessage {

    private final ClientHead client;
    private final ByteBuf content;
    private final Transport transport;

    public PacketsMessage(ClientHead client, ByteBuf content, Transport transport) {
        this.client = client;
        this.content = content;
        this.transport = transport;
    }

    public Transport getTransport() {
        return transport;
    }

    public ClientHead getClient() {
        return client;
    }

    public ByteBuf getContent() {
        return content;
    }

}
