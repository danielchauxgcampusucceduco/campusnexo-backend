-- Bookings. No two bookings of the same space may overlap, guaranteed by PostgreSQL itself: two concurrent requests
-- that both checked "no overlap" cannot both be committed. Half-open ranges [starts_at, ends_at) let a booking start
-- exactly when the previous one ends; btree_gist provides "=" for the space id inside the GiST index (a trusted
-- extension the database owner can create).
--
-- holder_code names the registered person responsible (usually the teacher); it is a code, not a foreign key, so a
-- booking keeps who it was for even if the person is later removed. checked_in_at is when that person confirmed
-- attendance at the room's kiosk.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE reservations (
    reservation_id UUID PRIMARY KEY,
    space_id VARCHAR(32) NOT NULL REFERENCES spaces (space_id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    expected_attendees INTEGER NOT NULL CHECK (expected_attendees > 0),
    reference VARCHAR(80) NOT NULL,
    created_by VARCHAR(64),
    holder_code VARCHAR(32),
    checked_in_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_reservation_interval CHECK (ends_at > starts_at),
    CONSTRAINT chk_reservation_check_in_holder CHECK (checked_in_at IS NULL OR holder_code IS NOT NULL),
    CONSTRAINT reservations_no_overlap EXCLUDE USING gist (space_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&)
);
CREATE INDEX idx_reservations_space_time ON reservations (space_id, starts_at, ends_at);
