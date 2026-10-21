-- 1. No two reservations of the same space may overlap, guaranteed by PostgreSQL itself: two concurrent
--    requests that both checked "no overlap" cannot both be committed. Half-open ranges [starts_at, ends_at)
--    let a reservation start exactly when the previous one ends. btree_gist provides "=" for the space id
--    inside a GiST index (it is a trusted extension: the database owner can create it).
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE reservations
    ADD CONSTRAINT reservations_no_overlap
    EXCLUDE USING gist (space_id WITH =, tstzrange(starts_at, ends_at, '[)') WITH &&);

-- 2. Optimistic concurrency for the twin: every write must present the revision it read.
ALTER TABLE space_twin_states ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
