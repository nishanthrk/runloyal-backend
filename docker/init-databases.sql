-- Initialize additional databases for microservices
-- This script runs when the PostgreSQL container starts for the first time

-- Create userdb database for User Service
CREATE DATABASE userdb;

-- Create addressdb database for Address Service  
CREATE DATABASE addressdb;

-- Grant privileges to postgres user for all databases
GRANT ALL PRIVILEGES ON DATABASE authdb TO postgres;
GRANT ALL PRIVILEGES ON DATABASE userdb TO postgres;
GRANT ALL PRIVILEGES ON DATABASE addressdb TO postgres;

