-- The energy savings change their baseline (EnergyAccountingPolicy): the conventional system now runs the unit while
-- the room is in scheduled use, not all day from 07:00 to 21:00, which in Pasto's 13 °C made any heating outside those
-- hours count as a loss. The savings accumulated in each twin were measured against the old baseline and are not
-- comparable, so the twin states start again: they are derived from the readings and rebuild with the next one. The
-- readings, alerts, bookings and inventory stay.
DELETE FROM space_twin_states;
