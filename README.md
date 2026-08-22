# Calendar

## 🚧 Under Construction

A task organizer application.

## Tech Stack

* **Backend:** Java + Spring Boot
* **Frontend:** Angular
* **Mobile:** Flutter
* **Desktop:** Java
* **Database:** PostgreSQL
* **Containerization:** Docker + Docker Compose

## Requirements

Before running the project, make sure you have installed:

* Docker
* Docker Compose

You can verify your installation with:

```bash
docker --version
docker compose version
```

## Running with Docker

Clone the repository:

```bash
git clone <repository-url>
cd Calendar
```

Create the environment file if the project provides an example:

```bash
cp .env.example .env
```

Then start the containers:

```bash
docker compose up --build
```

To run the containers in the background:

```bash
docker compose up --build -d
```

Check the running containers:

```bash
docker compose ps
```

View the logs:

```bash
docker compose logs -f
```

## Stopping the Application

Stop the containers:

```bash
docker compose down
```

To also remove the associated volumes:

```bash
docker compose down -v
```

> Removing volumes deletes the data stored by the containers, including the PostgreSQL database.

## Project Structure

```text
Calendar/
├── backend/       # Spring Boot API
├── desktop/       # Desktop application
├── frontend/      # Web application
├── mobile/        # Flutter application
├── .github/
│   └── workflows/ # CI/CD workflows
├── docker-compose.yml
└── README.md
```

## Development

The project is divided into independent applications:

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm start
```

### Mobile

```bash
cd mobile
flutter pub get
flutter run
```

### Desktop

```bash
cd desktop
mvn clean verify
```

## Testing

### Backend

```bash
cd backend
./mvnw clean verify
```

### Frontend

```bash
cd frontend
npm test
```

### Mobile

```bash
cd mobile
flutter test
```

## Code Analysis

The project uses GitHub Actions for continuous integration and CodeQL security analysis.

The CI pipeline covers:

* Backend build and tests
* Desktop build and tests
* Frontend formatting, tests and production build
* Mobile dependency resolution, analysis, tests and APK build
* CodeQL analysis for Java and JavaScript/TypeScript

## License

This project is currently under development.