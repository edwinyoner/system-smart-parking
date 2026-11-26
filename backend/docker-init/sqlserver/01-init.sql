-- ==============================================================================
-- SQL Server 2022 - payment-service
-- Lo ejecuta entrypoint.sh (mismo contenedor) en cuanto el motor responde.
-- APP_USER y APP_PASSWORD llegan como variables de sqlcmd (-v), desde backend/.env.
-- Es idempotente: se puede ejecutar en cada arranque.
-- Las tablas las crea Hibernate (ddl-auto), aquí solo la base y el usuario.
-- ==============================================================================

IF DB_ID('payment_service_db') IS NULL
    CREATE DATABASE payment_service_db;
GO

-- Usuario propio del servicio (no se conecta como sa)
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = '$(APP_USER)')
    CREATE LOGIN [$(APP_USER)] WITH PASSWORD = '$(APP_PASSWORD)', DEFAULT_DATABASE = payment_service_db;
GO

USE payment_service_db;
GO

IF USER_ID('$(APP_USER)') IS NULL
    CREATE USER [$(APP_USER)] FOR LOGIN [$(APP_USER)];
GO

-- db_owner solo dentro de payment_service_db: Hibernate crea y borra tablas (create-drop)
ALTER ROLE db_owner ADD MEMBER [$(APP_USER)];
GO
