package com.microsoft.plaiground.common.data;

import com.microsoft.plaiground.client.model.GameMode;
import com.microsoft.plaiground.common.data.mocks.DummySerializableClassWith2Keys;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SerializerTests {

  @Test
  public void canSerializeDummyClass() {
    var dummy = new DummySerializableClassWith2Keys(
        "key1",
        "key2",
        "v1",
        "v2",
        "v3",
        new String[]{"a value", "another value"},
        true,
        GameMode.ADVENTURE,
        57
    );

    var serialized = RecordSerializer.serialize(dummy);

    var expectedBase =
        "DUMMYSERIALIZABLECLASSWITH2KEYS:key1:key2:";
    for (var k : serialized.keySet()) {
      Assertions.assertTrue(k.startsWith(expectedBase));
    }

    Assertions.assertEquals(dummy.aValue1, serialized.get(expectedBase + "aValue1"));
    Assertions.assertEquals(dummy.aValue2, serialized.get(expectedBase + "aValue2"));
    Assertions.assertEquals(dummy.aValue3, serialized.get(expectedBase + "aValue3"));

    Assertions.assertEquals(String.join(",", dummy.anArray),
        serialized.get(expectedBase + "anArray"));

    Assertions.assertEquals("true", serialized.get(expectedBase + "aBool"));
    Assertions.assertEquals(GameMode.ADVENTURE.name(), serialized.get(expectedBase + "anEnum"));
    Assertions.assertEquals("57", serialized.get(expectedBase + "anInteger"));
  }

  @Test
  public void canDeserializeDummyClass() {
    var expected = new DummySerializableClassWith2Keys(
        "key1",
        "key2",
        "v1",
        "v2",
        "v3",
        new String[]{"a value", "another value"},
        true,
        GameMode.ADVENTURE,
        97
    );

    var serialized = RecordSerializer.serialize(expected);
    var deserialized = RecordSerializer.deserialize(
        DummySerializableClassWith2Keys.class,
        serialized);

    Assertions.assertTrue(deserialized.aBool);

    // just compare the serialized versions
    Assertions.assertEquals(serialized, RecordSerializer.serialize(deserialized));
  }
}
