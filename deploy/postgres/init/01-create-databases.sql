-- One Postgres container locally, but each service gets its own database.
-- Services never read each other's databases.
CREATE DATABASE accounts;
CREATE DATABASE orchestrator;
CREATE DATABASE risk;
