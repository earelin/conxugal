package gal.conxugal.application.http.auth.support;

import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;

@ServerFilter({"/login", "/forbidden"})
public class RequestThreadRecordingFilter {

  private final RequestThreadRecorder recorder;

  public RequestThreadRecordingFilter(RequestThreadRecorder recorder) {
    this.recorder = recorder;
  }

  @RequestFilter
  void recordExecutionThread() {
    recorder.record(Thread.currentThread());
  }
}
