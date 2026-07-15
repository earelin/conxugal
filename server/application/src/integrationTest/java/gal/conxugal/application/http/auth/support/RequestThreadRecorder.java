package gal.conxugal.application.http.auth.support;

import jakarta.inject.Singleton;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class RequestThreadRecorder {

  private final AtomicReference<Thread> lastRequestThread = new AtomicReference<>();

  void record(Thread thread) {
    lastRequestThread.set(thread);
  }

  public Thread lastRequestThread() {
    return lastRequestThread.get();
  }
}
