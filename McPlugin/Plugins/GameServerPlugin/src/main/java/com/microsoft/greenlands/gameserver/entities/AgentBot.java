package com.microsoft.greenlands.gameserver.entities;

import com.microsoft.greenlands.client.model.BaseEvent;
import com.microsoft.greenlands.client.model.BlockPlaceEvent;
import com.microsoft.greenlands.client.model.BlockRemoveEvent;
import com.microsoft.greenlands.client.model.PlatformPlayerTurnChangeEvent;
import com.microsoft.greenlands.client.model.PlayerChatEvent;
import com.microsoft.greenlands.client.model.PlayerMoveEvent;
import com.microsoft.greenlands.common.data.records.GameConfig;
import com.microsoft.greenlands.common.data.records.PlayerGameConfig;
import com.microsoft.greenlands.common.utils.BlockUtils;
import com.microsoft.greenlands.common.utils.LocationUtils;
import com.microsoft.greenlands.common.utils.MinecraftLogger;
import com.microsoft.greenlands.common.utils.WorldUtils;
import com.microsoft.greenlands.gameserver.entities.actions.Action;
import com.microsoft.greenlands.gameserver.entities.actions.Action.ActionState;
import com.microsoft.greenlands.gameserver.entities.actions.ActionCallback;
import com.microsoft.greenlands.gameserver.entities.actions.ActionScheduler;
import com.microsoft.greenlands.gameserver.entities.actions.BlockBreakAction;
import com.microsoft.greenlands.gameserver.entities.actions.BlockPlaceAction;
import com.microsoft.greenlands.gameserver.entities.actions.EndTurnAction;
import com.microsoft.greenlands.gameserver.entities.actions.PlayerChatAction;
import com.microsoft.greenlands.gameserver.entities.actions.PlayerMoveAction;
import com.microsoft.greenlands.gameserver.utils.AgentManager;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.Gravity;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;


/**
 * Abstraction to represent automatic bots that simulate Players.
 */
public class AgentBot {

  public final UUID agentKey;
  private final NPC npc;
  private PlayerGameConfig agentGameConfig;
  private GameConfig gameConfig;

  // If true, actions can not be added to pendingActions queue
  // If false, actions may be added to pendingActions queue
  private boolean preventAdditionalPendingActions;
  private final BlockingQueue<Action> pendingActions = new LinkedBlockingQueue<>();

  public NPC getNpc() {
    return npc;
  }

  public void allowAdditionalActions() {
    this.preventAdditionalPendingActions = false;
  }

  public PlayerGameConfig getAgentGameConfig() {
    return agentGameConfig;
  }

  public void setAgentGameConfig(PlayerGameConfig agentGameConfig) {
    this.agentGameConfig = agentGameConfig;
  }

  public GameConfig getGameConfig() {
    return gameConfig;
  }

  public void setGameConfig(GameConfig gameConfig) {
    this.gameConfig = gameConfig;
  }


  /**
   * Creates a new AgentBot.
   *
   * @param agentKey Agent key to be displayed
   */
  public AgentBot(UUID agentKey, PlayerGameConfig agentGameConfig, GameConfig gameConfig) {
    MinecraftLogger.finest("Creating Agent " + agentKey + " for game " + gameConfig.gameId);

    this.agentKey = agentKey;
    this.agentGameConfig = agentGameConfig;
    this.gameConfig = gameConfig;

    npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "Agent");
    // prepare for npc teleporting
    npc.setFlyable(true);
    if (npc.hasTrait(Gravity.class)) {
      npc.getOrAddTrait(Gravity.class).setEnabled(true);
    }

    npc.getNavigator().getDefaultParameters().useNewPathfinder(true);

