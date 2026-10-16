# 🧺 LaundryLink
### Laundry Management System

LaundryLink is a full-stack, web-based laundry management system developed as a **second-year Software Engineering group project**.

Built using **Java, Spring Boot, Microsoft SQL Server, HTML, CSS, and JavaScript**, LaundryLink provides a centralized platform for managing laundry services, customer orders, pickup and delivery operations, payments, and customer support.

The project focuses on applying software engineering principles, object-oriented programming, design patterns, and database-driven application development to a practical service management system.

## ✨ Key Features

**👤 User & Account Management**
- User registration and authentication
- Account and profile management
- Role-based access control

**🧺 Laundry Order Management**
- Laundry service and item selection
- Order placement and management
- Service pricing and order tracking

**⚙️ Laundry Processing**
- Management of laundry processing stages
- Order status updates
- Processing workflow tracking

**🚚 Pickup & Delivery Management**
- Rider management
- Laundry pickup and delivery coordination
- Delivery status management

**💳 Payments & Billing**
- Payment management
- Billing and transaction records
- Promotional offers and discounts

**🎧 Customer Support**
- Customer feedback and support requests
- Case assignment and priority management
- Support status tracking and activity history

**🔔 Notifications & Status Tracking**
- Application notifications
- Order and service status updates
- Status history management

---

## 🛠️ Technology Stack

| Category | Technologies |
|---|---|
| Backend | Java 26, Spring Boot |
| Frontend | HTML, CSS, JavaScript |
| Database | Microsoft SQL Server |
| Security | Spring Security |
| Build & Dependency Management | Maven |
| Architecture | Layered Architecture, Package-by-Feature |
| Version Control | Git, GitHub |
| Development Environment | IntelliJ IDEA |

---

## 🏗️ System Architecture

LaundryLink follows a **layered architecture with package-by-feature organization**, allowing each functional module to manage its own responsibilities.

The application is structured around four primary layers:

| Layer | Responsibility |
|---|---|
| Controller | Handles incoming HTTP requests and API responses |
| Service | Implements business logic and application workflows |
| Repository | Manages database operations and data access |
| Entity | Represents application data and database relationships |

Request and response DTOs are used where appropriate to support communication between layers.

This architecture encourages separation of concerns, modular development, maintainability, and easier collaboration among team members.

### Application Flow

`Frontend → Controller → Service → Repository → SQL Server Database`

---

## 📁 Project Structure

```text
LaundryLink/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── .../LaundryLink/
│   │   │       ├── account/
│   │   │       ├── address/
│   │   │       ├── user/
│   │   │       ├── auth/
│   │   │       ├── orders/
│   │   │       ├── orderlines/
│   │   │       ├── items/
│   │   │       ├── services/
│   │   │       ├── servicepricing/
│   │   │       ├── processing/
│   │   │       ├── rider/
│   │   │       ├── payment/
│   │   │       ├── billing/
│   │   │       ├── promotion/
│   │   │       ├── support/
│   │   │       ├── status/
│   │   │       ├── log/
│   │   │       ├── notification/
│   │   │       ├── common/
│   │   │       └── config/
│   │   │
│   │   └── resources/
│   │       ├── application.properties
│   │       └── static/
│   │
│   └── test/
│
├── database/
├── scripts/
├── Documentation/
└── pom.xml
```

Each feature package contains its relevant controller, service, repository, entity, and DTO classes where applicable.

