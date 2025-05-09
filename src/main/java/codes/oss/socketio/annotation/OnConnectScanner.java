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
package codes.oss.socketio.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import codes.oss.socketio.SocketIOClient;
import codes.oss.socketio.handler.SocketIOException;
import codes.oss.socketio.listener.ConnectListener;
import codes.oss.socketio.namespace.Namespace;

public class OnConnectScanner implements AnnotationScanner  {

    @Override
    public Class<? extends Annotation> getScanAnnotation() {
        return OnConnect.class;
    }

    @Override
    public void addListener(Namespace namespace, final Object object, final Method method, Annotation annotation) {
        namespace.addConnectListener(new ConnectListener() {
            @Override
            public void onConnect(SocketIOClient client) {
                try {
                    method.invoke(object, client);
                } catch (InvocationTargetException e) {
                    throw new SocketIOException(e.getCause());
                } catch (Exception e) {
                    throw new SocketIOException(e);
                }
            }
        });
    }

    @Override
    public void validate(Method method, Class<?> clazz) {
        if (method.getParameterTypes().length != 1) {
            throw new IllegalArgumentException("Wrong OnConnect listener signature: " + clazz + "." + method.getName());
        }

        for (Class<?> eventType : method.getParameterTypes()) {
			if (SocketIOClient.class.equals(eventType)) {
                return;
			}
        }

        throw new IllegalArgumentException("Wrong OnConnect listener signature: " + clazz + "." + method.getName());
    }

}
