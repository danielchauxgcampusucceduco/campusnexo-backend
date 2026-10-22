package edu.campusnexo.application.alert;

import edu.campusnexo.domain.alert.Alert;
import edu.campusnexo.domain.port.AlertRepository;
import java.time.Instant;

/**
 * Stores an alert unless an alert of the same type was already raised for the space since a given instant.
 * The repository makes the check and the insert one atomic step, so two observer threads (or two backend
 * instances) handling readings of the same room cannot both raise it.
 */
public class AlertRecorder {
    private final AlertRepository repository;

    public AlertRecorder(AlertRepository repository) {
        this.repository = repository;
    }

    public boolean recordUnlessRaisedSince(Alert alert, Instant since) {
        return repository.saveUnlessRaisedSince(alert, since);
    }
}
