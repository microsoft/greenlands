package com.microsoft.greenlands.lobbyserver.entities;

import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Represents the information about a player that wants to be paired.
 */
public record PlayerPairingInfo(
    /*
     * ID of the player that is represented by this pairing info
     */
    UUID playerId,

    /*
     * Whether the current Pairing info represents a pairing request made by an agent or by a player.
     */
    boolean isAgent,

    /*
     * The _join code_ that players can optionally provide when joining a game. This is filled
     * if the player joined using a specific join code provided to them from an external source
     * (e.g. MTurk).
     */
    @Nullable String joinCode,

    /*
     * The _group id_ is attached to events to allow querying for all events in the group or set.
     * This is created using components from the join code. It is usually the same, but can be different.
     */
    @Nullable String groupId
) {

}
