package edu.campusnexo.infrastructure.config;

import edu.campusnexo.application.port.in.TwinWatchdogUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the twin watchdog on a fixed delay; {@code TWIN_WATCHDOG_ENABLED=false} turns it off. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.twin.watchdog.enabled", havingValue = "true", matchIfMissing = true)
class TwinWatchdogScheduler {
    private static final Logger log = LoggerFactory.getLogger(TwinWatchdogScheduler.class);
    private final TwinWatchdogUseCase watchdog;

    TwinWatchdogScheduler(TwinWatchdogUseCase watchdog) {
        this.watchdog = watchdog;
    }

    @Scheduled(fixedDelayString = "${app.twin.watchdog.interval}", initialDelayString = "${app.twin.watchdog.interval}")
    void vigilar() {
        try {
            int revisados = watchdog.vigilar();
            if (revisados > 0) {
                log.debug("Vigilancia del gemelo: {} salones reevaluados", revisados);
            }
        } catch (RuntimeException failure) {
            // The next round tries again; one bad room must not stop the schedule.
            log.warn("Vigilancia del gemelo falló: {}", failure.getMessage(), failure);
        }
    }
}
