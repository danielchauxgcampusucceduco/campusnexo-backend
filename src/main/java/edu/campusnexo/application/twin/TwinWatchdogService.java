package edu.campusnexo.application.twin;

import edu.campusnexo.application.alert.AlertRecorder;
import edu.campusnexo.application.port.in.TwinWatchdogUseCase;
import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.alert.AlertSeverity;
import edu.campusnexo.domain.alert.AlertType;
import edu.campusnexo.domain.port.SpaceRepositoryPort;
import edu.campusnexo.domain.twin.Sensing;
import edu.campusnexo.domain.twin.SpaceTwinState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.UnaryOperator;

/**
 * Looks only at rooms with devices that have been quiet for {@code quietPeriod}: rooms that report are decided by
 * their own readings, and rooms without devices have nothing that changes with time. A camera silent for
 * {@link Sensing#CAMERA_SILENCE} is marked offline (the fusion goes on with CO₂ and Wi-Fi) and raises FALLO_CAMARA.
 */
public class TwinWatchdogService implements TwinWatchdogUseCase {
    static final String TRIGGER = "Vigilancia del gemelo";

    private final Clock clock;
    private final SpaceRepositoryPort twins;
    private final SpaceReevaluationService reevaluation;
    private final AlertRecorder alerts;
    private final Duration quietPeriod;
    private final Duration alertCooldown;

    public TwinWatchdogService(Clock clock, SpaceRepositoryPort twins, SpaceReevaluationService reevaluation,
                               AlertRecorder alerts, Duration quietPeriod, Duration alertCooldown) {
        this.clock = clock;
        this.twins = twins;
        this.reevaluation = reevaluation;
        this.alerts = alerts;
        this.quietPeriod = quietPeriod;
        this.alertCooldown = alertCooldown;
    }

    @Override
    public int vigilar() {
        Instant now = clock.instant();
        int revisados = 0;
        for (SpaceTwinState twin : twins.findAllTwins()) {
            if (!twin.sensing().reporting() || twin.updatedAt().isAfter(now.minus(quietPeriod))) {
                continue;
            }
            boolean silent = twin.sensing().cameraSilent(now);
            UnaryOperator<SpaceTwinState> change = silent
                    ? state -> state.toBuilder().sensing(state.sensing().cameraLost()).build()
                    : UnaryOperator.identity();
            reevaluation.reevaluate(twin.spaceId(), TRIGGER, change);
            if (silent) {
                long seconds = Duration.between(twin.sensing().lastCameraFrameAt(), now).toSeconds();
                alerts.recordUnlessRaisedSince(Alert.raise(twin.spaceId(), AlertType.FALLO_CAMARA, AlertSeverity.CRITICA,
                        now, "La cámara de " + twin.spaceId() + " no envía frames hace " + seconds
                                + " s; la ocupación se estima con CO₂ y Wi-Fi", seconds,
                        Sensing.CAMERA_SILENCE.toSeconds()), now.minus(alertCooldown));
            }
            revisados++;
        }
        return revisados;
    }
}
