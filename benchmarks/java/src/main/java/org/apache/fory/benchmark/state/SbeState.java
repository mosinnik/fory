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

import java.nio.ByteBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.fory.benchmark.data.Image;
import org.apache.fory.benchmark.data.Media;
import org.apache.fory.benchmark.data.MediaContent;
import org.apache.fory.benchmark.data.Sample;
import org.apache.fory.benchmark.state.generated.sbe.ImageSize;
import org.apache.fory.benchmark.state.generated.sbe.MediaContentDecoder;
import org.apache.fory.benchmark.state.generated.sbe.MediaContentEncoder;
import org.apache.fory.benchmark.state.generated.sbe.MessageHeaderDecoder;
import org.apache.fory.benchmark.state.generated.sbe.MessageHeaderEncoder;
import org.apache.fory.benchmark.state.generated.sbe.SampleDecoder;
import org.apache.fory.benchmark.state.generated.sbe.SampleEncoder;
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
@Fork(value = 0, jvmArgsAppend = "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED")
@CompilerControl(value = CompilerControl.Mode.INLINE)
public class SbeState {
  private static final Logger LOG = LoggerFactory.getLogger(SbeState.class);

  public static void main(String[] args) {
    SbeUserTypeState state = new SbeUserTypeState();
    state.objectType = ObjectType.SAMPLE;
    state.bufferType = BufferType.array;
    state.setup();
  }

  @State(Scope.Thread)
  public abstract static class SbeBenchmarkState extends BenchmarkState {
    public UnsafeBuffer buffer;

    @Setup(Level.Trial)
    public void setup() {
      buffer = new UnsafeBuffer(ByteBuffer.allocate(1024 * 512));
    }
  }

  public static class SbeUserTypeState extends SbeBenchmarkState {
    @Param({"SAMPLE", "MEDIA_CONTENT"})
    public ObjectType objectType;

    public Object object;
    public int serializedLength;
    public byte[] serializedBytes;

    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final SampleEncoder sampleEncoder = new SampleEncoder();
    private final SampleDecoder sampleDecoder = new SampleDecoder();
    private final MediaContentEncoder mediaContentEncoder = new MediaContentEncoder();
    private final MediaContentDecoder mediaContentDecoder = new MediaContentDecoder();

    @Override
    public void setup() {
      super.setup();
      object = ObjectType.createObject(objectType, false);

      switch (objectType) {
        case SAMPLE:
          serializeSample((Sample) object);
          break;
        case MEDIA_CONTENT:
          serializeMediaContent((MediaContent) object);
          break;
        default:
          throw new UnsupportedOperationException(
              String.format("SBE does not support object type %s", objectType));
      }

      serializedLength = bufferIndex;
      serializedBytes = new byte[serializedLength];
      buffer.getBytes(0, serializedBytes);
      LOG.info(
          "======> SBE | {} | {} | {} | {} |",
          objectType,
          bufferType,
          buffer.capacity(),
          serializedLength);

      if (objectType == ObjectType.SAMPLE) {
        Object deserialized = deserializeSample(serializedBytes);
        Preconditions.checkArgument(object.equals(deserialized));
      }
    }

    private int bufferIndex;

