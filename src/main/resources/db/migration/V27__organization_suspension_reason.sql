-- Why an organization was suspended; shown to the SIGNA admin and cleared on reactivation.
ALTER TABLE organizations ADD COLUMN suspension_reason varchar(500);