For more information, refer to the [Project Structure Documentation](https://github.com/it25103615/SE-DDD-Laundry-Management-System/blob/main/Documentation/Project-Structure.md).

---

## 🧩 Software Design Principles & Patterns

LaundryLink applies object-oriented programming principles and software design practices to organize application functionality.

### Object-Oriented Programming
- **Encapsulation:** Organizing application data and related behavior within classes.
- **Abstraction:** Separating business logic from implementation details through interfaces and service layers.
- **Inheritance:** Supporting code reuse and specialization where appropriate.
- **Polymorphism:** Allowing different implementations to be used through shared interfaces.

### Design Patterns

The project incorporates design-pattern-based approaches within its application modules, including:

**Strategy Pattern — Payment Management**

Supports the separation of payment-related behaviors into interchangeable implementations, reducing dependency on a single payment-processing approach.

**State Pattern — Workflow Management**

Supports the organization of status-dependent behaviors and transitions within application workflows.

**Repository Pattern — Data Access**

Separates database operations from application business logic using Spring Data repositories.

These approaches contribute to maintainability, flexibility, and separation of concerns.

---

## 📸 Application Screenshots

The following section is reserved for screenshots of the completed application.

### Login & Authentication
*Screenshot of the login interface*

### Customer Dashboard
*Screenshot of the customer-facing dashboard*

### Laundry Order Management
*Screenshot of the order placement or order tracking interface*

### Payment & Billing
*Screenshot of the payment interface*

### Customer Support
*Screenshot of the customer support management interface*

Screenshots can be added under a `screenshots/` directory and linked here using Markdown image references.

---

## 🚀 Getting Started

### Prerequisites

Ensure the following are installed:

- Java 26
- Apache Maven
- Microsoft SQL Server
- IntelliJ IDEA or another compatible Java IDE

### 1. Clone the Repository

```bash
git clone https://github.com/it25103615/SE-DDD-Laundry-Management-System.git
```

```bash
cd SE-DDD-Laundry-Management-System
```

### 2. Configure the Database

Create a local configuration file at:

`src/main/resources/application-local.properties`

Configure the SQL Server connection for your development environment.

Local database credentials and machine-specific authentication dependencies must not be committed to version control.

### 3. Prepare the Database

Ensure SQL Server is running and apply the required database migrations from the `database/` directory.

### 4. Run the Application

Using an installed Maven distribution:

```bash
mvn spring-boot:run
```

For the documented Windows support setup, the repository also provides:

```powershell
powershell -File scripts/Start-Support.ps1
```

### 5. Run Tests

```bash
mvn clean test
```

Database-dependent integration tests may require additional local configuration.

**Security note:** The development database includes sample records with plaintext passwords. Password hashing and secure credential management must be verified before any production deployment.

---

## 🧪 Testing & Validation

The project includes automated tests under `src/test/java`, organized according to the application package structure.

Testing and validation activities cover relevant application functionality, business logic, and database integration.

The documented local SQL Server startup evaluation recorded **34 passing tests**.

The system was also developed with consideration for input validation, exception handling, database consistency, and role-based access control.

---

## 🤝 Team Collaboration

LaundryLink was developed collaboratively by a six-member Software Engineering project team.

The project provided practical experience in:

- Collaborative software development
- Modular feature implementation
- Git and GitHub version control
- Database design and integration
- Object-oriented design and design patterns
- Testing, debugging, and integration
- Technical documentation and project presentation

Each team member contributed to the development of functional components, which were integrated into a shared application.

---

## 👥 Contributors

| Team Member | GitHub Profile | Contribution |
|---|---|---|
| Member 1 | @username | Account & User Management |
| Member 2 | @username | Order Management |
| Member 3 | @username | Laundry Processing |
| Member 4 | @username | Pickup & Delivery |
| Member 5 | @username | Payment & Billing |
| Agksheya_B | IT25200308 | Customer Support |



---

## 📚 Documentation

Additional technical documentation is available in the repository:

- [Project Structure Guide](https://github.com/it25103615/SE-DDD-Laundry-Management-System/blob/main/Documentation/Project-Structure.md)
- [Package Restructure Record](https://github.com/it25103615/SE-DDD-Laundry-Management-System/blob/main/Documentation/Package-Restructure.md)
- [Support Evaluation Guide](https://github.com/it25103615/SE-DDD-Laundry-Management-System/blob/main/Documentation/Support-Progress-Evaluation.md)

---

## 🎓 Project Background

LaundryLink was developed as part of a **second-year, first-semester Software Engineering group project**.

The project demonstrates the practical application of full-stack development, object-oriented programming, software architecture, relational database management, design patterns, and collaborative development practices.

Through the development of LaundryLink, the team gained hands-on experience in designing, implementing, integrating, and evaluating a multi-module software application.

---

**LaundryLink — Simplifying Laundry Service Management.**
