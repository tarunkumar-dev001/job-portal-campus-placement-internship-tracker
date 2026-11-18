import { expect, test } from '@playwright/test';

const apiBase = 'http://127.0.0.1:8080/api';
const password = `Fullstack-E2E-${Date.now()}-Pass!`;

function registrationResponseHeaders(response) {
  const headers = response.headers();
  return Object.fromEntries(Object.entries(headers)
    .filter(([name]) => name === 'content-type'
      || name === 'location'
      || /(?:request|correlation)[-_]?id$/i.test(name)
      || /^(?:traceparent|x-b3-traceid|x-amzn-trace-id|x-cloud-trace-context)$/i.test(name))
    .map(([name, value]) => {
      if (name === 'content-type') {
        return [name, value];
      }
      if (name !== 'location') {
        return [name, true];
      }
      try {
        const location = new URL(value, apiBase);
        return [name, `${location.origin}${location.pathname === '/' ? '/' : '/[redacted-path]'}${location.search ? '?[redacted-query]' : ''}`];
      } catch {
        return [name, '[redacted-location]'];
      }
    }));
}

async function requestJson(context, path, { method = 'GET', body, token, registrationRole } = {}) {
  const diagnosticPath = path === '/auth/register' ? path : '[path omitted]';
  let response;
  try {
    response = await context.fetch(`${apiBase}${path}`, {
      method,
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(body ? { 'Content-Type': 'application/json' } : {}),
      },
      ...(body ? { data: body } : {}),
    });
  } catch {
    throw new Error(`${method} ${diagnosticPath} failed before receiving a response`);
  }
  const responseBody = response.status() === 204 ? null : await response.json().catch(() => null);
  if (!response.ok()) {
    if (registrationRole) {
      console.error('[E2E] Registration request failed', JSON.stringify({
        role: registrationRole,
        status: response.status(),
        headers: registrationResponseHeaders(response),
        body: '[response body omitted for credential safety]',
      }));
    }
    throw new Error(`${method} ${diagnosticPath} failed (${response.status()}): [response body omitted for credential safety]`);
  }
  return responseBody;
}

async function verifyAndLogin(context, { email, role, userPassword = password }) {
  await requestJson(context, '/auth/verification/request', {
    method: 'POST', body: { email },
  });
  const message = await requestJson(context, `/e2e/mailbox/${email}`, {
  });
  await requestJson(context, '/auth/verification/verify', {
    method: 'POST', body: { token: message.token },
  });
  const session = await requestJson(context, '/auth/login', {
    method: 'POST', body: { email, password: userPassword },
  });
  expect(session.role).toBe(role);
  expect(session.token).toBeTruthy();
  return session;
}

async function registerAndVerify(context, { email, role, fullName, rollNumber, department, cgpa, backlogs }) {
  console.info('[E2E] Registration role:', role);
  const registered = await requestJson(context, '/auth/register', {
    method: 'POST',
    body: { email, password, role, fullName, rollNumber, department, cgpa, backlogs },
    registrationRole: role,
  });
  const hasExpectedRegistrationMessage = typeof registered?.message === 'string'
    && registered.message.includes('Account created');
  expect(hasExpectedRegistrationMessage).toBe(true);
  return verifyAndLogin(context, { email, role });
}

async function loginPage(page, email, userPassword = password) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(userPassword);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

test('provisioned placement officer can verify and access the dashboard', async ({ page, request }) => {
  const officerEmail = 'officer@e2e.test';
  const officerPassword = 'E2e-Officer-Only-Password!';
  await verifyAndLogin(request, {
    email: officerEmail,
    role: 'PLACEMENT_OFFICER',
    userPassword: officerPassword,
  });
  await loginPage(page, officerEmail, officerPassword);
  await expect(page.getByRole('heading', { name: 'Placement Officer Dashboard' })).toBeVisible();
});

