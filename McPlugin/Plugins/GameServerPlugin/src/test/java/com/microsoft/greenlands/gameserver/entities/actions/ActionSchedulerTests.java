package com.microsoft.greenlands.gameserver.entities.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microsoft.greenlands.common.data.records.GameConfig;
import com.microsoft.greenlands.common.utils.PluginUtils;
import com.microsoft.greenlands.common.utils.Scheduler;
import com.microsoft.greenlands.gameserver.entities.AgentBot;
import com.microsoft.greenlands.gameserver.entities.mocks.CountActionMock;
import com.microsoft.greenlands.gameserver.utils.AgentManager;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class ActionSchedulerTests {

  private AutoCloseable mocks;
  private final int defaultTaskId = 31;

  @Mock
  private BukkitScheduler mockServerScheduler;

  @Mock
  private Scheduler mockScheduler;

  @Mock
  private ActionCallback mockCallback;

  @Spy
  private CountActionMock mockAction;

  @InjectMocks
  private ActionScheduler actionScheduler;

  @BeforeAll
  public static void setUp() {
    // mock JavaPlugin
    var mockJavaPlugin = mock(JavaPlugin.class);
    when(mockJavaPlugin.getLogger()).thenReturn(Logger.getLogger("test-logger"));

    // mock plugin utils
    mockStatic(PluginUtils.class)
        .when(PluginUtils::getPluginInstance)
        .thenReturn(mockJavaPlugin);

    // mock citizens
    var mockNavigator = mock(Navigator.class);
    when(mockNavigator.getDefaultParameters()).thenReturn(new NavigatorParameters());
    when(mockNavigator.getLocalParameters()).thenReturn(new NavigatorParameters());

    var mockNpc = mock(NPC.class);
    when(mockNpc.getNavigator()).thenReturn(mockNavigator);

    var mockNPCRegistry = mock(NPCRegistry.class);
    when(mockNPCRegistry.createNPC(any(EntityType.class), anyString())).thenReturn(mockNpc);

    mockStatic(CitizensAPI.class)
        .when(CitizensAPI::getNPCRegistry)
        .thenReturn(mockNPCRegistry);

    // mock agent manager
    var mockGameConfig = new GameConfig();
    mockGameConfig.gameId = "aacc110b-a4ae-4dab-bd15-553c86d17227";

    var mockAgent = new AgentBot(UUID.fromString("45cc110b-a4ae-4dab-bd15-553c86d17227"), null,
        mockGameConfig);

    mockStatic(AgentManager.class)
        .when(() -> AgentManager.getAgentByKey(any(UUID.class)))
        .thenReturn(Optional.of(mockAgent));
  }

  /**
   * Initializes mocks.
   */
  @BeforeEach
  public void setUpEach() {
    mocks = MockitoAnnotations.openMocks(this);

    // Override scheduler to call the run() method once when task is scheduled
    doAnswer(new Answer<Integer>() {
      @Override
      public Integer answer(InvocationOnMock invocation) throws Throwable {
        var task = (Runnable) invocation.getArguments()[0];
        task.run();
        return defaultTaskId;
      }
    }).when(mockScheduler).scheduleRepeatingTaskByTicks(any(Runnable.class), anyLong(), anyLong());
  }

  /**
   * Test actions that finish in their setUp method are never scheduled.
   */
  @Test
  public void onlySetUpActionIsNotScheduled() {
    doAnswer(invocation -> {
      mockAction.transitionToState(Action.ActionState.SUCCESS);
      return null;
    }).when(mockAction).setUp();

    actionScheduler.schedule(mockAction, mockCallback);

    assertEquals(Action.ActionState.SUCCESS, mockAction.getState());
    verify(mockAction).setUp();
    verify(mockScheduler, never()).scheduleRepeatingTaskByTicks(any(Runnable.class), anyLong(),
        anyLong());
    verify(mockScheduler, never()).cancelTask(anyInt());
    verify(mockCallback).onActionEnd(eq(mockAction));
  }

  /**
   * Test action that doesn't finish in setUp method is scheduled and never cancelled.
   */
  @Test
  public void repetitiveActionIsScheduled() {
    actionScheduler.schedule(mockAction, mockCallback);
    // Taks is still running
    assertEquals(Action.ActionState.RUNNING, mockAction.getState());
    verify(mockAction).setUp();
    verify(mockScheduler).scheduleRepeatingTaskByTicks(any(), eq(1L),
        eq(mockAction.stateCheckIntervalTicks));
    verify(mockAction).hasBeenScheduled(eq(defaultTaskId));
    verify(mockScheduler, never()).cancelTask(anyInt());
    verify(mockCallback, never()).onActionEnd(any(Action.class));
  }

  /**
   * Test actions that update their state to SUCCESS on run method are still scheduled.
   */
  @Test
  public void actionIsCanceledAfterSuccess() {
    // Override action mock to change status to success
    doAnswer(invocation -> {
      mockAction.transitionToState(Action.ActionState.SUCCESS);
      return null;
    }).when(mockAction).execute();
    when(mockAction.getScheduledTaskId()).thenReturn(defaultTaskId);

    // Override scheduler to call the run() method once when task is scheduled
    doAnswer(new Answer<Integer>() {
      @Override
      public Integer answer(InvocationOnMock invocation) throws Throwable {
        var task = (Runnable) invocation.getArguments()[0];
        task.run();
        return defaultTaskId;
      }
    }).when(mockScheduler).scheduleRepeatingTaskByTicks(any(Runnable.class), anyLong(), anyLong());

    actionScheduler.schedule(mockAction, mockCallback);
    // Task has finished
    assertEquals(Action.ActionState.SUCCESS, mockAction.getState());
    // Task was correctly scheduled and cancelled
    verify(mockAction).hasBeenScheduled(eq(defaultTaskId));
    verify(mockScheduler).cancelTask(eq(defaultTaskId));
    verify(mockCallback).onActionEnd(eq(mockAction));
  }

  /**
   * Test actions that update their state to SUCCESS on run method are still scheduled.
   */
  @Test
  public void actionIsCanceledWhenTimesOut() {
    when(mockAction.getScheduledTaskId()).thenReturn(defaultTaskId);
    when(mockAction.hasTimedOut()).thenReturn(true);

    actionScheduler.schedule(mockAction, mockCallback);
    // Task has failed
    assertEquals(Action.ActionState.FAILURE, mockAction.getState());
    // Task was correctly scheduled
    verify(mockScheduler).scheduleRepeatingTaskByTicks(any(), eq(1L),
        eq(mockAction.stateCheckIntervalTicks));
    verify(mockAction).hasBeenScheduled(eq(defaultTaskId));
    // Task was cancelled
    verify(mockScheduler).cancelTask(eq(defaultTaskId));
    verify(mockCallback).onActionTimeout(eq(mockAction));
  }

  @AfterEach
  public void tearDownEach() throws Exception {
    mocks.close();
  }
}
