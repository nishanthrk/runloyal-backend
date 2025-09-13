# HOE Assessment

## Spring Boot Microservices Assignment

### Objective
Build a small-scale microservices-based system in Spring Boot that demonstrates:
1. OAuth2.0 Login (with username/password and social logins like Google/Facebook).
2. Address Update Microservice (to manage user addresses).
3. API Documentation with Swagger/OpenAPI.
4. Distributed Transaction Management (across multiple microservices, e.g., User + Address).

### Deliverables

#### Authentication & Authorization Service
- Implement using Spring Security + Spring Authorization Server.
- Support traditional login (username/password + mobile number).
- Support social login (Google, Facebook).
- Issue Access Tokens + Refresh Tokens.

#### User Profile Microservice
- CRUD APIs for managing user details.
- Integrate with Auth Service for securing APIs.

#### Address Microservice
- CRUD APIs for managing addresses (linked with User profile).
- Expose APIs via Swagger.
- Participate in distributed transactions (e.g., when updating both user and address).

#### Distributed Transaction Setup
- Implement saga pattern or outbox pattern for handling distributed transactions between User and Address microservices.
- Example use case: when a user updates their profile and address together, ensure consistency across services.

#### Swagger/OpenAPI
- Add Swagger UI for all microservices.
- Document authentication endpoints and sample request/response models.

### Technical Requirements
- Java 11+
- Spring Boot 2.7+ / 3.x
- Spring Security + Spring Authorization Server
- Spring Data JPA (PostgreSQL)
- Swagger/OpenAPI 3.0
- Distributed Transaction Approach: Saga / Outbox / Event-driven with Kafka (pick one and implement)
- Docker Compose for running services locally

### Bonus (Optional)
- Use API Gateway for routing.
- Use Redis for token/session caching.
- Use Kafka/RabbitMQ for event-driven distributed transactions.

### Assignment for the New Employee
- Set up the repo with 3 microservices (auth-service, user-service, address-service).
- Implement OAuth2.0 login with social login in auth-service.
- Create Swagger UI docs for each service.
- Implement update profile + address with distributed transaction.
- Demonstrate with Postman collection and README.md.