    public byte[] serializeSample(Sample sample) {
      bufferIndex = 0;
      sampleEncoder.wrapAndApplyHeader(buffer, bufferIndex, messageHeaderEncoder);
      bufferIndex += messageHeaderEncoder.encodedLength();

      sampleEncoder
          .intValue(sample.intValue)
          .longValue(sample.longValue)
          .floatValue(sample.floatValue)
          .doubleValue(sample.doubleValue)
          .shortValue(sample.shortValue)
          .charValue((byte) sample.charValue)
          .booleanValue((short) (sample.booleanValue ? 1 : 0))
          .intValueBoxed(sample.intValueBoxed == null ? 0 : sample.intValueBoxed)
          .longValueBoxed(sample.longValueBoxed == null ? 0 : sample.longValueBoxed)
          .floatValueBoxed(sample.floatValueBoxed == null ? 0 : sample.floatValueBoxed)
          .doubleValueBoxed(sample.doubleValueBoxed == null ? 0 : sample.doubleValueBoxed)
          .shortValueBoxed(sample.shortValueBoxed == null ? 0 : sample.shortValueBoxed)
          .charValueBoxed(sample.charValueBoxed == null ? 0 : (byte) sample.charValueBoxed.charValue())
          .booleanValueBoxed(
              sample.booleanValueBoxed == null ? (short) 0 : (short) (sample.booleanValueBoxed ? 1 : 0));

      if (sample.intArray != null) {
        SampleEncoder.IntArrayEncoder groupEncoder = sampleEncoder.intArrayCount(sample.intArray.length);
        for (int v : sample.intArray) {
          groupEncoder.next().element(v);
        }
      }
      if (sample.longArray != null) {
        SampleEncoder.LongArrayEncoder groupEncoder = sampleEncoder.longArrayCount(sample.longArray.length);
        for (long v : sample.longArray) {
          groupEncoder.next().element(v);
        }
      }
      if (sample.floatArray != null) {
        SampleEncoder.FloatArrayEncoder groupEncoder = sampleEncoder.floatArrayCount(sample.floatArray.length);
        for (float v : sample.floatArray) {
          groupEncoder.next().element(v);
        }
      }
      if (sample.doubleArray != null) {
        SampleEncoder.DoubleArrayEncoder groupEncoder = sampleEncoder.doubleArrayCount(sample.doubleArray.length);
        for (double v : sample.doubleArray) {
          groupEncoder.next().element(v);
        }
      }
      if (sample.shortArray != null) {
        SampleEncoder.ShortArrayEncoder groupEncoder = sampleEncoder.shortArrayCount(sample.shortArray.length);
        for (short v : sample.shortArray) {
          groupEncoder.next().element(v);
        }
      }
      if (sample.charArray != null) {
        SampleEncoder.CharArrayEncoder groupEncoder = sampleEncoder.charArrayCount(sample.charArray.length);
        for (char v : sample.charArray) {
          groupEncoder.next().element((byte) v);
        }
      }
      if (sample.booleanArray != null) {
        SampleEncoder.BooleanArrayEncoder groupEncoder = sampleEncoder.booleanArrayCount(sample.booleanArray.length);
        for (boolean v : sample.booleanArray) {
          groupEncoder.next().element((short) (v ? 1 : 0));
        }
      }
      if (sample.string != null) {
        sampleEncoder.string(sample.string);
      }

      bufferIndex = messageHeaderEncoder.encodedLength() + sampleEncoder.encodedLength();
      byte[] result = new byte[bufferIndex];
      buffer.getBytes(0, result);
      return result;
    }

    public Sample deserializeSample(byte[] data) {
      UnsafeBuffer buf = new UnsafeBuffer(data);
      messageHeaderDecoder.wrap(buf, 0);
      int idx = messageHeaderDecoder.encodedLength();

      sampleDecoder.wrap(buf, idx, messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());

      Sample sample = new Sample();
      sample.intValue = sampleDecoder.intValue();
      sample.longValue = sampleDecoder.longValue();
      sample.floatValue = sampleDecoder.floatValue();
      sample.doubleValue = sampleDecoder.doubleValue();
      sample.shortValue = sampleDecoder.shortValue();
      sample.charValue = (char) sampleDecoder.charValue();
      sample.booleanValue = sampleDecoder.booleanValue() != 0;
      sample.intValueBoxed = sampleDecoder.intValueBoxed();
      sample.longValueBoxed = sampleDecoder.longValueBoxed();
      sample.floatValueBoxed = sampleDecoder.floatValueBoxed();
      sample.doubleValueBoxed = sampleDecoder.doubleValueBoxed();
      sample.shortValueBoxed = sampleDecoder.shortValueBoxed();
      sample.charValueBoxed = (char) sampleDecoder.charValueBoxed();
      sample.booleanValueBoxed = sampleDecoder.booleanValueBoxed() != 0;

      SampleDecoder.IntArrayDecoder intArrayDecoder = sampleDecoder.intArray();
      sample.intArray = new int[intArrayDecoder.count()];
      int i = 0;
      for (SampleDecoder.IntArrayDecoder d : intArrayDecoder) {
        sample.intArray[i++] = d.element();
      }
      SampleDecoder.LongArrayDecoder longArrayDecoder = sampleDecoder.longArray();
      sample.longArray = new long[longArrayDecoder.count()];
      i = 0;
      for (SampleDecoder.LongArrayDecoder d : longArrayDecoder) {
        sample.longArray[i++] = d.element();
      }
      SampleDecoder.FloatArrayDecoder floatArrayDecoder = sampleDecoder.floatArray();
      sample.floatArray = new float[floatArrayDecoder.count()];
      i = 0;
      for (SampleDecoder.FloatArrayDecoder d : floatArrayDecoder) {
        sample.floatArray[i++] = d.element();
      }
      SampleDecoder.DoubleArrayDecoder doubleArrayDecoder = sampleDecoder.doubleArray();
      sample.doubleArray = new double[doubleArrayDecoder.count()];
      i = 0;
      for (SampleDecoder.DoubleArrayDecoder d : doubleArrayDecoder) {
        sample.doubleArray[i++] = d.element();
      }
      SampleDecoder.ShortArrayDecoder shortArrayDecoder = sampleDecoder.shortArray();
      sample.shortArray = new short[shortArrayDecoder.count()];
      i = 0;
      for (SampleDecoder.ShortArrayDecoder d : shortArrayDecoder) {
        sample.shortArray[i++] = d.element();
      }
      SampleDecoder.CharArrayDecoder charArrayDecoder = sampleDecoder.charArray();
      sample.charArray = new char[charArrayDecoder.count()];
      i = 0;
      for (SampleDecoder.CharArrayDecoder d : charArrayDecoder) {
        sample.charArray[i++] = (char) d.element();
      }
      SampleDecoder.BooleanArrayDecoder booleanArrayDecoder = sampleDecoder.booleanArray();
      sample.booleanArray = new boolean[booleanArrayDecoder.count()];
      i = 0;
      for (SampleDecoder.BooleanArrayDecoder d : booleanArrayDecoder) {
        sample.booleanArray[i++] = d.element() != 0;
      }
      sample.string = sampleDecoder.string();

      return sample;
    }

