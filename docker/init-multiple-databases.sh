#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE authdb;
    CREATE DATABASE userdb;
    CREATE DATABASE addressdb;
    
    -- Grant privileges to the main user
    GRANT ALL PRIVILEGES ON DATABASE authdb TO $POSTGRES_USER;
    GRANT ALL PRIVILEGES ON DATABASE userdb TO $POSTGRES_USER;
    GRANT ALL PRIVILEGES ON DATABASE addressdb TO $POSTGRES_USER;
    
    -- Create extensions for each database
    \c authdb;
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
    
    \c userdb;
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
    
    \c addressdb;
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
EOSQL
