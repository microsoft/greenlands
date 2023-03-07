package com.microsoft.greenlands.common.utils;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * {@link AsyncHelper} exposes methods that allow us to run certain operations asynchronously (in a
 * thread that is not the main Minecraft Server thread) and have it so that these operations can
 * optionally return a callback which is executed in the main Server thread. This is useful since it
 * allows making expensive operations on a separate thread and then apply any resulting changes to
 * Minecraft on the server thread.
 *
 * <p>AsyncHelper maintains an internal queue ({@link #responseQueue}) which holds the callbacks
 * ({@link Runnable}) that are executed on the main thread. AsyncHelper will check this queue every
 * {@link #asyncHelperLoopDelay}.</p>
 *
 * <p>NOTE: All operations that call the bukkit API need to be done on the Server thread!</p>
 *
 * <p>An example scenario where this can be useful is for making requests to the Service API:</p>
 *
 * <pre>
 * {@code
 *  AsyncHelper.run(() -> {
 *    // suppose this is a long running operation
 *    var resultFromAPI = challengesApi.createChallenge();
 *
 *    return () -> {
 *      // do something with "resultFromAPI"
 *      // this callback is optional, but if provided will run on the main server thread.
 *    };
 *  });
 *
 *  AsyncHelper.run(() -> {
 *    // Example which does not return a callback
 *    return null;
 *  });
 * }
 * </pre>
 */
public class AsyncHelper {

  private static final int asyncHelperLoopDelay = 100 * Scheduler.MILLISECOND;
  private static final Queue<Runnable> responseQueue = new ConcurrentLinkedDeque<>();

  /**
   * This function is meant to be called from the JavaPlugin's onEnable method. It will start a loop
   * that runs periodically and will check if there are any callbacks in {@link
   * AsyncHelper#responseQueue} that need to be executed on the main server thread.
   */
  public static void registerLoop() {
    Scheduler.getInstance().scheduleRepeatingTaskByMs(() -> {
      // check if we have responses in our queue and execute them if we do
      var mainThreadCallbackFn = responseQueue.poll();
      if (mainThreadCallbackFn != null) {
        // this will run the callback, which will ALWAYS run on the main server thread
        mainThreadCallbackFn.run();
      }
    }, asyncHelperLoopDelay);
  }

  /**
   * This method is meant to be called from a thread that is not the main server one, and it will
   * ensure that the provided {@link Runnable} is executed on the main thread as soon as possible.
   */
  public static synchronized void runOnMainThread(Runnable runnable) {
    responseQueue.add(runnable);
  }

  /**
   * This method will run `task` on a new thread. `task` can optionally return a {@link Runnable},
   * and if it does so then this runnable will be executed on the main server thread.
   */
  public static void run(Supplier<@Nullable Runnable> task) {
    var thread = new Thread(() -> {
      // run the supplier and add its callback to the queue (if any)
      var result = task.get();
      if (result != null) {
        responseQueue.add(result);
      }
    });

    thread.start();
  }
}
