import { expect, test } from '@playwright/test';

const interview = {
  id: 501, applicationId: 401, jobTitle: 'Campus Software Engineer',
  scheduledAt: '2026-12-01T10:00:00Z', mode: 'ONLINE',
  locationOrLink: 'https://meeting.example.test', notes: 'Technical round',
  feedback: null, status: 'SCHEDULED',
};
const offer = {
  id: 601, applicationId: 401, jobId: 301, jobTitle: 'Campus Software Engineer',
  packageDetails: '12 LPA', packageAmount: 1200000,
  joiningDate: '2027-01-01T00:00:00Z', status: 'ISSUED',
  createdAt: '2026-11-01T10:00:00Z', updatedAt: '2026-11-01T10:00:00Z',
};

async function mockApi(page, initialRole) {
  const state = {
    role: initialRole,
    company: { id: 1, name: 'Example Campus Co', verified: true },
    jobs: [{ id: 301, title: 'Campus Software Engineer', deadline: '2026-12-20', published: true, closed: false }],
    applications: [{ applicationId: 401, studentUserId: 21, studentName: 'Test Student', email: 'student@example.test', status: 'SHORTLISTED' }],
    interviews: [{ ...interview }],
    offers: [{ ...offer }],
    drives: [{ id: 701, title: 'Example Campus Drive', companyName: 'Example Campus Co', registeredCount: 2, status: 'OPEN' }],
    users: [{ id: 1, email: 'test@example.test', role: initialRole, enabled: true }],
    companies: [{ id: 1, name: 'Example Campus Co', verified: false }],
  };
  await page.route('http://localhost:8080/api/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api', '');
    const method = route.request().method();
    const respond = data => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) });
    if (path === '/auth/register' || path === '/auth/login') {
      const request = route.request().postDataJSON();
      state.role = request.role || initialRole;
      return respond({ token: 'test-access-token', refreshToken: 'test-refresh-token', email: request.email, role: state.role });
    }
    if (path === '/auth/verification/verify') return respond({ message: 'Email verified.' });
    if (path === '/auth/logout') return respond({ message: 'Logged out.' });
    if (path === '/student/profile' && method === 'GET') return respond({
      fullName: 'Test Student', rollNumber: 'T-001', department: 'Computer Science',
      cgpa: 8.5, backlogs: 0, graduationYear: 2027, currentSemester: 7,
    });
    if (path === '/student/profile' && method === 'PUT') return respond({ fullName: 'Test Student', rollNumber: 'T-001' });
    if (path === '/student/opportunities') return respond({ content: [{ id: 301, title: 'Campus Software Engineer', companyName: 'Example Campus Co', description: 'Build software', location: 'Remote', deadline: '2026-12-20', eligible: true, saved: false }] });
    if (path === '/student/saved-jobs') return respond([]);
    if (path === '/student/applications' && method === 'GET') return respond([]);
    if (path === '/student/applications/301' && method === 'POST') return respond({ id: 401, jobTitle: 'Campus Software Engineer', status: 'APPLIED' });
    if (path === '/student/interviews') return respond(state.interviews);
    if (path === '/student/placement-drives') return respond([]);
    if (path === '/student/offers' && method === 'GET') return respond(state.offers);
    if (path === '/student/offers/601/status' && method === 'PATCH') {
      state.offers[0].status = route.request().postDataJSON().status;
      return respond(state.offers[0]);
    }
    if (path === '/student/notifications') return respond([]);
    if (path === '/recruiter/company' && method === 'GET') return respond(state.company);
    if (path === '/recruiter/company' && method === 'POST') {
      state.company = { ...route.request().postDataJSON(), verified: true };
      return respond(state.company);
    }
    if (path === '/recruiter/jobs' && method === 'GET') return respond(state.jobs);
    if (path === '/recruiter/jobs' && method === 'POST') {
      state.jobs.push({ id: 302, title: 'Campus Software Engineer', deadline: '2026-12-20', published: false });
      return respond(state.jobs.at(-1));
    }
    if (path === '/recruiter/jobs/302/publish' && method === 'POST') return respond({ ...state.jobs[1], published: true });
    if (path === '/recruiter/jobs/302/applications' || path === '/recruiter/jobs/301/applications') return respond(state.applications);
    if (path === '/recruiter/applications/401/status') return respond({ ...state.applications[0], status: 'INTERVIEW_SCHEDULED' });
    if (path === '/recruiter/applications/401/interview' && method === 'POST') {
      state.applications[0].status = 'INTERVIEW_SCHEDULED';
      state.interviews = [interview];
      return respond(interview);
    }
    if (path === '/recruiter/interviews') return respond(state.interviews);
    if (path.includes('/interviews/501') && method === 'PUT') return respond({ ...interview, status: 'RESCHEDULED' });
    if (path.includes('/interviews/501/cancel')) return respond({ ...interview, status: 'CANCELLED' });
    if (path.includes('/interviews/501/feedback')) {
      state.interviews = [{ ...interview, feedback: 'Good technical skills', status: 'COMPLETED' }];
      return respond(state.interviews[0]);
    }
    if (path === '/recruiter/applications/401/offers' && method === 'POST') {
      state.offers = [offer];
      return respond(offer);
    }
    if (path === '/recruiter/offers' && method === 'GET') return respond(state.offers);
    if (path === '/recruiter/offers/601/status' && method === 'PATCH') {
      state.offers[0].status = route.request().postDataJSON().status;
      return respond(state.offers[0]);
    }
    if (path.startsWith('/placement/')) {
      if (path === '/placement/drives' && method === 'GET') return respond(state.drives);
      if (path === '/placement/stats') return respond({ activeDrives: 1, registeredStudents: 2, selectedStudents: 1 });
      if (path === '/placement/drives' && method === 'POST') return respond(state.drives[0]);
    }
    if (path === '/admin/stats') return respond({ users: 4, students: 1, recruiters: 1, placementOfficers: 1, colleges: 1, companies: 1, drives: 1, selections: 1 });
    if (path === '/admin/users') return respond(state.users);
    if (path === '/admin/colleges') return respond([{ id: 1, code: 'TEST', name: 'Test College' }]);
    if (path === '/admin/companies') return respond(state.companies);
    if (path === '/admin/audit-logs') return respond([{ id: 1, actorEmail: 'admin@example.test', action: 'COMPANY_VERIFIED', targetType: 'Company', targetId: '1', createdAt: '2026-11-01T10:00:00Z' }]);
    if (path === '/admin/companies/1/verification' && method === 'PATCH') {
      state.companies[0].verified = route.request().postDataJSON().verified;
      return respond(state.companies[0]);
    }
    if (path === '/admin/users/1/status' && method === 'PATCH') {
      state.users[0].enabled = route.request().postDataJSON().enabled;
      return respond(state.users[0]);
    }
    return respond({});
  });
  return state;
}

