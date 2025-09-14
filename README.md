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
- Maven 3.8+
- Docker & Docker Compose
- Git

### 1. Clone the Repository

```bash
git clone <repository-url>
cd rl-backend
```

### 2. Start Infrastructure Services

```bash
# Start PostgreSQL, Kafka, Redis, and monitoring
docker-compose up -d auth-db user-db address-db redis kafka zookeeper prometheus grafana

# Wait for services to be healthy (check with)
docker-compose ps
```

### 3. Run Services Locally (Development)

```bash
# Terminal 1 - Auth Service
cd auth-service
./mvnw spring-boot:run

# Terminal 2 - User Service
cd user-service
./mvnw spring-boot:run

# Terminal 3 - Address Service
cd address-service
./mvnw spring-boot:run
```

### 4. Or Run Everything with Docker

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
- Geolocation support
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

2. **GitHub OAuth2:**
   - Go to GitHub Settings > Developer settings > OAuth Apps
   - Create a new OAuth App
   - Set Authorization callback URL: `http://localhost:8081/login/oauth2/code/github`

3. **Facebook OAuth2:**
   - Go to [Facebook Developers](https://developers.facebook.com/)
   - Create a new app
   - Add Facebook Login product
   - Set Valid OAuth Redirect URIs: `http://localhost:8081/login/oauth2/code/facebook`

## 📊 Monitoring & Observability

### Prometheus Metrics
Access Prometheus at: http://localhost:9090

### Grafana Dashboards
Access Grafana at: http://localhost:3000
- Username: `admin`
- Password: `admin`

### Kafka UI
Access Kafka UI at: http://localhost:8080

### API Documentation
- Auth Service: http://localhost:8081/swagger-ui.html
- User Service: http://localhost:8082/swagger-ui.html
- Address Service: http://localhost:8083/swagger-ui.html

## 🧪 Testing

### Run Unit Tests

```bash
# Test all services
./mvnw test

# Test specific service
cd auth-service
./mvnw test
```

### Integration Tests

```bash
# Run integration tests with Testcontainers
./mvnw verify
```

### API Testing with cURL

```bash
# Register a new user
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "Password123!",
    "clientId": "web-client"
  }'

# Login
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "identifier": "testuser",
    "password": "Password123!",
    "clientId": "web-client"
  }'

# Get user profile (replace TOKEN with actual JWT)
curl -X GET http://localhost:8081/api/auth/profile \
  -H "Authorization: Bearer TOKEN"
```

## 🔄 Event Flow

### User Registration Flow
1. User registers via Auth Service
2. Auth Service creates user record
3. Auth Service publishes `UserCreated` event to Kafka
4. User Service consumes event and creates user profile
5. Address Service consumes event and initializes address records

### User Update Flow
1. User updates profile via User Service
2. User Service updates database
3. User Service publishes `UserUpdated` event via outbox pattern
4. Address Service consumes event and updates related records

## 🛠️ Development

### Database Migrations

#### General Migration Commands

```bash
# Run Flyway migrations for any service
cd <service-directory>
./mvnw flyway:migrate

# Check migration status
./mvnw flyway:info
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

**Migration Versions:**
- **V1**: `init address schema` - Creates core address and processed_events tables
- **V2**: `add failed events table` - Adds failed_events table for dead letter queue functionality

### Adding New Features

1. Create feature branch
2. Implement changes with tests
3. Update API documentation
4. Test with Docker Compose
5. Submit pull request

### Code Style
- Follow Spring Boot best practices
- Use meaningful variable and method names
- Add comprehensive JavaDoc comments
- Maintain test coverage above 80%

## 🐳 Docker Commands

```bash
# Build specific service
docker-compose build auth-service

# View logs
docker-compose logs -f auth-service

# Scale services
docker-compose up -d --scale user-service=2

# Stop all services
docker-compose down

# Remove volumes (careful!)
docker-compose down -v
```

## 🔍 Troubleshooting

### Common Issues

1. **Port conflicts:**
   ```bash
   # Check what's using the port
   lsof -i :8081
   
   # Kill process if needed
   kill -9 <PID>
   ```

2. **Database connection issues:**
   ```bash
   # Check if PostgreSQL is running
   docker-compose ps auth-db
   
   # View database logs
   docker-compose logs auth-db
   ```

3. **Kafka connection issues:**
   ```bash
   # Check Kafka status
   docker-compose ps kafka
   
   # List topics
   docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092
   ```

### Health Checks

```bash
# Check service health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
```

## 📝 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## 📞 Support

For support and questions:
- Create an issue in the repository
- Check the troubleshooting section
- Review the API documentation