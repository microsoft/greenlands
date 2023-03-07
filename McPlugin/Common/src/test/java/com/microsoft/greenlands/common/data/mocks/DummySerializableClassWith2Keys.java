package com.microsoft.greenlands.common.data.mocks;

import com.microsoft.greenlands.client.model.GameMode;
import com.microsoft.greenlands.common.data.RedisRecord;
import com.microsoft.greenlands.common.data.annotations.RedisKey;

public class DummySerializableClassWith2Keys implements RedisRecord {

  @RedisKey
  public String aKey1;

  @RedisKey
  public String aKey2;

  public String aValue1;
  public String aValue2;
  public String aValue3;

  public String[] anArray;

  public boolean aBool;

  public GameMode anEnum;

  public Integer anInteger;

  public DummySerializableClassWith2Keys() {
  }

  public DummySerializableClassWith2Keys(String aKey1, String aKey2, String aValue1, String aValue2,
      String aValue3, String[] anArray, boolean aBool, GameMode anEnum, Integer anInteger) {
    this.aKey1 = aKey1;
    this.aKey2 = aKey2;
    this.aValue1 = aValue1;
    this.aValue2 = aValue2;
    this.aValue3 = aValue3;
    this.anArray = anArray;
    this.aBool = aBool;
    this.anEnum = anEnum;
    this.anInteger = anInteger;
  }
}