async function login(page, role) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(`${role.toLowerCase()}@example.test`);
  await page.getByLabel('Password').fill('safe-test-password');
  await page.getByRole('button', { name: 'Sign in' }).click();
}
function acceptDialogs(page, values) {
  const queue = [...values];
  const handler = dialog => {
    dialog.accept(queue.shift() || '');
    if (queue.length === 0) page.off('dialog', handler);
  };
  page.on('dialog', handler);
}

test('student registers, verifies, applies, reviews interview and accepts own offer', async ({ page }) => {
  await mockApi(page, 'STUDENT');
  await page.goto('/login');
  await page.getByRole('button', { name: 'Create an account' }).click();
  await page.getByLabel('Email').fill('student@example.test');
  await page.getByLabel('Password').fill('safe-test-password');
  await page.getByLabel('Full name').fill('Test Student');
  await page.getByLabel('Roll number').fill('T-001');
  await page.getByLabel('Department').fill('Computer Science');
  await page.getByLabel('CGPA').fill('8.5');
  await page.getByLabel('Backlogs').fill('0');
  await page.getByRole('button', { name: 'Register' }).click();
  await expect(page.getByRole('heading', { name: 'Verify email' })).toBeVisible();
  await page.goto('/verify-email?token=synthetic-verification-token');
  await page.getByLabel('Token').fill('synthetic-verification-token');
  await page.getByRole('button', { name: 'Verify email' }).click();
  await expect(page.getByText('Email verified.')).toBeVisible();
  await page.goto('/login');
  await page.getByLabel('Email').fill('student@example.test');
  await page.getByLabel('Password').fill('safe-test-password');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByRole('button', { name: 'profile', exact: true }).click();
  await page.getByRole('button', { name: 'Save profile' }).click();
  await page.getByRole('button', { name: 'jobs', exact: true }).click();
  await page.getByRole('button', { name: 'Apply' }).click();
  await page.getByRole('link', { name: 'Interview details' }).click();
  await expect(page.getByText('Technical round')).toBeVisible();
  await page.getByRole('link', { name: 'My offers' }).click();
  await page.getByRole('button', { name: 'Accept' }).click();
  await expect(page.getByText(/ACCEPTED/)).toBeVisible();
});