test('real backend and browser cover student, recruiter, placement officer, and admin workflows', async ({ page, request, browser }) => {
  const suffix = Date.now();
  const adminEmail = 'admin@e2e.test';
  const adminLogin = await requestJson(request, '/auth/login', {
    method: 'POST',
    body: { email: adminEmail, password: 'E2e-Admin-Only-Password!' },
  });
  const adminToken = adminLogin.token;
  const studentEmail = `student-${suffix}@e2e.test`;
  const recruiterEmail = `recruiter-${suffix}@e2e.test`;
  const officerEmail = 'officer@e2e.test';
  const student = await registerAndVerify(request, {
    email: studentEmail, role: 'STUDENT', fullName: 'E2E Student', rollNumber: `E2E-${suffix}`,
    department: 'Computer Science', cgpa: 8.5, backlogs: 0,
  });
  const recruiter = await registerAndVerify(request, { email: recruiterEmail, role: 'RECRUITER' });
  const officer = await verifyAndLogin(request, {
    email: officerEmail,
    role: 'PLACEMENT_OFFICER',
    userPassword: 'E2e-Officer-Only-Password!',
  });

  const college = await requestJson(request, '/admin/colleges', {
    method: 'POST', token: adminToken,
    body: { code: `E2E${suffix}`, name: 'E2E College', active: true },
  });
  const users = await requestJson(request, '/admin/users', { token: adminToken });
  const officerRecord = users.find(user => user.email === officerEmail);
  expect(officerRecord).toBeTruthy();
  await requestJson(request, `/admin/placement-officers/${officerRecord.id}/college`, {
    method: 'PATCH', token: adminToken, body: { collegeId: college.id },
  });

  await requestJson(request, '/recruiter/company', {
    method: 'POST', token: recruiter.token,
    body: { name: `E2E Company ${suffix}`, industry: 'Software', website: 'https://example.test' },
  });
  const companies = await requestJson(request, '/admin/companies', { token: adminToken });
  const company = companies.find(item => item.name === `E2E Company ${suffix}`);
  expect(company).toBeTruthy();
  await requestJson(request, `/admin/companies/${company.id}/verification`, {
    method: 'PATCH', token: adminToken, body: { verified: true },
  });

  const drive = await requestJson(request, '/placement/drives', {
    method: 'POST', token: officer.token,
    body: {
      title: 'E2E Placement Drive', companyName: company.name, jobTitle: 'Software Engineer',
      driveType: 'FULL_TIME', minCgpa: 7, maxBacklogs: 0,
      registrationDeadline: new Date(Date.now() + 86_400_000).toISOString().slice(0, 10),
      driveDate: new Date(Date.now() + 172_800_000).toISOString().slice(0, 10),
    },
  });
  await requestJson(request, `/placement/drives/${drive.id}/status?status=OPEN`, {
    method: 'PATCH', token: officer.token,
  });

  const job = await requestJson(request, '/recruiter/jobs', {
    method: 'POST', token: recruiter.token,
    body: {
      title: 'E2E Software Engineer', description: 'Full-stack verification role', companyName: company.name,
      minCgpa: 7, maxBacklogs: 0, deadline: new Date(Date.now() + 86_400_000).toISOString().slice(0, 10),
      location: 'Remote', jobType: 'FULL_TIME', requiredSkills: 'Java, React', internship: false,
    },
  });
  await requestJson(request, `/recruiter/jobs/${job.id}/publish`, { method: 'POST', token: recruiter.token });

  await loginPage(page, studentEmail);
  await expect(page.getByRole('heading', { name: 'Student Workspace' })).toBeVisible();
  await page.evaluate(() => localStorage.setItem('token', 'expired.access.token'));
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Student Workspace' })).toBeVisible();
  await expect(page.getByText('E2E Software Engineer')).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('token'))).not.toBe('expired.access.token');
  await page.getByRole('button', { name: 'Apply' }).click();
  await expect(page.getByRole('status').filter({ hasText: /^Application submitted\.$/ })).toBeVisible();
  const application = await requestJson(request, '/recruiter/jobs', { token: recruiter.token });
  expect(application.some(item => item.id === job.id)).toBeTruthy();
  const applicants = await requestJson(request, `/recruiter/jobs/${job.id}/applications`, { token: recruiter.token });
  const applicationId = applicants[0].applicationId;
  await requestJson(request, `/recruiter/applications/${applicationId}/status`, {
    method: 'PATCH', token: recruiter.token, body: { status: 'SHORTLISTED', note: 'E2E test' },
  });
  const interviewAt = new Date(Date.now() + 2_500).toISOString();
  const interview = await requestJson(request, `/recruiter/applications/${applicationId}/interview`, {
    method: 'POST', token: recruiter.token,
    body: { scheduledAt: interviewAt, mode: 'ONLINE', locationOrLink: 'https://example.test/meeting', notes: 'E2E interview' },
  });
  const offer = await requestJson(request, `/recruiter/applications/${applicationId}/offers`, {
    method: 'POST', token: recruiter.token,
    body: { packageDetails: 'E2E offer', packageAmount: 100000, joiningDate: new Date(Date.now() + 604_800_000).toISOString() },
  });

  await page.getByRole('button', { name: 'drives', exact: true }).click();
  await page.getByRole('button', { name: 'Register' }).click();
  await Promise.all([
    '/student/profile', '/student/opportunities', '/student/saved-jobs', '/student/applications',
    '/student/interviews', '/student/notifications', '/student/placement-drives',
  ].map(path => requestJson(request, path, { token: student.token })));
  await expect(page.getByText(/Registered · REGISTERED/)).toBeVisible();
  await requestJson(request, `/placement/drives/${drive.id}/registrations/${users.find(user => user.email === studentEmail).id}/eligibility`, {
    method: 'PATCH', token: officer.token,
  });
  await requestJson(request, `/placement/drives/${drive.id}/selections`, {
    method: 'POST', token: officer.token,
    body: { studentId: users.find(user => user.email === studentEmail).id, jobTitle: 'Software Engineer', packageAmount: 100000 },
  });

  await new Promise(resolve => setTimeout(resolve, 2_600));
  await requestJson(request, `/recruiter/applications/${applicationId}/interviews/${interview.id}/feedback`, {
    method: 'POST', token: recruiter.token, body: { feedback: 'E2E feedback recorded' },
  });
  await page.goto('/student/interviews');
  await expect(page.getByText('E2E interview')).toBeVisible();
  await page.goto('/student/offers');
  await expect(page.getByText('E2E offer')).toBeVisible();
  await page.getByRole('button', { name: 'Accept' }).click();
  await expect(page.getByText(/ACCEPTED/)).toBeVisible();

  const recruiterContext = await browser.newContext({ baseURL: 'http://127.0.0.1:4174' });
  const recruiterPage = await recruiterContext.newPage();
  await loginPage(recruiterPage, recruiterEmail);
  await expect(recruiterPage.getByRole('heading', { name: 'Recruiter Dashboard' })).toBeVisible();
  await recruiterPage.goto('/recruiter/interviews');
  await expect(recruiterPage.getByRole('heading', { name: 'Interview Management' })).toBeVisible();

  const officerContext = await browser.newContext({ baseURL: 'http://127.0.0.1:4174' });
  const officerPage = await officerContext.newPage();
  await loginPage(officerPage, officerEmail, 'E2e-Officer-Only-Password!');
  await expect(officerPage.getByRole('heading', { name: 'Placement Officer Dashboard' })).toBeVisible();
  await expect(officerPage.getByText('E2E Placement Drive')).toBeVisible();

  const adminContext = await browser.newContext({ baseURL: 'http://127.0.0.1:4174' });
  const adminPage = await adminContext.newPage();
  await loginPage(adminPage, adminEmail, 'E2e-Admin-Only-Password!');
  await expect(adminPage.getByRole('heading', { name: 'Administrator Dashboard' })).toBeVisible();
  await expect(adminPage.getByText('E2E Company')).toBeVisible();
  await Promise.all([recruiterContext.close(), officerContext.close(), adminContext.close()]);
  expect(student.token).toBeTruthy();
});
