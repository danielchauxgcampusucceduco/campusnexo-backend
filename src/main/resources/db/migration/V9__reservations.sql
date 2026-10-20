CREATE TABLE reservations (
    reservation_id UUID PRIMARY KEY,
    space_id VARCHAR(32) NOT NULL REFERENCES spaces (space_id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    expected_attendees INTEGER NOT NULL CHECK (expected_attendees > 0),
    reference VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_reservation_interval CHECK (ends_at > starts_at)
);
CREATE INDEX idx_reservations_space_time ON reservations (space_id, starts_at, ends_at);
