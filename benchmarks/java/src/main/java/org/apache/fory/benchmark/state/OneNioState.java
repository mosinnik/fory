/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fory.benchmark.state;

import java.io.IOException;
import one.nio.serial.Serializer;
import org.apache.fory.benchmark.IntsSerializationSuite;
import org.apache.fory.benchmark.LongStringSerializationSuite;
import org.apache.fory.benchmark.LongsSerializationSuite;
import org.apache.fory.benchmark.StringSerializationSuite;
import org.apache.fory.benchmark.data.Data;
import org.apache.fory.logging.Logger;
import org.apache.fory.logging.LoggerFactory;
import org.apache.fory.util.Preconditions;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 3)
@Measurement(iterations = 3)
@Fork(value = 0)
@CompilerControl(value = CompilerControl.Mode.INLINE)
public class OneNioState {
  private static final Logger LOG = LoggerFactory.getLogger(OneNioState.class);

  public static void main(String[] args) {
    OneNioUserTypeState userTypeState = new OneNioUserTypeState();
    userTypeState.objectType = ObjectType.SAMPLE;
    userTypeState.references = false;
    userTypeState.bufferType = BufferType.array;
    userTypeState.setup();
  }

  public static byte[] serialize(Object o) {
    try {
      return Serializer.serialize(o);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Object deserialize(byte[] data) {
    try {
      return Serializer.deserialize(data);
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @State(Scope.Thread)
  public abstract static class OneNioBenchmarkState extends BenchmarkState {
    public byte[] serializedBytes;

    @Setup(Level.Trial)
    public void setup() {}
  }

  public static class OneNioUserTypeState extends OneNioBenchmarkState {
    @Param() public ObjectType objectType;

    public Object object;
    public int serializedLength;

    @Override
    public void setup() {
      super.setup();
      object = ObjectType.createObject(objectType, references);
      serializedBytes = serialize(object);
      serializedLength = serializedBytes.length;
      LOG.info(
          "======> OneNio | {} | {} | {} | {} |",
          objectType,
          references,
          bufferType,
          serializedLength);
      Object o2 = deserialize(serializedBytes);
      Preconditions.checkArgument(object.equals(o2));
    }
  }

  public static class DataState extends OneNioBenchmarkState {
    public Data data = new Data();
  }

  public static class ReadIntsState extends DataState {
    @Override
    public void setup() {
      super.setup();
      serializedBytes =
          new IntsSerializationSuite().onennio_serializeInts(this);
    }
  }

  public static class ReadLongsState extends DataState {
    @Override
    public void setup() {
      super.setup();
      serializedBytes =
          new LongsSerializationSuite().onennio_serializeLongs(this);
    }
  }

  public static class ReadStrState extends DataState {
    @Override
    public void setup() {
      super.setup();
      serializedBytes =
          new StringSerializationSuite().onennio_serializeStr(this);
    }
  }

  public static class ReadLongStrState extends DataState {
    @Override
    public void setup() {
      super.setup();
      serializedBytes =
          new LongStringSerializationSuite().onennio_serializeLongStr(this);
    }
  }
}