    public byte[] serializeMediaContent(MediaContent mediaContent) {
      bufferIndex = 0;
      mediaContentEncoder.wrapAndApplyHeader(buffer, bufferIndex, messageHeaderEncoder);
      bufferIndex += messageHeaderEncoder.encodedLength();

      Media media = mediaContent.getMedia();
      mediaContentEncoder
          .width(media.getWidth())
          .height(media.getHeight())
          .duration(media.getDuration())
          .size(media.getSize())
          .bitrate(media.getBitrate())
          .hasBitrate(media.isHasBitrate() ? (short) 1 : (short) 0)
          .player(org.apache.fory.benchmark.state.generated.sbe.Player.get(
              (short) media.getPlayer().ordinal()))
          .mediaUri(media.getUri())
          .mediaTitle(media.getTitle() == null ? "" : media.getTitle())
          .format(media.getFormat())
          .copyright(media.getCopyright() == null ? "" : media.getCopyright());

      MediaContentEncoder.ImagesEncoder imagesEncoder =
          mediaContentEncoder.imagesCount(mediaContent.getImages().size());
      for (Image image : mediaContent.getImages()) {
        imagesEncoder
            .next()
            .width(image.getWidth())
            .height(image.getHeight())
            .size(ImageSize.get((short) image.getSize().ordinal()))
            .uri(image.getUri())
            .title(image.getTitle() == null ? "" : image.getTitle());
      }

      MediaContentEncoder.PersonsEncoder personsEncoder =
          mediaContentEncoder.personsCount(media.getPersons().size());
      for (String person : media.getPersons()) {
        personsEncoder.next().person(person);
      }

      bufferIndex = messageHeaderEncoder.encodedLength() + mediaContentEncoder.encodedLength();
      byte[] result = new byte[bufferIndex];
      buffer.getBytes(0, result);
      return result;
    }

    public MediaContent deserializeMediaContent(byte[] data) {
      UnsafeBuffer buf = new UnsafeBuffer(data);
      messageHeaderDecoder.wrap(buf, 0);
      int idx = messageHeaderDecoder.encodedLength();

      mediaContentDecoder.wrap(buf, idx, messageHeaderDecoder.blockLength(), messageHeaderDecoder.version());

      Media media = new Media();
      media.setWidth(mediaContentDecoder.width());
      media.setHeight(mediaContentDecoder.height());
      media.setDuration(mediaContentDecoder.duration());
      media.setSize(mediaContentDecoder.size());
      media.setBitrate(mediaContentDecoder.bitrate());
      media.setHasBitrate(mediaContentDecoder.hasBitrate() != 0);
      media.setPlayer(Media.Player.values()[mediaContentDecoder.player().ordinal()]);
      media.setUri(mediaContentDecoder.mediaUri());
      media.setTitle(mediaContentDecoder.mediaTitle());
      media.setFormat(mediaContentDecoder.format());
      media.setCopyright(mediaContentDecoder.copyright());

      java.util.List<Image> images = new java.util.ArrayList<>();
      for (MediaContentDecoder.ImagesDecoder imagesDecoder : mediaContentDecoder.images()) {
        images.add(
            new Image(
                imagesDecoder.uri(),
                imagesDecoder.title(),
                imagesDecoder.width(),
                imagesDecoder.height(),
                Image.Size.values()[imagesDecoder.size().ordinal()],
                null));
      }

      java.util.List<String> persons = new java.util.ArrayList<>();
      for (MediaContentDecoder.PersonsDecoder personsDecoder : mediaContentDecoder.persons()) {
        persons.add(personsDecoder.person());
      }
      media.setPersons(persons);

      return new MediaContent(media, images);
    }
  }
}
