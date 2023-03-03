package com.microsoft.plaiground.common.enums;

import java.util.Optional;

public enum ChallengeType {
  AGENT_CHALLENGE, HUMAN_CHALLENGE;

  public static Optional<ChallengeType> fromString(String key) {
    switch (key) {
      case "ac" -> {
        return Optional.of(AGENT_CHALLENGE);
      }
      case "hc" -> {
        return Optional.of(HUMAN_CHALLENGE);
      }
    }

    return Optional.empty();
  }

  @Override
  public String toString() {
    switch (this) {
      case AGENT_CHALLENGE -> {
        return "ac";
      }
      case HUMAN_CHALLENGE -> {
        return "hc";
      }
    }

    assert false : "Tried to convert an unknown ChallengeType enum value to string";
    return null;
  }
}
