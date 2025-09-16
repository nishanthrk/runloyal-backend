# RL Backend - Microservices Architecture

A comprehensive backend system built with Spring Boot microservices, featuring authentication, user management, and address services with event-driven architecture.

## 🏗️ Architecture Overview

This project implements a microservices architecture with the following components:

- **Auth Service** (Port 8081): JWT-based authentication with OAuth2 social login
- **User Service** (Port 8082): User profile management with outbox pattern
- **Address Service** (Port 8083): Address management with Kafka event consumption
- **PostgreSQL**: Separate databases for each service
- **Apache Kafka**: Event streaming and inter-service communication
- **Redis**: Caching and session management
- **Prometheus & Grafana**: Monitoring and observability

## 🚀 Quick Start

### Prerequisites

- Java 17+
- Gradle 8.5+
- Docker & Docker Compose
- Git

### 1. Clone the Repository

```bash
git clone <repository-url>
cd rl-backend
```

### Prerequities
1, Postgres
2, Redis
3, Kafka


### Run Services Locally (Development)

```bash
# Terminal 1 - Auth Service (with OAuth2 configuration)
cd auth-service
export GOOGLE_CLIENT_ID="189314938353-c85gkd8vnu0gp1tc717hl5epvepugcgh.apps.googleusercontent.com" && export GOOGLE_CLIENT_SECRET="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" && ./gradlew bootRun

# Terminal 2 - User Service
cd user-service
./gradlew bootRun

# Terminal 3 - Address Service
cd address-service
./gradlew bootRun
```

### 4. OAuth2 Configuration

The auth service requires Google OAuth2 credentials for social login functionality. The environment variables are:

- `GOOGLE_CLIENT_ID`: Your Google OAuth2 client ID
- `GOOGLE_CLIENT_SECRET`: Your Google OAuth2 client secret

These are automatically set in the startup command above. For production, set these as environment variables or use a configuration management system.

### Run Everything with Docker

```bash
# Build and start all services
docker-compose up --build

# Or run in background
docker-compose up -d --build
```

## 📋 Service Details

### Auth Service (Port 8081)

**Features:**
- JWT token-based authentication
- OAuth2 social login (Google, GitHub, Facebook)
- Refresh token rotation
- Token revocation and blacklisting
- User registration and login
- Password validation and security

**Key Endpoints:**
- `POST /api/auth/register` - User registration
- `POST /api/auth/login` - User login
- `POST /api/auth/refresh` - Refresh access token
- `GET /api/auth/profile` - Get user profile
- `POST /api/auth/logout` - Logout user
- `GET /oauth2/authorization/{provider}` - OAuth2 login

### User Service (Port 8082)

**Features:**
- User profile CRUD operations
- Event publishing with outbox pattern
- User search and filtering
- Profile image management
- Account status management

**Key Endpoints:**
- `GET /api/users/{id}` - Get user by ID
- `PUT /api/users/{id}` - Update user profile
- `GET /api/users/search` - Search users
- `DELETE /api/users/{id}` - Deactivate user

### Address Service (Port 8083)

**Features:**
- Address CRUD operations
- Kafka event consumption
- Address validation
- Multiple addresses per user

**Key Endpoints:**
- `GET /api/addresses/user/{userId}` - Get user addresses
- `POST /api/addresses` - Create new address
- `PUT /api/addresses/{id}` - Update address
- `DELETE /api/addresses/{id}` - Delete address

## 🔧 Configuration

### Environment Variables

```bash
# Database Configuration
DB_USERNAME=postgres
DB_PASSWORD=password

# JWT Configuration
JWT_SECRET=mySecretKey123456789012345678901234567890
JWT_ACCESS_EXPIRATION=900000
JWT_REFRESH_EXPIRATION=604800000

# OAuth2 Configuration
GOOGLE_CLIENT_ID=your-google-client-id
GOOGLE_CLIENT_SECRET=your-google-client-secret
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret
FACEBOOK_CLIENT_ID=your-facebook-client-id
FACEBOOK_CLIENT_SECRET=your-facebook-client-secret

# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379
```

### OAuth2 Setup

1. **Google OAuth2:**
   - Go to [Google Cloud Console](https://console.cloud.google.com/)
   - Create a new project or select existing
   - Enable Google+ API
   - Create OAuth2 credentials
   - Add redirect URI: `http://localhost:8081/login/oauth2/code/google`


## 🔄 Event Flow

### User Registration Flow
1. User registers via Auth Service
2. Auth Service calls User Service directly via REST API (`POST /api/users`)
3. User Service creates user record in its database
4. Auth Service stores authentication credentials in its database
5. Auth Service returns JWT tokens to user

### User Update Flow
1. User updates profile via User Service
2. User Service updates database
3. User Service publishes `ProfileUpdated` event via outbox pattern to Kafka
4. Address Service consumes event and updates related address records

## 🛠️ Development

### Database Migrations

#### General Migration Commands

```bash
# Run Flyway migrations for any service
cd <service-directory>
./gradlew flywayMigrate

# Check migration status
./gradlew flywayInfo
```

#### Address Service Migration (If Tables Are Deleted)

If the address service database tables get deleted, follow these steps to recreate them:

1. **Verify PostgreSQL is running:**
   ```bash
   # Check if PostgreSQL is running locally
   ps aux | grep postgres
   ```

2. **Check database status:**
   ```bash
   # Connect to addressdb and check if tables exist
   psql -h localhost -U nishanth -d addressdb -c "\dt"
   ```

3. **Run the migration with explicit database configuration:**
   ```bash
   cd address-service
   ./gradlew flywayMigrate -Dflyway.url=jdbc:postgresql://localhost:5432/addressdb -Dflyway.user=nishanth -Dflyway.password=
   ```

4. **Verify tables were created:**
   ```bash
   # Check all tables
   psql -h localhost -U nishanth -d addressdb -c "\dt"
   
   # Check specific table structure
   psql -h localhost -U nishanth -d addressdb -c "\d addresses"
   psql -h localhost -U nishanth -d addressdb -c "\d processed_events"
   psql -h localhost -U nishanth -d addressdb -c "\d failed_events"
   ```

5. **Verify migration history:**
   ```bash
   psql -h localhost -U nishanth -d addressdb -c "SELECT version, description, installed_on FROM flyway_schema_history ORDER BY installed_rank;"
   ```

**Expected Tables After Migration:**
- `addresses` - Main address storage table
- `processed_events` - Event processing tracking for idempotency
- `failed_events` - Dead letter queue for failed event processing
- `flyway_schema_history` - Migration tracking table

### Health Checks

```bash
# Check service health
curl http://localhost:8081/health
curl http://localhost:8082/health
curl http://localhost:8083/health
```
