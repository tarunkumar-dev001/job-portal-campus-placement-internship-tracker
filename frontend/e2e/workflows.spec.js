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
    rejectionExplanation: null,
    studentApplications: [{
      id: 401, jobId: 301, jobTitle: 'Campus Software Engineer', companyName: 'Example Campus Co',
      status: 'REJECTED', appliedAt: '2026-11-01T10:00:00Z', withdrawnAt: null,
      history: [
        { status: 'APPLIED', changedAt: '2026-11-01T10:00:00Z', note: 'Application submitted' },
        {
          status: 'REJECTED', changedAt: '2026-11-03T10:00:00Z',
          rejectionExplanation: 'The role requires more production Java experience.',
        },
      ],
    }, {
      id: 402, jobId: 302, jobTitle: 'Platform Engineering Intern', companyName: 'Example Campus Co',
      status: 'APPLIED', appliedAt: '2026-11-02T10:00:00Z', withdrawnAt: '2026-11-04T10:30:00Z',
      history: [
        { status: 'APPLIED', changedAt: '2026-11-02T10:00:00Z', note: 'Application submitted' },
        { status: 'WITHDRAWN', changedAt: '2026-11-04T10:30:00Z' },
      ],
    }, {
      id: 403, jobId: 303, jobTitle: 'Backend Engineer', companyName: 'Example Campus Co',
      status: 'APPLIED', appliedAt: '2026-11-05T10:00:00Z', withdrawnAt: null, history: [],
    }],
    notifications: [
      { id: 801, title: 'Interview rescheduled', message: 'Campus Software Engineer at Example Campus Co: interview rescheduled.', type: 'INTERVIEW', readFlag: false, createdAt: '2026-11-06T10:00:00Z' },
      { id: 802, title: 'Offer received', message: 'Campus Software Engineer at Example Campus Co: a job offer is available in your offers.', type: 'OFFER', readFlag: false, createdAt: '2026-11-05T10:00:00Z' },
      { id: 803, title: 'Application update', message: 'Your application status changed.', type: 'APPLICATION', readFlag: true, createdAt: '2026-11-04T10:00:00Z' },
    ],
    failNotifications: false,
    failOpportunities: false,
    failStudentOffers: false,
    failRecruiterJobs: false,
    failPlacementDrives: false,
    failAdminUsers: false,
    notificationRequests: 0,
    interviews: [{ ...interview }],
    offers: [{ ...offer }],
    offersDelayMs: 0,
    drives: [{ id: 701, title: 'Example Campus Drive', companyName: 'Example Campus Co', registeredCount: 2, status: 'OPEN' }],
    users: [{ id: 1, email: 'test@example.test', role: initialRole, enabled: true }],
    companies: [{ id: 1, name: 'Example Campus Co', verified: false }],
    analyticsCalls: {},
    getCalls: {},
    mutationCalls: {},
    mutationDelays: {},
    mutationErrors: {},
    failedMutations: {},
    authRegisterError: false,
    analyticsDelayMs: 0,
    failAnalytics: false,
    analytics: {
      ADMIN: {
        users: 15, students: 12, recruiters: 1, placementOfficers: 1, colleges: 1, companies: 1, drives: 1, selections: 1,
        activeJobs: 2, activeInternships: 1, totalApplications: 12, uniqueStudentsPlaced: 3, placementPercentage: 25,
        applicationsByStatus: { APPLIED: 7, REJECTED: 5 },
        recentActivity: [],
      },
      PLACEMENT_OFFICER: {
        activeDrives: 1, registeredStudents: 2, selectedStudents: 1, totalStudents: 24,
        totalDrives: 3, totalApplications: 12, totalDriveRegistrations: 8, uniqueStudentsShortlisted: 6,
        uniqueStudentsPlaced: 3, unplacedStudents: 21, placementPercentage: 12.5, rejectedApplications: 2,
        applicationsByStatus: { APPLIED: 7, REJECTED: 2 }, studentsByDepartment: { 'Computer Science': 18 },
        selectionsByDepartment: { 'Computer Science': 2 },
        rejectionReasons: { 'Eligibility criteria': 2 },
      },
      RECRUITER: {
        totalJobs: 3, totalInternships: 2, activeJobPostings: 4, totalApplicants: 5,
        shortlistedCandidates: 2, rejectedCandidates: 1, scheduledInterviews: 1, offersIssued: 2,
        acceptedOffers: 1, declinedOffers: 1, applicationsByStatus: { SHORTLISTED: 2, REJECTED: 1 },
      },
      STUDENT: {
        totalApplications: 2, upcomingInterviews: 1, offersReceived: 1, acceptedOffers: 0,
        declinedOffers: 0, savedJobs: 3, applicationsByStatus: { APPLIED: 1, INTERVIEW_SCHEDULED: 1 },
        recentApplicationActivity: [{
          jobTitle: 'Campus Software Engineer', companyName: 'Example Campus Co', status: 'APPLIED',
          changedAt: '2026-11-01T10:00:00Z', note: 'Application submitted',
        }],
      },
    },
  };
  await page.route('http://localhost:8080/api/**', async route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api', '');
    const method = route.request().method();
    const respond = data => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(data) });
    const statsResponse = role => {
      state.analyticsCalls[role] = (state.analyticsCalls[role] || 0) + 1;
      return (async () => {
        if (state.analyticsDelayMs) await new Promise(resolve => setTimeout(resolve, state.analyticsDelayMs));
        if (state.failAnalytics) {
          return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'Analytics unavailable' }) });
        }
        return respond(state.analytics[role]);
      })();
    };
    if (method !== 'GET') {
      const mutationKey = `${method} ${path}`;
      state.mutationCalls[mutationKey] = (state.mutationCalls[mutationKey] || 0) + 1;
      if (state.mutationDelays[mutationKey]) {
        await new Promise(resolve => setTimeout(resolve, state.mutationDelays[mutationKey]));
      }
      if (state.mutationErrors[mutationKey]) {
        return route.fulfill({
          status: state.mutationErrors[mutationKey].status || 400,
          contentType: 'application/json',
          body: JSON.stringify(state.mutationErrors[mutationKey]),
        });
      }
      if (state.failedMutations[mutationKey]) {
        return route.fulfill({
          status: 503,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'Temporary mutation failure' }),
        });
      }
    } else {
      const requestKey = `${method} ${path}`;
      state.getCalls[requestKey] = (state.getCalls[requestKey] || 0) + 1;
    }
    if (path === '/auth/register' || path === '/auth/login') {
      const request = route.request().postDataJSON();
      if (path === '/auth/register' && state.authRegisterError) {
        return route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: JSON.stringify({
            message: 'Registration could not be completed.',
            fieldErrors: { email: 'This email is already registered.' },
          }),
        });
      }
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
    if (path === '/student/opportunities') {
      if (state.failOpportunities) {
        return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'Opportunities unavailable' }) });
      }
      const search = url.searchParams.get('search') || '';
      if (state.opportunityDelays?.[search]) {
        await new Promise(resolve => setTimeout(resolve, state.opportunityDelays[search]));
      }
      if (state.opportunityResponses?.[search]) return respond(state.opportunityResponses[search]);
      return respond({ content: [{ id: 301, title: 'Campus Software Engineer', companyName: 'Example Campus Co', description: 'Build software', location: 'Remote', deadline: '2026-12-20', eligible: true, saved: false }] });
    }
    if (path === '/student/saved-jobs') return respond([]);
    if (path === '/student/applications' && method === 'GET') return respond(state.studentApplications);
    if (path === '/student/applications/301' && method === 'POST') return respond({ id: 401, jobTitle: 'Campus Software Engineer', status: 'APPLIED' });
    if (path === '/student/interviews') return respond(state.interviews);
    if (path === '/student/placement-drives') return respond([]);
    if (path === '/student/resume' && method === 'POST') return respond({ resumeUrl: 'resume.pdf' });
    if (path === '/student/offers' && method === 'GET') {
      if (state.offersDelayMs) await new Promise(resolve => setTimeout(resolve, state.offersDelayMs));
      if (state.failStudentOffers) {
        return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'Offers unavailable' }) });
      }
      return respond(state.offers);
    }
    if (path === '/student/offers/601/status' && method === 'PATCH') {
      state.offers[0].status = route.request().postDataJSON().status;
      return respond(state.offers[0]);
    }
    if (path === '/student/notifications' && method === 'GET') {
      state.notificationRequests += 1;
      if (state.failNotifications) {
        return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'Notifications unavailable' }) });
      }
      return respond(state.notifications);
    }
    if (path === '/student/notifications/read-all' && method === 'PATCH') {
      state.notifications.forEach(notification => { notification.readFlag = true; });
      return route.fulfill({ status: 204, body: '' });
    }
    if (/^\/student\/notifications\/\d+\/read$/.test(path) && method === 'PATCH') {
      const notificationId = Number(path.split('/')[3]);
      const notification = state.notifications.find(item => item.id === notificationId);
      if (notification) notification.readFlag = true;
      return respond(notification);
    }
    if (path === '/recruiter/company' && method === 'GET') return respond(state.company);
    if (path === '/recruiter/company' && method === 'POST') {
      state.company = { ...route.request().postDataJSON(), verified: true };
      return respond(state.company);
    }
    if (path === '/recruiter/jobs' && method === 'GET') {
      if (state.failRecruiterJobs) {
        return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'Job postings unavailable' }) });
      }
      return respond(state.jobs);
    }
    if (path === '/recruiter/jobs' && method === 'POST') {
      state.jobs.push({ id: 302, title: 'Campus Software Engineer', deadline: '2026-12-20', published: false });
      return respond(state.jobs.at(-1));
    }
    if (path === '/recruiter/jobs/302/publish' && method === 'POST') return respond({ ...state.jobs[1], published: true });
    if (/^\/recruiter\/jobs\/\d+\/applications$/.test(path)) {
      const jobId = Number(path.split('/')[3]);
      if (state.applicationDelays?.[jobId]) {
        await new Promise(resolve => setTimeout(resolve, state.applicationDelays[jobId]));
      }
      return respond(state.applicationsByJob?.[jobId] || state.applications);
    }
    if (path === '/recruiter/applications/401/status' && method === 'PATCH') {
      const request = route.request().postDataJSON();
      state.applications[0].status = request.status;
      state.rejectionExplanation = request.rejectionExplanation || null;
      return respond(state.applications[0]);
    }
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
      state.offers = [{ ...offer }];
      return respond(state.offers[0]);
    }
    if (path === '/recruiter/offers' && method === 'GET') return respond(state.offers);
    if (path === '/recruiter/offers/601/status' && method === 'PATCH') {
      state.offers[0].status = route.request().postDataJSON().status;
      return respond(state.offers[0]);
    }
    if (path.startsWith('/placement/')) {
      if (path === '/placement/drives' && method === 'GET') {
        if (state.failPlacementDrives) {
          return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'Placement drives unavailable' }) });
        }
        return respond(state.drives);
      }
      if (path === '/placement/stats') return statsResponse('PLACEMENT_OFFICER');
      if (path === '/placement/drives' && method === 'POST') return respond(state.drives[0]);
    }
    if (path === '/admin/stats') return statsResponse('ADMIN');
    if (path === '/recruiter/stats') return statsResponse('RECRUITER');
    if (path === '/student/stats') return statsResponse('STUDENT');
    if (path === '/admin/users') {
      if (state.failAdminUsers) {
        return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ message: 'Users unavailable' }) });
      }
      return respond(state.users);
    }
    if (path === '/admin/colleges' && method === 'GET') return respond(
      Array.from({ length: state.analytics.ADMIN.colleges }, (_, index) => ({ id: index + 1, code: `TEST${index + 1}`, name: `Test College ${index + 1}` })),
    );
    if (path === '/admin/colleges' && method === 'POST') {
      state.analytics.ADMIN.colleges += 1;
      return respond({ id: state.analytics.ADMIN.colleges, ...route.request().postDataJSON() });
    }
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
test('student registers, verifies, applies, reviews interview and accepts own offer', async ({ page }) => {
  const state = await mockApi(page, 'STUDENT');
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
  await expect(page.getByRole('heading', { name: 'Student Workspace' })).toBeVisible();
  for (const label of ['Applications', 'Upcoming interviews', 'Offers received', 'Accepted offers', 'Declined offers', 'Saved jobs']) {
    await expect(page.getByRole('region', { name: 'Dashboard analytics' }).getByText(label, { exact: true })).toBeVisible();
  }
  await expect(page.getByRole('region', { name: 'Dashboard analytics' }).getByText('2', { exact: true })).toBeVisible();
  await expect(page.getByText('Application submitted', { exact: true })).toBeVisible();
  await expect(page.getByRole('figure', { name: 'Application progress' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Interviews and offer summary' })).toBeVisible();
  await expect.poll(() => state.analyticsCalls.STUDENT).toBeGreaterThan(0);
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

test('registration validation exposes associated errors, keyboard focus, server field errors, and preserves values', async ({ page }) => {
  const state = await mockApi(page, 'STUDENT');
  await page.goto('/login');
  await page.getByRole('button', { name: 'Create an account' }).click();
  const email = page.getByLabel('Email');
  await page.getByRole('button', { name: 'Register' }).click();
  await expect(email).toHaveAttribute('aria-invalid', 'true');
  await expect(email).toHaveAttribute('aria-describedby', 'auth-email-error');
  await expect(page.getByText('Email is required.')).toBeVisible();
  await expect(email).toBeFocused();

  await email.fill('not-an-email');
  await page.getByLabel('Password').fill('safe-test-password');
  await page.getByLabel('Full name').fill('Test Student');
  await page.getByLabel('Roll number').fill('T-001');
  await page.getByLabel('Department').fill('Computer Science');
  await page.getByLabel('CGPA').fill('11');
  await page.getByLabel('Backlogs').fill('0');
  await page.getByRole('button', { name: 'Register' }).click();
  await expect(page.getByText('Enter a valid email.')).toBeVisible();
  await expect(email).toHaveAttribute('aria-invalid', 'true');

  await email.fill('student@example.test');
  await page.getByLabel('CGPA').fill('8.5');
  const password = page.getByLabel('Password');
  await password.fill('short');
  await page.getByRole('button', { name: 'Register' }).click();
  await expect(page.getByText('Password must contain at least 12 characters.')).toBeVisible();
  await password.fill('safe-test-password');
  state.authRegisterError = true;
  await page.getByRole('button', { name: 'Register' }).click();
  await expect(page.getByRole('alert')).toContainText('Registration could not be completed.');
  await expect(page.getByText('This email is already registered.')).toBeVisible();
  await expect(email).toHaveValue('student@example.test');
  await expect(page.getByLabel('Full name')).toHaveValue('Test Student');

  state.authRegisterError = false;
  await page.getByRole('button', { name: 'Register' }).click();
  await expect(page.getByRole('heading', { name: 'Verify email' })).toBeVisible();
});

test('recruiter manages company, posting, interview and offer routes', async ({ page }) => {
  const state = await mockApi(page, 'RECRUITER');
  await login(page, 'RECRUITER');
  await expect(page.getByRole('heading', { name: 'Recruiter Dashboard' })).toBeVisible();
  for (const label of ['Jobs', 'Internships', 'Active postings', 'Applicants', 'Shortlisted candidates',
    'Rejected candidates', 'Scheduled interviews', 'Offers issued', 'Accepted offers', 'Declined offers']) {
    await expect(page.getByRole('region', { name: 'Dashboard analytics' }).getByText(label, { exact: true })).toBeVisible();
  }
  await expect(page.getByText('4', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Recruitment pipeline milestones' })).toBeVisible();
  await expect(page.getByText(/Independent counts, not a conversion funnel/)).toBeVisible();
  await expect.poll(() => state.analyticsCalls.RECRUITER).toBeGreaterThan(0);
  await page.getByLabel('Name').fill('Example Campus Co');
  await page.getByRole('button', { name: 'Save company' }).click();
  await page.locator('[name="title"]').fill('Campus Software Engineer');
  await page.locator('[name="companyName"]').fill('Example Campus Co');
  await page.locator('#job-description').fill('Build reliable software');
  await page.locator('[name="minCgpa"]').fill('7');
  await page.locator('[name="maxBacklogs"]').fill('0');
  await page.locator('[name="deadline"]').fill('2026-12-20');
  await page.getByRole('button', { name: 'Post job drive' }).click();
  await page.getByRole('button', { name: 'Publish' }).last().click();
  await page.getByRole('button', { name: 'Campus Software Engineer' }).last().click();
  await page.getByRole('button', { name: 'Schedule interview' }).click();
  const scheduledAt = page.getByLabel('Interview date and time');
  await page.getByRole('button', { name: 'Submit' }).click();
  await expect(scheduledAt).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByText('Interview date and time is required.')).toBeVisible();
  await scheduledAt.fill('2026-12-03T10:00');
  await page.getByLabel('Interview mode').fill('ONLINE');
  await page.getByLabel('Meeting link or location').fill('https://meeting.example.test');
  await page.getByRole('button', { name: 'Submit' }).click();
  await page.getByRole('button', { name: 'Issue offer' }).click();
  const offerPackage = page.getByLabel('Offer package details');
  await page.getByRole('button', { name: 'Submit' }).click();
  await expect(offerPackage).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByText('Offer package details is required.')).toBeVisible();
  await offerPackage.fill('12 LPA');
  await page.getByLabel('Joining date').fill('2027-01-01');
  await page.getByRole('button', { name: 'Submit' }).click();
  await page.getByRole('link', { name: 'Interview management' }).click();
  await expect(page.getByRole('heading', { name: 'Interview Management' })).toBeVisible();
  await page.getByRole('button', { name: 'Reschedule' }).click();
  await page.getByLabel('New interview date and time').fill('2026-12-04T10:00');
  await page.getByRole('button', { name: 'Save schedule' }).click();
  await page.getByRole('button', { name: 'Add feedback' }).click();
  await page.getByLabel('Interview feedback').fill('Good technical skills');
  await page.getByRole('button', { name: 'Save feedback' }).click();
  await page.goto('/recruiter/offers');
  await expect(page.getByText('12 LPA')).toBeVisible();
  await page.getByRole('button', { name: 'Withdraw' }).click();
  await expect(page.getByText(/WITHDRAWN/)).toBeVisible();
});

test('student application submission shows pending, rejects duplicates, and can retry a failure', async ({ page }) => {
  const state = await mockApi(page, 'STUDENT');
  const key = 'POST /student/applications/301';
  state.failedMutations[key] = true;
  state.mutationDelays[key] = 1200;
  await login(page, 'STUDENT');

  const apply = page.getByRole('button', { name: 'Apply' });
  await apply.click();
  await expect(apply).toHaveText('Applying…');
  await expect(apply).toBeDisabled();
  await apply.evaluate(button => { button.click(); button.click(); });
  await expect.poll(() => state.mutationCalls[key]).toBe(1);
  await expect(page.getByRole('alert')).toContainText('Temporary mutation failure');
  await expect(apply).toHaveText('Apply');
  await expect(apply).toBeEnabled();

  state.failedMutations[key] = false;
  await apply.click();
  await expect(page.getByText('Application submitted.', { exact: true })).toBeVisible();
  await expect.poll(() => state.mutationCalls[key]).toBe(2);
});

test('student mutation completion after navigation does not trigger stale dashboard reloads', async ({ page }) => {
  const state = await mockApi(page, 'STUDENT');
  const key = 'POST /student/applications/301';
  state.mutationDelays[key] = 350;
  await login(page, 'STUDENT');
  await expect.poll(() => state.getCalls['GET /student/applications'] || 0).toBeGreaterThan(0);
  const initialApplicationsReads = state.getCalls['GET /student/applications'];

  const applicationResponse = page.waitForResponse(response =>
    response.request().method() === 'POST' && response.url().includes('/student/applications/301'));
  await page.getByRole('button', { name: 'Apply' }).click();
  await page.getByRole('link', { name: 'Interview details' }).click();
  await expect(page.getByRole('heading', { name: 'My Interviews' })).toBeVisible();
  await applicationResponse;
  expect(state.getCalls['GET /student/applications']).toBe(initialApplicationsReads);
});

test('student offer responses show pending, reject duplicate clicks, and allow retry', async ({ page }) => {
  const state = await mockApi(page, 'STUDENT');
  const key = 'PATCH /student/offers/601/status';
  state.failedMutations[key] = true;
  state.mutationDelays[key] = 1000;
  await login(page, 'STUDENT');
  await page.goto('/student/offers');
  await expect(page.getByRole('heading', { name: 'My Offers' })).toBeVisible();
  await expect(page.getByText('Campus Software Engineer', { exact: true })).toBeVisible();

  const accept = page.locator('article').filter({ hasText: 'Campus Software Engineer' }).locator('button').first();
  const acceptPromise = accept.click();
  await expect(accept).toHaveText('Saving…');
  await expect(accept).toBeDisabled();
  await accept.evaluate(button => button.click());
  await expect.poll(() => state.mutationCalls[key]).toBe(1);
  await expect(page.getByRole('alert')).toContainText('Temporary mutation failure');
  await expect(accept).toBeEnabled();

  state.failedMutations[key] = false;
  await accept.click();
  await expect(page.getByText('Offer accepted.', { exact: true })).toBeVisible();
  await acceptPromise;
  await expect.poll(() => state.offers[0].status).toBe('ACCEPTED');
  await expect.poll(() => state.mutationCalls[key]).toBe(2);
});

test('recruiter submits a labeled rejection explanation', async ({ page }) => {
  const state = await mockApi(page, 'RECRUITER');
  const key = 'PATCH /recruiter/applications/401/status';
  state.failedMutations[key] = true;
  state.mutationDelays[key] = 1000;
  await login(page, 'RECRUITER');
  await page.getByRole('button', { name: 'Campus Software Engineer' }).last().click();
  await page.getByRole('combobox').selectOption('REJECTED');
  const explanation = 'The role requires more production Java experience.';
  const explanationField = page.getByLabel('Rejection explanation');
  const save = page.getByRole('button', { name: /Save rejection|Saving/ });
  await save.click();
  await expect(explanationField).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByText('Rejection explanation is required.')).toBeVisible();
  await explanationField.fill(explanation);
  const savePromise = save.click();
  await expect(save).toHaveText('Saving…');
  await expect(save).toBeDisabled();
  await save.evaluate(button => button.click());
  await expect.poll(() => state.mutationCalls[key]).toBe(1);
  await expect(page.getByRole('alert')).toContainText('Temporary mutation failure');
  await expect(save).toBeEnabled();

  state.failedMutations[key] = false;
  await save.click();
  await expect(page.getByText('Application status updated.', { exact: true })).toBeVisible();
  await savePromise;
  await expect.poll(() => state.rejectionExplanation).toBe(explanation);
  await expect.poll(() => state.mutationCalls[key]).toBe(2);
});

test('recruiter applicant forms stay outside the table scroller on mobile', async ({ page }) => {
  await mockApi(page, 'RECRUITER');
  await page.setViewportSize({ width: 375, height: 812 });
  await login(page, 'RECRUITER');
  await page.getByRole('button', { name: 'Campus Software Engineer' }).last().click();

  const applicantsTable = page.getByRole('table');
  await expect(applicantsTable.getByRole('columnheader', { name: 'Student' })).toBeVisible();
  const tableScroller = page.locator('.overflow-x-auto').filter({ has: applicantsTable });
  const scheduleButton = page.getByRole('button', { name: 'Schedule interview' });
  await scheduleButton.click();

  const scheduledAt = page.getByLabel('Interview date and time');
  const scheduleForm = page.locator('form').filter({ has: scheduledAt });
  await expect(scheduledAt).toBeVisible();
  await scheduleForm.scrollIntoViewIfNeeded();
  for (const width of [375, 390]) {
    await page.setViewportSize({ width, height: 812 });
    const layout = await scheduledAt.evaluate(field => ({
      viewportWidth: window.innerWidth,
      fieldRight: field.getBoundingClientRect().right,
      insideTableScroller: Boolean(field.closest('.overflow-x-auto')),
    }));
    expect(layout.insideTableScroller).toBe(false);
    expect(layout.fieldRight).toBeLessThanOrEqual(layout.viewportWidth);
    expect(await tableScroller.evaluate(element => element.scrollWidth))
      .toBeLessThanOrEqual(await tableScroller.evaluate(element => element.clientWidth));
    await expect(scheduleForm.getByRole('button', { name: 'Submit' })).toBeVisible();
  }

  await page.locator('#application-status-401').selectOption('REJECTED');
  const explanation = page.getByLabel('Rejection explanation');
  const rejectionForm = page.locator('form').filter({ has: explanation });
  await expect(explanation).toBeVisible();
  await rejectionForm.scrollIntoViewIfNeeded();
  for (const width of [375, 390]) {
    await page.setViewportSize({ width, height: 812 });
    const layout = await explanation.evaluate(field => ({
      viewportWidth: window.innerWidth,
      fieldRight: field.getBoundingClientRect().right,
      insideTableScroller: Boolean(field.closest('.overflow-x-auto')),
    }));
    expect(layout.insideTableScroller).toBe(false);
    expect(layout.fieldRight).toBeLessThanOrEqual(layout.viewportWidth);
    expect(await tableScroller.evaluate(element => element.scrollWidth))
      .toBeLessThanOrEqual(await tableScroller.evaluate(element => element.clientWidth));
    await expect(rejectionForm.getByRole('button', { name: 'Save rejection' })).toBeVisible();
  }

  await page.setViewportSize({ width: 1280, height: 900 });
  await expect(applicantsTable.getByRole('columnheader', { name: 'Job' })).toBeVisible();
  await expect(applicantsTable.getByRole('columnheader', { name: 'Status' })).toBeVisible();
  await expect(scheduleForm).toBeVisible();
  expect(await tableScroller.evaluate(element => element.scrollWidth))
    .toBeLessThanOrEqual(await tableScroller.evaluate(element => element.clientWidth));
});

test('recruiter interview rescheduling shows pending, prevents duplicates, and retries failures', async ({ page }) => {
  const state = await mockApi(page, 'RECRUITER');
  const key = 'PUT /recruiter/applications/401/interviews/501';
  state.failedMutations[key] = true;
  state.mutationDelays[key] = 1000;
  await login(page, 'RECRUITER');
  await page.getByRole('link', { name: 'Interview management' }).click();

  const interviewRow = page.locator('article').filter({ hasText: 'ONLINE' });
  const reschedule = interviewRow.locator('button').first();
  await reschedule.click();
  const scheduledAt = page.getByLabel('New interview date and time');
  await scheduledAt.fill('2026-12-02T10:00');
  const rescheduleForm = page.locator('form').filter({ has: scheduledAt });
  const save = rescheduleForm.getByRole('button', { name: /Save schedule|Saving/ });
  const reschedulePromise = save.click();
  await expect(save).toHaveText('Saving…');
  await expect(save).toBeDisabled();
  await save.evaluate(button => button.click());
  await expect.poll(() => state.mutationCalls[key]).toBe(1);
  await expect(page.getByRole('alert')).toContainText('Temporary mutation failure');
  await expect(save).toBeEnabled();

  state.failedMutations[key] = false;
  await scheduledAt.fill('2026-12-03T10:00');
  await save.click();
  await expect(page.getByText('Interview rescheduled.', { exact: true })).toBeVisible();
  await reschedulePromise;
  await expect.poll(() => state.mutationCalls[key]).toBe(2);
});

test('recruiter job creation shows pending, prevents duplicate posts, and retries failures', async ({ page }) => {
  const state = await mockApi(page, 'RECRUITER');
  const key = 'POST /recruiter/jobs';
  state.failedMutations[key] = true;
  state.mutationDelays[key] = 1200;
  await login(page, 'RECRUITER');

  await page.locator('[name="title"]').fill('Reliable Campus Engineer');
  await page.locator('[name="companyName"]').fill('Example Campus Co');
  await page.locator('#job-description').fill('Build reliable software.');
  await page.locator('[name="minCgpa"]').fill('7');
  await page.locator('[name="maxBacklogs"]').fill('0');
  await page.locator('[name="deadline"]').fill('2026-12-20');
  const submit = page.getByRole('button', { name: /Post job drive|Posting/ });
  const submitPromise = submit.evaluate(button => { button.click(); button.click(); });
  await expect(submit).toHaveText('Posting…');
  await expect(submit).toBeDisabled();
  await expect.poll(() => state.mutationCalls[key]).toBe(1);
  await expect(page.getByRole('alert')).toContainText('Temporary mutation failure');
  await submitPromise;
  await expect(submit).toHaveText('Post job drive');
  await expect(submit).toBeEnabled();

  state.failedMutations[key] = false;
  await submit.click();
  await expect(page.getByText('Job posting created.', { exact: true })).toBeVisible();
  await expect.poll(() => state.mutationCalls[key]).toBe(2);
});

test('job creation maps server validation errors to fields and preserves entered values', async ({ page }) => {
  const state = await mockApi(page, 'RECRUITER');
  const key = 'POST /recruiter/jobs';
  state.mutationErrors[key] = {
    message: 'A posting with this title already exists.',
    errors: { title: 'Choose a different job title.' },
  };
  await login(page, 'RECRUITER');

  await page.locator('#job-title').fill('Campus Engineer');
  await page.locator('#job-company').fill('Example Campus Co');
  await page.locator('#job-description').fill('Build reliable software.');
  await page.locator('#job-min-cgpa').fill('7');
  await page.locator('#job-max-backlogs').fill('0');
  await page.locator('#job-deadline').fill('2026-12-20');
  await page.getByRole('button', { name: 'Post job drive' }).click();
  await expect(page.getByRole('alert')).toContainText('A posting with this title already exists.');
  await expect(page.getByText('Choose a different job title.')).toBeVisible();
  await expect(page.locator('#job-title')).toHaveValue('Campus Engineer');

  delete state.mutationErrors[key];
  await page.getByRole('button', { name: 'Post job drive' }).click();
  await expect(page.getByText('Job posting created.', { exact: true })).toBeVisible();
  await expect.poll(() => state.mutationCalls[key]).toBe(2);
});

test('placement drive creation shows pending, prevents duplicates, and retries failures', async ({ page }) => {
  const state = await mockApi(page, 'PLACEMENT_OFFICER');
  const key = 'POST /placement/drives';
  state.failedMutations[key] = true;
  state.mutationDelays[key] = 1200;
  await login(page, 'PLACEMENT_OFFICER');

  await page.locator('[name="title"]').fill('Autumn Internship Drive');
  await page.locator('[name="registrationDeadline"]').fill('2026-12-10');
  const submit = page.getByRole('button', { name: /Create placement drive|Creating drive/ });
  const submitPromise = submit.evaluate(button => { button.click(); button.click(); });
  await expect(submit).toHaveText('Creating drive…');
  await expect(submit).toBeDisabled();
  await expect.poll(() => state.mutationCalls[key]).toBe(1);
  await expect(page.getByRole('alert')).toContainText('Temporary mutation failure');
  await submitPromise;
  await expect(submit).toHaveText('Create placement drive');
  await expect(submit).toBeEnabled();

  state.failedMutations[key] = false;
  await submit.click();
  await expect(page.getByText('Placement drive created.', { exact: true })).toBeVisible();
  await expect.poll(() => state.mutationCalls[key]).toBe(2);
});

test('placement drive form identifies required fields and rejects an out-of-range CGPA', async ({ page }) => {
  await mockApi(page, 'PLACEMENT_OFFICER');
  await login(page, 'PLACEMENT_OFFICER');
  const title = page.locator('#drive-title');
  await page.getByRole('button', { name: 'Create placement drive' }).click();
  await expect(title).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByText('Title is required.')).toBeVisible();

  await title.fill('Campus Internship Drive');
  await page.getByLabel('Registration Deadline').fill('2026-12-20');
  const minCgpa = page.getByLabel('Minimum CGPA');
  await minCgpa.fill('10.1');
  await page.getByRole('button', { name: 'Create placement drive' }).click();
  await expect(page.getByText('Minimum CGPA must be no more than 10.')).toBeVisible();

  await minCgpa.fill('9.5');
  await page.getByRole('button', { name: 'Create placement drive' }).click();
  await expect(page.getByText('Placement drive created.', { exact: true })).toBeVisible();
});

test('admin company verification shows pending, prevents duplicates, and retries failures', async ({ page }) => {
  const state = await mockApi(page, 'ADMIN');
  const key = 'PATCH /admin/companies/1/verification';
  state.failedMutations[key] = true;
  state.mutationDelays[key] = 1200;
  await login(page, 'ADMIN');

  const verify = page.getByRole('button', { name: /Verify|Saving/ });
  const verifyPromise = verify.evaluate(button => { button.click(); button.click(); });
  await expect(verify).toHaveText('Saving…');
  await expect(verify).toBeDisabled();
  await expect.poll(() => state.mutationCalls[key]).toBe(1);
  await expect(page.getByRole('alert')).toContainText('Temporary mutation failure');
  await verifyPromise;
  await expect(verify).toHaveText('Verify');
  await expect(verify).toBeEnabled();

  state.failedMutations[key] = false;
  await verify.click();
  await expect(page.getByText('Company verified.', { exact: true })).toBeVisible();
  await expect.poll(() => state.mutationCalls[key]).toBe(2);
});

test('student sees status notes, rejection explanations, and withdrawn applications', async ({ page }) => {
  await mockApi(page, 'STUDENT');
  await login(page, 'STUDENT');
  await page.getByRole('button', { name: 'applications', exact: true }).click();
  await expect(page.getByText('Application timeline', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('Status note:', { exact: false }).first()).toBeVisible();
  await expect(page.getByText('Application submitted', { exact: true })).toBeVisible();
  await expect(page.getByText('Rejection explanation:', { exact: false })).toBeVisible();
  await expect(page.getByText(/The role requires more production Java experience\./)).toBeVisible();
  await expect(page.locator('time[datetime="2026-11-03T10:00:00Z"]')).toBeVisible();
  await expect(page.getByText('WITHDRAWN', { exact: true })).toBeVisible();
  await expect(page.getByText(/^Withdrawn on /)).toBeVisible();
  await expect(page.getByText('No application history is available yet.')).toBeVisible();
});

test('student can mark notifications individually and all at once', async ({ page }) => {
  const state = await mockApi(page, 'STUDENT');
  await login(page, 'STUDENT');
  await expect(page.getByRole('region', { name: 'Dashboard analytics' })).toBeVisible();
  await page.getByRole('button', { name: /notifications/i }).click();
  await expect(page.getByText('2 unread', { exact: true })).toBeVisible();
  await expect(page.locator('article[aria-label="Unread notification"]')).toHaveCount(2);
  await expect(page.locator('article[aria-label="Read notification"]')).toHaveCount(1);

  await page.getByRole('button', { name: 'Mark Interview rescheduled as read' }).click();
  await expect.poll(() => state.notifications.find(item => item.id === 801).readFlag).toBe(true);
  await expect(page.getByText('1 unread', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Mark all as read' }).click();
  await expect.poll(() => state.notifications.every(item => item.readFlag)).toBe(true);
  await expect(page.getByText('0 unread', { exact: true })).toBeVisible();
  await expect(page.locator('article[aria-label="Unread notification"]')).toHaveCount(0);
  await expect(page.locator('article[aria-label="Read notification"]')).toHaveCount(3);

  await page.getByRole('button', { name: 'applications', exact: true }).click();
  await expect(page.getByText('Application timeline', { exact: true }).first()).toBeVisible();
  await expect(page.getByRole('region', { name: 'Dashboard analytics' })).toBeVisible();
});

test('student notification actions show pending, prevent duplicates, and can be retried', async ({ page }) => {
  const state = await mockApi(page, 'STUDENT');
  const key = 'PATCH /student/notifications/801/read';
  state.failedMutations[key] = true;
  state.mutationDelays[key] = 1000;
  await login(page, 'STUDENT');
  await page.getByRole('button', { name: /notifications/i }).click();

  const markRead = page.getByRole('button', { name: 'Mark Interview rescheduled as read' });
  const markPromise = markRead.click();
  await expect(markRead).toHaveText('Marking read…');
  await expect(markRead).toBeDisabled();
  await markRead.evaluate(button => button.click());
  await expect.poll(() => state.mutationCalls[key]).toBe(1);
  await expect(page.getByRole('alert')).toContainText('Temporary mutation failure');
  await expect(markRead).toBeEnabled();

  state.failedMutations[key] = false;
  await markRead.click();
  await expect(page.getByText('Notification marked as read.', { exact: true })).toBeVisible();
  await markPromise;
  await expect.poll(() => state.notifications.find(item => item.id === 801).readFlag).toBe(true);
  await expect.poll(() => state.mutationCalls[key]).toBe(2);
});

test('resume upload requires a PDF and announces client-side file errors', async ({ page }) => {
  const state = await mockApi(page, 'STUDENT');
  await login(page, 'STUDENT');
  await page.getByRole('button', { name: 'resume', exact: true }).click();
  const file = page.locator('#resume-file');
  await page.getByRole('button', { name: 'Upload / replace' }).click();
  await expect(file).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByText('Resume (PDF) is required.')).toBeVisible();

  await file.setInputFiles({ name: 'resume.txt', mimeType: 'text/plain', buffer: Buffer.from('not a pdf') });
  await page.getByRole('button', { name: 'Upload / replace' }).click();
  await expect(page.getByRole('alert')).toContainText('Choose a PDF file.');
  expect(state.mutationCalls['POST /student/resume'] || 0).toBe(0);

  await file.setInputFiles({ name: 'resume.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 test') });
  await page.getByRole('button', { name: 'Upload / replace' }).click();
  await expect(page.getByText('Resume uploaded.', { exact: true })).toBeVisible();
  await expect.poll(() => state.mutationCalls['POST /student/resume']).toBe(1);
});

test('student notifications show empty and recoverable error states without breaking dashboard', async ({ page }) => {
  const state = await mockApi(page, 'STUDENT');
  state.notifications = [];
  await login(page, 'STUDENT');
  await page.getByRole('button', { name: /notifications/i }).click();
  await expect(page.getByText('No notifications.', { exact: true })).toBeVisible();

  state.failNotifications = true;
  await page.getByRole('button', { name: 'jobs', exact: true }).click();
  await page.getByRole('button', { name: /notifications/i }).click();
  await page.getByRole('button', { name: 'Refresh notifications' }).click();
  await expect(page.getByRole('alert')).toHaveText('Notifications unavailable');
  await expect(page.getByRole('region', { name: 'Dashboard analytics' })).toBeVisible();
  await page.getByRole('button', { name: 'jobs', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Apply' })).toBeVisible();
});

test('student notifications load when an unrelated dashboard request fails', async ({ page }) => {
  const state = await mockApi(page, 'STUDENT');
  state.failOpportunities = true;
  await login(page, 'STUDENT');
  await expect(page.getByText('Opportunities unavailable', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: /notifications/i }).click();
  await expect(page.getByText('Interview rescheduled', { exact: true })).toBeVisible();
  await expect(page.getByText('2 unread', { exact: true })).toBeVisible();
  expect(state.notificationRequests).toBe(1);
});

test('student opportunity failures are not shown as empty results and can be retried', async ({ page }) => {
  const state = await mockApi(page, 'STUDENT');
  state.failOpportunities = true;
  await login(page, 'STUDENT');
  await expect(page.getByRole('alert')).toContainText('Opportunities unavailable');
  await expect(page.getByText('No opportunities match these filters.', { exact: true })).toHaveCount(0);
  state.failOpportunities = false;
  await page.getByRole('alert').getByRole('button', { name: 'Retry' }).click();
  await expect(page.getByRole('button', { name: 'Apply' })).toBeVisible();
});

test('student filter responses cannot overwrite a newer search result', async ({ page }) => {
  const state = await mockApi(page, 'STUDENT');
  state.opportunityResponses = {
    slow: { content: [{ id: 302, title: 'Slow search result', companyName: 'Example Campus Co', eligible: true }] },
    fast: { content: [{ id: 303, title: 'Latest search result', companyName: 'Example Campus Co', eligible: true }] },
  };
  state.opportunityDelays = { slow: 350 };
  await login(page, 'STUDENT');
  const search = page.getByPlaceholder('Search', { exact: true });
  const submit = page.getByRole('button', { name: 'Search opportunities' });
  const slowResponse = page.waitForResponse(response =>
    new URL(response.url()).searchParams.get('search') === 'slow');
  await search.fill('slow');
  await submit.click();
  await expect(page.getByText('Loading opportunities…')).toBeVisible();
  await search.fill('fast');
  await submit.click();
  await expect(page.getByText('Latest search result', { exact: true })).toBeVisible();
  await slowResponse;
  await expect(page.getByText('Latest search result', { exact: true })).toBeVisible();
  await expect(page.getByText('Slow search result', { exact: true })).toHaveCount(0);
});

test('student offers distinguish loading, empty, and failed requests with retry', async ({ page }) => {
  const state = await mockApi(page, 'STUDENT');
  state.offers = [];
  state.offersDelayMs = 350;
  await login(page, 'STUDENT');
  await page.getByRole('link', { name: 'My offers' }).click();
  await expect(page.getByText('Loading offers…')).toBeVisible();
  await expect(page.getByText('No offers received.', { exact: true })).toBeVisible();

  state.failStudentOffers = true;
  await page.reload();
  await expect(page.getByRole('alert')).toContainText('Offers unavailable');
  await expect(page.getByText('No offers received.', { exact: true })).toHaveCount(0);
  state.failStudentOffers = false;
  await page.getByRole('alert').getByRole('button', { name: 'Retry' }).click();
  await expect(page.getByText('No offers received.', { exact: true })).toBeVisible();
});

test('admin section failures do not hide successful sections and can be retried', async ({ page }) => {
  const state = await mockApi(page, 'ADMIN');
  state.failAdminUsers = true;
  await login(page, 'ADMIN');
  await expect(page.getByRole('alert')).toContainText('Users unavailable');
  await expect(page.getByText('No users are available.', { exact: true })).toHaveCount(0);
  await expect(page.getByText('Example Campus Co', { exact: true })).toBeVisible();
  await expect(page.getByText('COMPANY_VERIFIED')).toBeVisible();

  state.failAdminUsers = false;
  await page.getByRole('alert').getByRole('button', { name: 'Retry' }).click();
  await expect(page.getByRole('row').filter({ hasText: 'test@example.test' })).toBeVisible();
});

test('recruiter postings recover from request failure and old selected-job responses are ignored', async ({ page }) => {
  const state = await mockApi(page, 'RECRUITER');
  state.failRecruiterJobs = true;
  state.jobs.push({ id: 302, title: 'Latest Campus Role', deadline: '2026-12-20', published: true, closed: false });
  state.applicationsByJob = {
    301: state.applications,
    302: [{ applicationId: 402, studentName: 'Latest job candidate', email: 'latest@example.test', status: 'APPLIED' }],
  };
  state.applicationDelays = { 301: 350 };
  await login(page, 'RECRUITER');
  await expect(page.getByRole('alert')).toContainText('Job postings unavailable');
  await expect(page.getByText('No jobs posted yet.', { exact: true })).toHaveCount(0);
  await expect(page.getByLabel('Name')).toHaveValue('Example Campus Co');

  state.failRecruiterJobs = false;
  await page.getByRole('alert').getByRole('button', { name: 'Retry' }).click();
  await expect(page.getByRole('button', { name: 'Latest Campus Role' })).toBeVisible();
  const firstJobResponse = page.waitForResponse(response =>
    response.url().includes('/recruiter/jobs/301/applications'));
  await page.getByRole('button', { name: 'Campus Software Engineer' }).click();
  await page.getByRole('button', { name: 'Latest Campus Role' }).click();
  await expect(page.getByText('Latest job candidate', { exact: true })).toBeVisible();
  await firstJobResponse;
  await expect(page.getByText('Latest job candidate', { exact: true })).toBeVisible();
  await expect(page.getByText('Test Student', { exact: true })).toHaveCount(0);
});

test('recruiter mutation refresh preserves the newly selected job', async ({ page }) => {
  const state = await mockApi(page, 'RECRUITER');
  const key = 'PATCH /recruiter/applications/401/status';
  state.jobs.push({ id: 302, title: 'Secure Campus Engineer', deadline: '2026-12-20', published: true, closed: false });
  state.applicationsByJob = {
    301: [{ applicationId: 401, studentName: 'First job candidate', status: 'APPLIED' }],
    302: [{ applicationId: 402, studentName: 'Second job candidate', status: 'APPLIED' }],
  };
  state.mutationDelays[key] = 500;
  await login(page, 'RECRUITER');

  await page.getByRole('button', { name: 'Campus Software Engineer' }).last().click();
  await expect(page.getByText('First job candidate', { exact: true })).toBeVisible();
  const firstJobReads = state.getCalls['GET /recruiter/jobs/301/applications'];
  const mutationResponse = page.waitForResponse(response =>
    response.request().method() === 'PATCH' && response.url().includes('/recruiter/applications/401/status'));
  await page.locator('tbody select').selectOption('SHORTLISTED');
  await page.getByRole('button', { name: 'Secure Campus Engineer' }).click();
  await expect(page.getByText('Second job candidate', { exact: true })).toBeVisible();
  await mutationResponse;

  await expect.poll(() => state.getCalls['GET /recruiter/jobs/302/applications']).toBe(2);
  expect(state.getCalls['GET /recruiter/jobs/301/applications']).toBe(firstJobReads);
  await expect(page.getByText('Second job candidate', { exact: true })).toBeVisible();
});

test('placement officer reviews drives and database-backed statistics view', async ({ page }) => {
  const state = await mockApi(page, 'PLACEMENT_OFFICER');
  await login(page, 'PLACEMENT_OFFICER');
  await expect(page.getByRole('heading', { name: 'Placement Officer Dashboard' })).toBeVisible();
  for (const label of ['Active drives', 'Registrations', 'Selections', 'Total students', 'Total drives',
    'Applications', 'Drive registrations', 'Rejected applications']) {
    await expect(page.getByRole('region', { name: 'Dashboard analytics' }).getByText(label, { exact: true })).toBeVisible();
  }
  await expect(page.getByText('12.5%', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Placement rate' })).toBeVisible();
  for (const label of ['Students placed', 'Students shortlisted', 'Unplaced students']) {
    await expect(page.getByRole('region', { name: 'Dashboard analytics' }).getByText(label, { exact: true })).toBeVisible();
  }
  await expect(page.getByText('Computer Science', { exact: true })).toBeVisible();
  await expect(page.getByRole('figure', { name: 'Department activity comparison' })).toBeVisible();
  await expect(page.getByRole('figure', { name: 'Application status distribution' })).toBeVisible();
  await expect(page.getByText('Recorded rejection reasons', { exact: true })).toBeVisible();
  await expect.poll(() => state.analyticsCalls.PLACEMENT_OFFICER).toBeGreaterThan(0);
  await expect(page.getByText('Example Campus Drive')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Registrations' })).toBeVisible();
  await page.locator('[name="title"]').fill('Second Example Drive');
  await page.locator('[name="registrationDeadline"]').fill('2026-12-10');
  await page.getByRole('button', { name: 'Create placement drive' }).click();
});

test('placement drive failures are distinct from an empty drive list and can be retried', async ({ page }) => {
  const state = await mockApi(page, 'PLACEMENT_OFFICER');
  state.failPlacementDrives = true;
  await login(page, 'PLACEMENT_OFFICER');
  await expect(page.getByRole('alert')).toContainText('Placement drives unavailable');
  await expect(page.getByText('No placement drives have been created.', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('region', { name: 'Dashboard analytics' })).toBeVisible();

  state.failPlacementDrives = false;
  await page.getByRole('alert').getByRole('button', { name: 'Retry' }).click();
  await expect(page.getByText('Example Campus Drive', { exact: true })).toBeVisible();
});

test('admin manages accounts and reviews company verification and audit log', async ({ page }) => {
  const state = await mockApi(page, 'ADMIN');
  await login(page, 'ADMIN');
  await expect(page.getByRole('heading', { name: 'Administrator Dashboard' })).toBeVisible();
  for (const label of ['Users', 'Students', 'Recruiters', 'Placement officers', 'Colleges', 'Companies',
    'Drives', 'Selections', 'Active jobs', 'Active internships', 'Applications']) {
    await expect(page.getByRole('region', { name: 'Dashboard analytics' }).getByText(label, { exact: true })).toBeVisible();
  }
  await expect(page.getByRole('heading', { name: 'Platform overview' })).toBeVisible();
  await expect(page.getByRole('figure', { name: 'Application status distribution' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Placement rate' })).toBeVisible();
  await expect(page.getByText('25%', { exact: true })).toBeVisible();
  await expect(page.getByText('3 of 12 students placed', { exact: true })).toBeVisible();
  await expect.poll(() => state.analyticsCalls.ADMIN).toBeGreaterThan(0);
  await expect(page.getByText('COMPANY_VERIFIED')).toBeVisible();
  await page.getByRole('button', { name: 'Verify' }).click();
  await expect(page.getByText('COMPANY_VERIFIED')).toBeVisible();
  await page.getByRole('button', { name: 'Deactivate' }).click();
  const collegeCode = page.getByLabel('Code');
  await page.getByRole('button', { name: 'Create college' }).click();
  await expect(collegeCode).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByText('Code is required.')).toBeVisible();
  await collegeCode.fill('NEW');
  await page.getByLabel('Name').fill('New College');
  await page.getByRole('button', { name: 'Create college' }).click();
  const collegeCard = page.getByRole('region', { name: 'Dashboard analytics' }).locator('article').filter({
    has: page.getByText('Colleges', { exact: true }),
  });
  await expect(collegeCard.getByText('2', { exact: true })).toBeVisible();
});

test('zero analytics values and empty status maps render without errors', async ({ page }) => {
  const state = await mockApi(page, 'RECRUITER');
  state.analytics.RECRUITER = {
    totalJobs: 0, totalInternships: 0, activeJobPostings: 0, totalApplicants: 0,
    shortlistedCandidates: 0, rejectedCandidates: 0, scheduledInterviews: 0, offersIssued: 0,
    acceptedOffers: 0, declinedOffers: 0, applicationsByStatus: {},
  };
  await login(page, 'RECRUITER');
  const jobsCard = page.locator('article').filter({ has: page.getByText('Jobs', { exact: true }) });
  await expect(jobsCard.getByText('0', { exact: true })).toBeVisible();
  await expect(page.getByText('No application status data available.', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Company profile' })).toBeVisible();
});

test('student with no applications or interviews gets an empty state and missing fields are safe', async ({ page }) => {
  const state = await mockApi(page, 'STUDENT');
  state.analytics.STUDENT = {
    totalApplications: 0, upcomingInterviews: 0, offersReceived: 0, acceptedOffers: 0,
    declinedOffers: 0, savedJobs: null, applicationsByStatus: { APPLIED: null },
  };
  await login(page, 'STUDENT');
  const analytics = page.getByRole('region', { name: 'Dashboard analytics' });
  await expect(analytics.getByText('No applications or upcoming interviews yet.', { exact: false })).toBeVisible();
  await expect(analytics.getByText('Some status counts are unavailable; distribution percentages are withheld.', { exact: true })).toBeVisible();
  await expect(analytics.getByText('No recent application activity.', { exact: true })).toBeVisible();
  const savedJobsCard = analytics.locator('article').filter({ hasText: 'Saved jobs' });
  await expect(savedJobsCard.getByText('—', { exact: true })).toBeVisible();
  await expect.poll(() => state.analyticsCalls.STUDENT).toBeGreaterThan(0);
});

test('placement dashboard omits empty rejection reasons and handles missing department maps', async ({ page }) => {
  const state = await mockApi(page, 'PLACEMENT_OFFICER');
  state.analytics.PLACEMENT_OFFICER.rejectionReasons = {};
  state.analytics.PLACEMENT_OFFICER.studentsByDepartment = null;
  delete state.analytics.PLACEMENT_OFFICER.selectionsByDepartment;
  await login(page, 'PLACEMENT_OFFICER');
  const analytics = page.getByRole('region', { name: 'Dashboard analytics' });
  await expect(analytics.getByText('No department data available.', { exact: true })).toBeVisible();
  await expect(analytics.getByText('Recorded rejection reasons', { exact: true })).toHaveCount(0);
});

test('analytics grids adapt at mobile, tablet, and desktop widths', async ({ page }) => {
  const state = await mockApi(page, 'RECRUITER');
  await page.setViewportSize({ width: 375, height: 812 });
  await login(page, 'RECRUITER');
  const metricGrid = page.getByRole('region', { name: 'Dashboard analytics' }).locator('div.grid').first();
  const columnCount = () => metricGrid.evaluate(element =>
    window.getComputedStyle(element).gridTemplateColumns.split(' ').length);
  await expect.poll(columnCount).toBe(2);
  await page.setViewportSize({ width: 768, height: 1024 });
  await expect.poll(columnCount).toBe(3);
  await page.setViewportSize({ width: 1440, height: 900 });
  await expect.poll(columnCount).toBe(4);
  await expect.poll(() => state.analyticsCalls.RECRUITER).toBeGreaterThan(0);
});

test('long account emails keep all dashboard headers and logout usable on mobile', async ({ page }) => {
  await mockApi(page, 'STUDENT');
  const email = 'alexandra.srinivasan.longname@example-university.invalid';
  const dashboards = [
    { role: 'STUDENT', path: '/student', heading: 'Student Workspace' },
    { role: 'RECRUITER', path: '/recruiter', heading: 'Recruiter Dashboard' },
    { role: 'PLACEMENT_OFFICER', path: '/placement', heading: 'Placement Officer Dashboard' },
    { role: 'ADMIN', path: '/admin', heading: 'Administrator Dashboard' },
  ];

  for (const dashboard of dashboards) {
    await page.goto('/login');
    await page.evaluate(({ role, email }) => {
      localStorage.clear();
      localStorage.setItem('token', 'responsive-audit-token');
      localStorage.setItem('role', role);
      localStorage.setItem('email', email);
    }, { role: dashboard.role, email });
    await page.goto(dashboard.path);
    await expect(page.getByRole('heading', { name: dashboard.heading })).toBeVisible();

    for (const width of [375, 390, 768]) {
      await page.setViewportSize({ width, height: width < 500 ? 812 : 1024 });
      const layout = await page.evaluate(() => {
        const header = document.querySelector('main > header');
        const logout = header.querySelector('button');
        return {
          viewportWidth: window.innerWidth,
          documentWidth: document.documentElement.scrollWidth,
          email: header.querySelector('p').textContent,
          logoutRight: logout.getBoundingClientRect().right,
          logoutHeight: logout.getBoundingClientRect().height,
        };
      });
      expect(layout.email).toBe(email);
      expect(layout.documentWidth).toBeLessThanOrEqual(layout.viewportWidth);
      expect(layout.logoutRight).toBeLessThanOrEqual(layout.viewportWidth);
      expect(layout.logoutHeight).toBeGreaterThanOrEqual(44);
    }

    await page.setViewportSize({ width: 375, height: 812 });
    await page.getByRole('button', { name: 'Logout' }).click();
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  }
});

test('analytics refresh exposes keyboard focus and announces background progress', async ({ page }) => {
  const state = await mockApi(page, 'RECRUITER');
  await login(page, 'RECRUITER');
  const analytics = page.getByRole('region', { name: 'Dashboard analytics' });
  const refresh = analytics.getByRole('button', { name: 'Refresh analytics' });
  await refresh.focus();
  await page.keyboard.press('Tab');
  await page.keyboard.press('Shift+Tab');
  await expect(refresh).toBeFocused();
  await expect(refresh).toHaveClass(/focus-visible:ring-2/);
  await expect.poll(() => refresh.evaluate(element => element.matches(':focus-visible'))).toBe(true);

  state.analyticsDelayMs = 600;
  await refresh.click();
  await expect(analytics).toHaveAttribute('aria-busy', 'true');
  await expect(analytics.getByRole('status')).toContainText('Refreshing analytics. Showing the last successfully loaded data.');
  await expect(analytics).toHaveAttribute('aria-busy', 'false');
  await expect(analytics.getByRole('button', { name: 'Refresh analytics' })).toBeEnabled();
});

test('analytics failure leaves recruiter operations available and can be retried', async ({ page }) => {
  const state = await mockApi(page, 'RECRUITER');
  state.failAnalytics = true;
  await login(page, 'RECRUITER');
  await expect(page.getByRole('alert')).toHaveText('Analytics unavailable');
  await expect(page.getByRole('heading', { name: 'Company profile' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'My postings' })).toBeVisible();
  state.failAnalytics = false;
  await page.getByRole('button', { name: 'Retry analytics' }).click();
  await expect(page.getByRole('region', { name: 'Dashboard analytics' }).getByText('Applicants', { exact: true })).toBeVisible();
  await expect.poll(() => state.analyticsCalls.RECRUITER).toBeGreaterThan(1);
  state.failAnalytics = true;
  await page.getByRole('button', { name: 'Refresh analytics' }).click();
  await expect(page.getByRole('alert')).toHaveText('Analytics unavailable');
  await expect(page.getByText('Showing the last successfully loaded data.', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Recruitment pipeline milestones' })).toBeVisible();
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
