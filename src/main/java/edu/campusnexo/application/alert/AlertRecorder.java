package edu.campusnexo.application.alert;

import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.port.AlertRepository;
import java.time.Instant;

/** Stores an alert unless an alert of the same type was already raised for the space since a given instant. */
public class AlertRecorder {
    private final AlertRepository repository;

    public AlertRecorder(AlertRepository repository) {
        this.repository = repository;
    }

    public boolean recordUnlessRaisedSince(Alert alert, Instant since) {
        if (repository.existsSince(alert.spaceId(), alert.type(), since)) {
            return false;
        }
        repository.save(alert);
        return true;
    }
}
