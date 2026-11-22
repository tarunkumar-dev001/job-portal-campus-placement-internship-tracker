# CareerConnect

CareerConnect is a full-stack job portal and campus-placement tracker for students, recruiters, placement officers, and administrators. It supports job discovery and applications, interview and offer workflows, placement drives, notifications, dashboards, and email-based account verification.

## Technology stack

- Java 21, Spring Boot 3.5.7, Spring Security, JWT, JPA/Hibernate, Flyway, MySQL
- React 19, Vite, Tailwind CSS, React Router, Axios
- Playwright for browser and full-stack end-to-end tests
- MySQL/TiDB-compatible persistence and H2 for isolated test/demo environments

## Role features

- **Student:** Build a profile, search and save jobs or internships, apply to opportunities, track applications, schedule interviews, and review offers.
- **Recruiter:** Publish jobs, review applicants, shortlist or reject applications, manage interviews, and issue offers.
- **Placement officer:** Create placement drives, manage student registrations and selections, and monitor placement activity.
- **Admin:** Manage platform users and companies and review audit activity.
- Roles: `STUDENT`, `RECRUITER`, `PLACEMENT_OFFICER`, `ADMIN`

## Local development

1. Install Java 21, Node.js/npm, and MySQL 8.
2. Create a dedicated local database and user. Do not use production credentials.
3. Copy `.env.example` to `.env` and set database credentials, a unique random `JWT_SECRET`, and `RESUME_STORAGE_PATH`. In normal configuration, Spring Boot does not automatically load the root `.env`; export these variables in the shell or your IDE run configuration. The opt-in Spring `demo` profile is an exception: `application-demo.properties` explicitly imports the root `.env`.
4. Start the API with `.\mvnw.cmd spring-boot:run` from the repository root.
5. In `frontend`, run `npm ci`, set `VITE_API_BASE_URL=http://localhost:8080/api` in `.env.local`, then run `npm run dev`.

The application requires a configured database and secret; it intentionally has no default database password or JWT signing key. New accounts must verify their email before login or protected API access. Flyway owns schema changes and Hibernate validates mappings by default.

Verification and password-reset links are sent through Mailjet's HTTPS API by the `prod` profile. Configure Mailjet API credentials, sender identity, and HTTPS frontend URLs as documented in [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md); production startup fails fast if any required setting is missing. Mail errors return a generic service-unavailable response and do not expose provider details or tokens. The `e2e` profile uses only its isolated synthetic mailbox.

## Quick demo (local)

The demo profile is opt-in, isolated to H2, and uses synthetic accounts. Set a demo-only password in the shell, then start the backend:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'demo'
$env:APP_DEMO_SEED_ENABLED = 'true'
$env:DEMO_PASSWORD = '<set-your-own-demo-password>'
.\mvnw.cmd -Pdemo spring-boot:run
```

The demo password is not stored in the project; it must meet the application's configured length and is used for all seeded demo accounts.

Demo account emails:

- `admin@careerconnect-demo.test`
- `officer1@careerconnect-demo.test`
- `officer2@careerconnect-demo.test`
- `recruiter1@careerconnect-demo.test` through `recruiter5@careerconnect-demo.test`
- `student1@careerconnect-demo.test` through `student30@careerconnect-demo.test`

The `demo` profile cannot run with `prod`. Production activation and demo seeding are rejected by the environment safety guards.

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

The app is deployed with the backend on Render, the frontend on Netlify, and the database on TiDB Cloud. Live frontend: https://job-portal-campus-placement-internshi.netlify.app

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for the Render, Netlify, and TiDB Cloud preparation and deployment sequence, including disposable local MySQL testing.

## Current readiness

The app is deployed on Render, Netlify, and TiDB Cloud. The live site starts with no sample data; demo accounts are available only by running the demo profile locally. Current Flyway files V1–V4 were modified during earlier development. Although source Git history is available, the deployed database's applied migration history and schema remain unknown, so its original checksums and compatibility cannot be established from the current worktree alone. Phase 8 verified fresh V1–V5 migration and a synthetic V2-to-V5 upgrade on disposable MySQL 8.0.41, but that cannot establish compatibility with an unknown deployed history. No database outside that disposable instance was accessed. Mailjet sender/API setup, instance-local rate limiting, and browser-local token storage remain operational/security considerations. **Production readiness is not established.** See [PROJECT_AUDIT.md](PROJECT_AUDIT.md) for evidence and owner actions.

## License

This project is licensed under the [MIT License](LICENSE).
