package com.microsoft.plaiground.gameserver.entities;

import java.util.UUID;
import javax.annotation.Nullable;

public class PlayerRole {

  public final String roleId;
  public @Nullable UUID playerId = null;

  public PlayerRole(String roleId) {
    this.roleId = roleId;
  }

  public Boolean isPlayerPresentInGame() {
    return playerId != null;
  }
}
