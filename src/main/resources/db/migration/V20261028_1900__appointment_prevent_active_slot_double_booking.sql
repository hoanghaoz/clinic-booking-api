-- A released reservation stays in history; an active slot belongs to only one appointment.
-- Existing duplicates deliberately fail migration rather than silently discarding bookings.
create unique index uk_appointment_slots_active_slot
  on appointment_slots (slot_id) where released_at is null;
