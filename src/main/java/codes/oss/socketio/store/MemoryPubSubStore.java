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
package codes.oss.socketio.store;

import codes.oss.socketio.store.pubsub.PubSubListener;
import codes.oss.socketio.store.pubsub.PubSubMessage;
import codes.oss.socketio.store.pubsub.PubSubStore;
import codes.oss.socketio.store.pubsub.PubSubType;

public class MemoryPubSubStore implements PubSubStore {

    @Override
    public void publish(PubSubType type, PubSubMessage msg) {
    }

    @Override
    public <T extends PubSubMessage> void subscribe(PubSubType type, PubSubListener<T> listener, Class<T> clazz) {
    }

    @Override
    public void unsubscribe(PubSubType type) {
    }

    @Override
    public void shutdown() {
    }

}
