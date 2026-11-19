# CareerConnect

CareerConnect is a role-based job portal and campus placement tracker with a Spring Boot API and a React/Vite frontend.

## Stack and roles

- Java 21, Spring Boot 3.5.7, Spring Security, JWT, JPA/Hibernate, Flyway, MySQL
- React 19, Vite, Tailwind CSS, React Router, Axios
- Roles: `STUDENT`, `RECRUITER`, `PLACEMENT_OFFICER`, `ADMIN`

## Local development

1. Install Java 21, Node.js/npm, and MySQL 8.
2. Create a dedicated local database and user. Do not use production credentials.
3. Copy `.env.example` to `.env` and set database credentials, a unique random `JWT_SECRET`, and `RESUME_STORAGE_PATH`. In normal configuration, Spring Boot does not automatically load the root `.env`; export these variables in the shell or your IDE run configuration. The opt-in Spring `demo` profile is an exception: `application-demo.properties` explicitly imports the root `.env`.
4. Start the API with `.\mvnw.cmd spring-boot:run` from the repository root.
5. In `frontend`, run `npm ci`, set `VITE_API_BASE_URL=http://localhost:8080/api` in `.env.local`, then run `npm run dev`.

The application requires a configured database and secret; it intentionally has no default database password or JWT signing key. New accounts must verify their email before login or protected API access. Flyway owns schema changes and Hibernate validates mappings by default.

Verification and password-reset links are sent through Mailjet's HTTPS API by the `prod` profile. Configure Mailjet API credentials, sender identity, and HTTPS frontend URLs as documented in [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md); production startup fails fast if any required setting is missing. Mail errors return a generic service-unavailable response and do not expose provider details or tokens. The `e2e` profile uses only its isolated synthetic mailbox.

## Verification commands

From the repository root:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd verify
Push-Location frontend
npm.cmd ci
npm.cmd run build
npm.cmd run test:e2e
npm.cmd run test:e2e:fullstack
Pop-Location
```

The fixture Playwright tests mock API responses. The separate full-stack suite starts the real API and frontend against isolated H2. Real MySQL validation is separate; the Phase 8 suite was run against a temporary MySQL 8.0.41 instance and is documented in [PROJECT_AUDIT.md](PROJECT_AUDIT.md).

## Deployment

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for the Render, Netlify, and TiDB Cloud preparation and deployment sequence, including disposable local MySQL testing. Deployment and external-service changes have not been performed.

## Current readiness

Current Flyway files V1–V4 were modified during earlier development. Although source Git history is available, the deployed database's applied migration history and schema remain unknown, so its original checksums and compatibility cannot be established from the current worktree alone. Phase 8 verified fresh V1–V5 migration and a synthetic V2-to-V5 upgrade on disposable MySQL 8.0.41, but that cannot establish compatibility with an unknown deployed history. No database outside that disposable instance was accessed. Mailjet sender/API setup, instance-local rate limiting, and browser-local token storage remain operational/security considerations; no deployment was performed. **Production readiness is not established.** See [PROJECT_AUDIT.md](PROJECT_AUDIT.md) for evidence and owner actions.
