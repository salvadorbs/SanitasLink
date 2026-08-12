-- V12: Widen columns that now store AES-GCM encrypted values.
-- Encrypted payloads are Base64 ciphertext with a version prefix and are larger than the
-- plaintext, so the original VARCHAR lengths would be too small.

ALTER TABLE patients ALTER COLUMN email TYPE VARCHAR(600);
ALTER TABLE patients ALTER COLUMN phone TYPE VARCHAR(120);
ALTER TABLE patients ALTER COLUMN address TYPE VARCHAR(500);
ALTER TABLE appointments ALTER COLUMN notes TYPE VARCHAR(2000);