    // don't stop navigation until the distance of the npc is delta blocks away from target
    npc.getNavigator().getDefaultParameters().distanceMargin(0.1);
    npc.getNavigator().getDefaultParameters().pathDistanceMargin(1);
  }

  public static void deregisterAllBots() {
    CitizensAPI.getNPCRegistry().deregisterAll();
  }

  /**
   * Removes agent's npc in registry and de-schedules any running task.
   */
  public void destroy() {
    if (npc.isSpawned()) {
      npc.despawn();
    }
    CitizensAPI.getNPCRegistry().deregister(npc);

    // Cancel the current action, if any. Only the top action can be scheduled,
    // so we know there is no need of canceling the remaining actions.
    if (pendingActions.size() > 0) {
      ActionScheduler.getInstance().cancelAction(pendingActions.peek());
    }
    pendingActions.clear();
  }

  private class ScheduleNextActionCallback extends ActionCallback {

    @Override
    public void onActionEnd(Action action) {
      super.onActionEnd(action);
      // TODO handle action failure
      AgentManager.getAgentByKey(action.agentKey).get().maybeScheduleNextAction();
    }

    @Override
    public void onActionTimeout(Action action) {
      super.onActionTimeout(action);
      AgentManager.getAgentByKey(action.agentKey).get().maybeScheduleNextAction();
    }
  }

  /**
   * Spawns at the specified world's default spawn point.
   */
  public void spawnAtWorldSpawn(World world) {
    npc.spawn(world.getSpawnLocation());
  }

  /**
   * Spawns at the specified world's default spawn point.
   */
  public void spawnAtWorldSpawn(String worldName) {
    var world = WorldUtils.getWorldWithName(worldName);

    npc.spawn(world.getSpawnLocation());
  }

  /**
   * Spawns at the specified X,Y,Z point inside the world.
   */
  public void spawnAtLocation(String worldName, Vector location) {
    var world = WorldUtils.getWorldWithName(worldName);
    var bukkitLocation = location.toLocation(world.getCBWorld());

    npc.spawn(bukkitLocation);
  }

  /**
   * Spawns at the specified location.
   */
  public void spawnAtLocation(Location location) {
    npc.spawn(location);
  }

  /**
   * Spawns at the specified world's location.
   */
  public void spawnAtLocation(String worldName,
      com.microsoft.greenlands.client.model.Location location) {

    var world = WorldUtils.getWorldWithName(worldName);
    var bukkitLocation = LocationUtils.convertToBukkitLocation(
        world.getCBWorld(),
        location);

    npc.spawn(bukkitLocation);
  }

  public UUID getUuid() {
    return npc.getUniqueId();
  }

  public String getName() {
    return npc.getName();
  }

  /**
   * Returns a random location in a range of 8 blocks around current position, in the same y plane.
   */
  private Location getRandomLoc() {
    var maxGrid = 8;
    var rd = new Random();

    var location = npc.getEntity().getLocation().clone()
        .add(rd.nextInt(-maxGrid, maxGrid + 1),
            0,
            rd.nextInt(-maxGrid, maxGrid + 1));

    location.setPitch(rd.nextInt(-90, 90));
    location.setYaw(rd.nextInt(0, 360));
    location.setY(1);

    return location;
  }

  /**
   * Takes a {@link BaseEvent} that specifies the action that the agent should take and makes it so
   * that the NPC performs this action.
   */

  public void enqueueAction(BaseEvent event) {
    if (preventAdditionalPendingActions) {
      MinecraftLogger.warning("Agent " + agentKey + " is NOT allowed to enqueue more actions.");
      return;
    }

    if (event instanceof PlatformPlayerTurnChangeEvent) {
      handleTurnChangeEvent(event);
    } else if (event instanceof PlayerMoveEvent) {
      handlePlayerMoveEvent(event);
    } else if (event instanceof PlayerChatEvent) {
      handlePlayerChatEvent(event);
    } else if (event instanceof BlockRemoveEvent) {
      handleBlockRemoveEvent(event);
    } else if (event instanceof BlockPlaceEvent) {
      handleBlockPlaceEvent(event);
    } else {
      MinecraftLogger.severe(
          "Invalid agent event for transforming to npc actions: " + event.getEventType());
      return;
    }

    maybeScheduleNextAction();
  }

  private void handleTurnChangeEvent(BaseEvent event) {
    pendingActions.offer(new EndTurnAction(agentKey));
    this.preventAdditionalPendingActions = true;
  }

  private void handlePlayerMoveEvent(BaseEvent event) {
    var eventLocation = ((PlayerMoveEvent) event).getNewLocation();

    var targetLocation = LocationUtils.convertToBukkitLocation(
        npc.getEntity().getWorld(),
        eventLocation
    );

    pendingActions.offer(new PlayerMoveAction(agentKey, targetLocation));
  }

  private void handlePlayerChatEvent(BaseEvent event) {
    var eventMessage = ((PlayerChatEvent) event).getMessage();

    pendingActions.offer(new PlayerChatAction(agentKey, eventMessage));
  }

  private void handleBlockRemoveEvent(BaseEvent event) {
    var eventLocation = ((BlockRemoveEvent) event).getLocation();

    var blockRemoveLocation = LocationUtils.convertToBukkitLocation(
        npc.getEntity().getWorld(),
        eventLocation
    );

    pendingActions.offer(new BlockBreakAction(agentKey, blockRemoveLocation));
  }

  private void handleBlockPlaceEvent(BaseEvent event) {
    var materialId = ((BlockPlaceEvent) event).getMaterial();
    var eventLocation = ((BlockPlaceEvent) event).getLocation();

    var blockPlaceLocation = LocationUtils.convertToBukkitLocation(
        npc.getEntity().getWorld(),
        eventLocation
    );

    pendingActions.offer(
        new BlockPlaceAction(
            agentKey,
            blockPlaceLocation,
            BlockUtils.MATERIAL_NAMES[materialId]));
  }

  /**
   * Placeholder for methods that will actually select the next action to execute.
   * TODO this should also receive the action's parameters, possibly serialized.
   * TODO this should be replaced by something more elegant than a switch.
   */
  public void enqueueAction(String actionName) {

    MinecraftLogger.info("Enqueing action! " + actionName);
    switch (actionName) {
      case "random_move":
        pendingActions.add(new PlayerMoveAction(agentKey, getRandomLoc()));
        break;
      case "place_block":
        // Just as example, the Actions to place a block could be:
        // Enqueue set item in hand
        // Enqueue hand animation and block appearing
        break;
      case "destroy_block":  // Destroys block we know
        // Enqueue movement if necessary
        var existingBlockLocation = npc.getStoredLocation().getWorld().getSpawnLocation().clone()
            .add(-4, 0, 0);
        pendingActions.add(new BlockBreakAction(agentKey, existingBlockLocation));
        break;
      case "place_block_and_destroy":
        // Block appears in world (ignore the hand animation), agent moves to block and breaks it
        var moveLocation = getRandomLoc();
        moveLocation.setPitch(45);
        MinecraftLogger.info("Moving to " + moveLocation);

        pendingActions.add(new PlayerMoveAction(agentKey, moveLocation.clone()));

        var livingEntity = (LivingEntity) npc.getEntity();
        var blockPlaceRaytraceSource = moveLocation.clone();
        blockPlaceRaytraceSource.setY(
            blockPlaceRaytraceSource.getY() + livingEntity.getEyeHeight()
        );

        var raytraceResult = npc.getEntity().getWorld().rayTraceBlocks(
            blockPlaceRaytraceSource,
            blockPlaceRaytraceSource.getDirection(),
            5,
            FluidCollisionMode.NEVER,
            true
        );

        assert (raytraceResult != null
            && raytraceResult.getHitBlockFace() != null
            && raytraceResult.getHitBlock() != null) :
            "Raytrace result should not be null since agent is should be looking towards the floor";

        var adjacentBlock = raytraceResult
            .getHitBlock()
            .getRelative(raytraceResult.getHitBlockFace());

        // place a block
        pendingActions.add(new BlockPlaceAction(
            agentKey,
            adjacentBlock.getLocation().clone(),
            Material.AMETHYST_BLOCK));

        // move somewhere else, so we have time to observe that the block was actually placed
        var newLocation = getRandomLoc();
        pendingActions.add(new PlayerMoveAction(agentKey, newLocation));

        // go back to original location
        pendingActions.add(new PlayerMoveAction(agentKey, moveLocation));

        // break the block
        pendingActions.add(new BlockBreakAction(
            agentKey,
            adjacentBlock.getLocation().clone()));

        break;

      default:
        break;
    }
    maybeScheduleNextAction();
  }

  /**
   * Schedules next action in queue when possible. If action on top of queue is running, it does
   * nothing. If action on top of queue is finished, removes it and schedules next.
   */
  public void maybeScheduleNextAction() {
    if (pendingActions.size() == 0) {
      return;
    }

    Action currentAction = pendingActions.peek();
    if (currentAction.getState() == ActionState.EVENT_PRODUCED ||
        currentAction.getState() == ActionState.FAILURE) {
      MinecraftLogger.finest("Dequeuing action " + currentAction.toString());
      pendingActions.poll();
    }
    // Re-check if there is something to schedule
    if (pendingActions.size() == 0) {
      return;
    }

    currentAction = pendingActions.peek();
    // Current action is still running OR producing the event after successfully finished the action, we have to wait until next call.
    if (currentAction.getState() == ActionState.RUNNING
        || currentAction.getState() == ActionState.SUCCESS) {
      MinecraftLogger.info("Waiting for action " + currentAction.toString());
      return;
    }

    if (currentAction.getState() == ActionState.READY) {
      ActionScheduler.getInstance().schedule(currentAction, new ScheduleNextActionCallback());
      MinecraftLogger.info("Schedule action to start " + currentAction.toString());
    }
  }
}