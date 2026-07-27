package gal.conxugal.application.scheduling.organos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gal.conxugal.domain.organo.ImportOrganos;
import gal.conxugal.domain.organo.ImportOutcome;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

@MicronautTest
@Property(name = "conxugal.organos.import.schedule", value = "*/1 * * * * *")
class ImportOrganosSchedulerFiringIntegrationTest {

  private static final CountDownLatch RUN_INVOKED = new CountDownLatch(1);
  private static final AtomicReference<Thread> RUN_THREAD = new AtomicReference<>();

  @MockBean(ImportOrganos.class)
  ImportOrganos importOrganosMock() {
    ImportOrganos importOrganos = mock(ImportOrganos.class);
    when(importOrganos.run())
        .thenAnswer(
            invocation -> {
              RUN_THREAD.set(Thread.currentThread());
              RUN_INVOKED.countDown();
              return ImportOutcome.success(0, 0, 0);
            });
    return importOrganos;
  }

  @Test
  void fires_on_its_configured_schedule_using_virtual_threads() throws InterruptedException {
    assertThat(RUN_INVOKED.await(5, TimeUnit.SECONDS)).isTrue();
    assertThat(RUN_THREAD.get().isVirtual()).isTrue();
  }
}
