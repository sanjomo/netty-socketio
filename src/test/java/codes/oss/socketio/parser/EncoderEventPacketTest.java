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
package codes.oss.socketio.parser;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import codes.oss.socketio.protocol.Packet;
import codes.oss.socketio.protocol.PacketType;

public class EncoderEventPacketTest extends EncoderBaseTest {

    @Test
    public void testEncode() throws IOException {
        Packet packet = new Packet(PacketType.EVENT);
        packet.setName("woot");
        ByteBuf result = Unpooled.buffer();
//        encoder.encodePacket(packet, result);
        Assert.assertEquals("5:::{\"name\":\"woot\"}", result.toString(CharsetUtil.UTF_8));
    }

    @Test
    public void testEncodeWithMessageIdAndAck() throws IOException {
        Packet packet = new Packet(PacketType.EVENT);
//        packet.setId(1L);
//        packet.setAck(Packet.ACK_DATA);
        packet.setName("tobi");
        ByteBuf result = Unpooled.buffer();
//        encoder.encodePacket(packet, result);
        Assert.assertEquals("5:1+::{\"name\":\"tobi\"}", result.toString(CharsetUtil.UTF_8));
    }

    @Test
    public void testEncodeWithData() throws IOException {
        Packet packet = new Packet(PacketType.EVENT);
        packet.setName("edwald");
//        packet.setArgs(Arrays.asList(Collections.singletonMap("a", "b"), 2, "3"));
        ByteBuf result = Unpooled.buffer();
//        encoder.encodePacket(packet, result);
        Assert.assertEquals("5:::{\"name\":\"edwald\",\"args\":[{\"a\":\"b\"},2,\"3\"]}",
                                    result.toString(CharsetUtil.UTF_8));
    }

}
