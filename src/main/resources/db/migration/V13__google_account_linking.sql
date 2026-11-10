-- Cuentas creadas con Google no tienen contrasena (el codigo ya guardaba null, pero la columna era NOT NULL).
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- Identificador estable de la cuenta de Google ("sub"). Permite vincular una cuenta de Google con
-- un correo distinto al de Signa y reconocerla en el proximo inicio de sesion.
ALTER TABLE users ADD COLUMN google_sub character varying(255);
ALTER TABLE users ADD CONSTRAINT uk_users_google_sub UNIQUE (google_sub);