test('recruiter manages company, posting, interview and offer routes', async ({ page }) => {
  await mockApi(page, 'RECRUITER');
  await login(page, 'RECRUITER');
  await expect(page.getByRole('heading', { name: 'Recruiter Dashboard' })).toBeVisible();
  await page.getByPlaceholder('name', { exact: true }).fill('Example Campus Co');
  await page.getByRole('button', { name: 'Save company' }).click();
  await page.locator('[name="title"]').fill('Campus Software Engineer');
  await page.locator('[name="companyName"]').fill('Example Campus Co');
  await page.locator('[name="description"]').fill('Build reliable software');
  await page.locator('[name="minCgpa"]').fill('7');
  await page.locator('[name="maxBacklogs"]').fill('0');
  await page.locator('[name="deadline"]').fill('2026-12-20');
  await page.getByRole('button', { name: 'Post job drive' }).click();
  await page.getByRole('button', { name: 'Publish' }).last().click();
  await page.getByRole('button', { name: 'Campus Software Engineer' }).last().click();
  acceptDialogs(page, ['2026-12-03T10:00:00Z', 'ONLINE', 'https://meeting.example.test']);
  await page.getByRole('button', { name: 'Schedule interview' }).click();
  acceptDialogs(page, ['12 LPA', '2027-01-01']);
  await page.getByRole('button', { name: 'Issue offer' }).click();
  await page.getByRole('link', { name: 'Interview management' }).click();
  await expect(page.getByRole('heading', { name: 'Interview Management' })).toBeVisible();
  acceptDialogs(page, ['2026-12-03T10:00:00Z']);
  await page.getByRole('button', { name: 'Reschedule' }).click();
  acceptDialogs(page, ['Good technical skills']);
  await page.getByRole('button', { name: 'Submit feedback' }).click();
  await page.goto('/recruiter/offers');
  await expect(page.getByText('12 LPA')).toBeVisible();
  await page.getByRole('button', { name: 'Withdraw' }).click();
  await expect(page.getByText(/WITHDRAWN/)).toBeVisible();
});

test('placement officer reviews drives and database-backed statistics view', async ({ page }) => {
  await mockApi(page, 'PLACEMENT_OFFICER');
  await login(page, 'PLACEMENT_OFFICER');
  await expect(page.getByRole('heading', { name: 'Placement Officer Dashboard' })).toBeVisible();
  await expect(page.getByText('Example Campus Drive')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Registrations' })).toBeVisible();
  await page.locator('[name="title"]').fill('Second Example Drive');
  await page.locator('[name="registrationDeadline"]').fill('2026-12-10');
  await page.getByRole('button', { name: 'Create placement drive' }).click();
});

test('admin manages accounts and reviews company verification and audit log', async ({ page }) => {
  await mockApi(page, 'ADMIN');
  await login(page, 'ADMIN');
  await expect(page.getByRole('heading', { name: 'Administrator Dashboard' })).toBeVisible();
  await expect(page.getByText('COMPANY_VERIFIED')).toBeVisible();
  await page.getByRole('button', { name: 'Verify' }).click();
  await expect(page.getByText('COMPANY_VERIFIED')).toBeVisible();
  await page.getByRole('button', { name: 'Deactivate' }).click();
});

test('role-protected routes redirect a student away from recruiter-only pages', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'synthetic-access-token');
    localStorage.setItem('role', 'STUDENT');
    localStorage.setItem('email', 'student@example.test');
  });
  await page.goto('/recruiter/offers');
  await expect(page).toHaveURL(/\/student$/);
});
