function formatMetric(value, format) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '—';
  if (format === 'percent') {
    return `${new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format(value)}%`;
  }
  return new Intl.NumberFormat().format(value);
}

function isCount(value) {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0;
}

function cardClass(color = 'indigo') {
  const colors = {
    indigo: 'border-l-indigo-500',
    blue: 'border-l-blue-500',
    emerald: 'border-l-emerald-500',
    amber: 'border-l-amber-500',
    rose: 'border-l-rose-500',
    violet: 'border-l-violet-500',
  };
  return colors[color] || colors.indigo;
}

export function MetricGrid({ metrics, columns = 'xl:grid-cols-5' }) {
  return (
    <div className={`grid grid-cols-2 gap-3 sm:grid-cols-3 ${columns}`}>
      {metrics.map(({ label, value, format }) => (
        <article key={label} className="min-w-0 rounded-xl border border-slate-100 bg-white p-4 shadow-sm sm:p-5">
          <p className="text-sm font-medium text-slate-500">{label}</p>
          <p className="mt-2 break-words text-2xl font-bold text-indigo-700 sm:text-3xl">
            {formatMetric(value, format)}
          </p>
        </article>
      ))}
    </div>
  );
}

export function CountBreakdown({ title, counts, emptyMessage = 'No data available.' }) {
  const entries = counts && typeof counts === 'object' && !Array.isArray(counts)
    ? Object.entries(counts) : [];
  return (
    <section className="rounded-xl border border-slate-100 bg-white p-5 shadow-sm">
      <h3 className="font-semibold text-slate-900">{title}</h3>
      {entries.length === 0 ? <p className="mt-3 text-sm text-slate-500">{emptyMessage}</p> : (
        <dl className="mt-3 divide-y divide-slate-100">
          {entries.map(([label, value]) => (
            <div key={label} className="flex items-center justify-between gap-4 py-2 text-sm">
              <dt className="break-words text-slate-600">{label.replaceAll('_', ' ')}</dt>
              <dd className="shrink-0 font-semibold tabular-nums text-slate-900">{formatMetric(value)}</dd>
            </div>
          ))}
        </dl>
      )}
    </section>
  );
}

export function StatusDistribution({ title, counts, emptyMessage = 'No application status data available.' }) {
  const entries = counts && typeof counts === 'object' && !Array.isArray(counts)
    ? Object.entries(counts) : [];
  const completeCounts = entries.every(([, count]) => isCount(count));
  const total = entries.reduce((sum, [, count]) => sum + (isCount(count) ? count : 0), 0);

  return (
    <figure className="rounded-xl border border-slate-100 bg-white p-5 shadow-sm">
      <figcaption className="font-semibold text-slate-900">{title}</figcaption>
      {entries.length === 0 ? <p className="mt-3 text-sm text-slate-500">{emptyMessage}</p> : (
        <>
          {total === 0 && completeCounts && <p className="mt-3 text-sm text-slate-500">No applications are recorded for these statuses.</p>}
          {!completeCounts && <p className="mt-3 text-sm text-amber-700">Some status counts are unavailable; distribution percentages are withheld.</p>}
          <ul aria-label={`${title} data`} className="mt-3 space-y-3">
            {entries.map(([label, count]) => {
              const validCount = isCount(count);
              const share = completeCounts && total > 0 && validCount ? (count / total) * 100 : 0;
              const text = `${label.replaceAll('_', ' ')}: ${formatMetric(count)}${completeCounts && total > 0 && validCount ? `, ${formatMetric(share, 'percent')}` : ''}`;
              return (
                <li key={label} aria-label={text}>
                  <div className="flex items-center justify-between gap-3 text-sm">
                    <span className="break-words text-slate-600">{label.replaceAll('_', ' ')}</span>
                    <span className="shrink-0 font-semibold tabular-nums text-slate-900">
                      {formatMetric(count)}{completeCounts && total > 0 && validCount ? <span className="ml-2 font-normal text-slate-500">{formatMetric(share, 'percent')}</span> : null}
                    </span>
                  </div>
                  <div aria-hidden="true" className="mt-1.5 h-2 overflow-hidden rounded-full bg-slate-100">
                    <div className="h-full rounded-full bg-indigo-600 transition-[width]" style={{ width: `${share}%` }} />
                  </div>
                </li>
              );
            })}
          </ul>
        </>
      )}
    </figure>
  );
}

export function PlacementRate({ percentage, placed, students }) {
  const validPercentage = typeof percentage === 'number' && Number.isFinite(percentage);
  const barWidth = validPercentage ? Math.min(100, Math.max(0, percentage)) : 0;
  const placedText = formatMetric(placed);
  const studentsText = formatMetric(students);
  return (
    <section className="rounded-xl border border-slate-100 bg-white p-5 shadow-sm">
      <h3 className="font-semibold text-slate-900">Placement rate</h3>
      <p className="mt-3 text-3xl font-bold text-indigo-700">{formatMetric(percentage, 'percent')}</p>
      <p className="mt-1 text-sm text-slate-600">{placedText} of {studentsText} students placed</p>
      <div aria-hidden="true" className="mt-4 h-2 overflow-hidden rounded-full bg-slate-100">
        <div className="h-full rounded-full bg-indigo-600 transition-[width]" style={{ width: `${barWidth}%` }} />
      </div>
      <p className="mt-3 text-xs leading-5 text-slate-500">
        Distinct placed students divided by all students in this reporting scope.
      </p>
    </section>
  );
}

export function PlacementOutcomes({ placed, shortlisted, unplaced }) {
  const outcomes = [
    { label: 'Students placed', value: placed, color: 'emerald' },
    { label: 'Students shortlisted', value: shortlisted, color: 'blue' },
    { label: 'Unplaced students', value: unplaced, color: 'amber' },
  ];
  return (
    <section className="rounded-xl border border-slate-100 bg-white p-5 shadow-sm">
      <h3 className="font-semibold text-slate-900">Placement outcomes</h3>
      <dl className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-3">
        {outcomes.map(({ label, value, color }) => (
          <div key={label} className={`rounded-lg border border-slate-100 border-l-4 ${cardClass(color)} bg-slate-50 p-3`}>
            <dt className="text-sm text-slate-600">{label}</dt>
            <dd className="mt-1 text-2xl font-bold tabular-nums text-slate-900">{formatMetric(value)}</dd>
          </div>
        ))}
      </dl>
      <p className="mt-3 text-xs leading-5 text-slate-500">
        Shortlisted counts are status-history based and may overlap with placement outcomes.
      </p>
    </section>
  );
}

export function DepartmentComparison({ studentsByDepartment, selectionsByDepartment }) {
  const students = studentsByDepartment && typeof studentsByDepartment === 'object'
    && !Array.isArray(studentsByDepartment) ? studentsByDepartment : {};
  const selections = selectionsByDepartment && typeof selectionsByDepartment === 'object'
    && !Array.isArray(selectionsByDepartment) ? selectionsByDepartment : {};
  const departments = [...new Set([...Object.keys(students), ...Object.keys(selections)])].sort((a, b) => a.localeCompare(b));
  const maxCount = Math.max(0, ...departments.flatMap(department => [
    isCount(students[department]) ? students[department] : 0,
    isCount(selections[department]) ? selections[department] : 0,
  ]));

  return (
    <figure className="rounded-xl border border-slate-100 bg-white p-5 shadow-sm">
      <figcaption className="font-semibold text-slate-900">Department activity comparison</figcaption>
      <p className="mt-1 text-xs leading-5 text-slate-500">
        Student totals compared with recorded selection counts; this is not a placement-rate calculation.
      </p>
      {departments.length === 0 ? <p className="mt-3 text-sm text-slate-500">No department data available.</p> : (
        <>
          <ul aria-label="Department student and selection counts" className="mt-4 space-y-4">
            {departments.map(department => {
              const studentCount = students[department];
              const selectionCount = selections[department];
              const studentWidth = maxCount > 0 && isCount(studentCount) ? (studentCount / maxCount) * 100 : 0;
              const selectionWidth = maxCount > 0 && isCount(selectionCount) ? (selectionCount / maxCount) * 100 : 0;
              return (
                <li key={department} aria-label={`${department}: ${formatMetric(studentCount)} students, ${formatMetric(selectionCount)} recorded selections`}>
                  <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
                    <span className="break-words text-sm font-medium text-slate-700">{department}</span>
                    <span className="text-xs tabular-nums text-slate-500">
                      {formatMetric(studentCount)} students · {formatMetric(selectionCount)} selections
                    </span>
                  </div>
                  <div aria-hidden="true" className="mt-2 space-y-1.5">
                    <div className="h-2 overflow-hidden rounded-full bg-slate-100">
                      <div className="h-full rounded-full bg-indigo-600" style={{ width: `${studentWidth}%` }} />
                    </div>
                    <div className="h-2 overflow-hidden rounded-full bg-slate-100">
                      <div className="h-full rounded-full bg-emerald-500" style={{ width: `${selectionWidth}%` }} />
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>
          <div aria-label="Chart legend" className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-xs text-slate-600">
            <span><span aria-hidden="true" className="mr-2 inline-block h-2.5 w-2.5 rounded-full bg-indigo-600" />Students</span>
            <span><span aria-hidden="true" className="mr-2 inline-block h-2.5 w-2.5 rounded-full bg-emerald-500" />Recorded selections</span>
          </div>
        </>
      )}
    </figure>
  );
}

export function RecruiterPipeline({ stats }) {
  const milestones = [
    { label: 'Shortlisted candidates', value: stats.shortlistedCandidates, description: 'Distinct students with a shortlisted status.', color: 'blue' },
    { label: 'Rejected candidates', value: stats.rejectedCandidates, description: 'Distinct students with a rejected application.', color: 'rose' },
    { label: 'Scheduled interviews', value: stats.scheduledInterviews, description: 'Scheduled interview records.', color: 'violet' },
    { label: 'Offers issued', value: stats.offersIssued, description: 'Offer records, including later accepted or declined offers.', color: 'indigo' },
    { label: 'Accepted offers', value: stats.acceptedOffers, description: 'Accepted offer records.', color: 'emerald' },
    { label: 'Declined offers', value: stats.declinedOffers, description: 'Declined offer records.', color: 'amber' },
  ];
  return (
    <section className="rounded-xl border border-slate-100 bg-white p-5 shadow-sm">
      <h3 className="font-semibold text-slate-900">Recruitment pipeline milestones</h3>
      <p className="mt-1 text-xs leading-5 text-slate-500">
        Independent counts, not a conversion funnel. Candidates, interviews, and offers are different record types; accepted and declined offers are included in offers issued.
      </p>
      <dl className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {milestones.map(({ label, value, description, color }) => (
          <div key={label} className={`rounded-lg border border-slate-100 border-l-4 ${cardClass(color)} p-4`}>
            <dt className="text-sm font-medium text-slate-700">{label}</dt>
            <dd className="mt-2 text-2xl font-bold tabular-nums text-slate-900">{formatMetric(value)}</dd>
            <p className="mt-1 text-xs leading-5 text-slate-500">{description}</p>
          </div>
        ))}
      </dl>
    </section>
  );
}

export function StudentCommitments({ stats }) {
  const summary = [
    { label: 'Upcoming interviews', value: stats.upcomingInterviews, color: 'violet' },
    { label: 'Offers received', value: stats.offersReceived, color: 'indigo' },
    { label: 'Accepted offers', value: stats.acceptedOffers, color: 'emerald' },
    { label: 'Declined offers', value: stats.declinedOffers, color: 'amber' },
  ];
  return (
    <section className="rounded-xl border border-slate-100 bg-white p-5 shadow-sm">
      <h3 className="font-semibold text-slate-900">Interviews and offer summary</h3>
      <dl className="mt-3 grid grid-cols-2 gap-3 xl:grid-cols-4">
        {summary.map(({ label, value, color }) => (
          <div key={label} className={`rounded-lg border border-slate-100 border-l-4 ${cardClass(color)} bg-slate-50 p-3`}>
            <dt className="text-sm text-slate-600">{label}</dt>
            <dd className="mt-1 text-2xl font-bold tabular-nums text-slate-900">{formatMetric(value)}</dd>
          </div>
        ))}
      </dl>
      <p className="mt-3 text-xs leading-5 text-slate-500">
        Offer counts are shown by returned status; no pending or other status is inferred.
      </p>
    </section>
  );
}

export function AnalyticsPanel({ loading, error, onRefresh, hasData, children }) {
  return (
    <section aria-label="Dashboard analytics" aria-busy={loading} className="mx-auto mt-6 max-w-6xl">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-xl font-semibold text-slate-900">Analytics</h2>
        <button type="button" onClick={onRefresh} disabled={loading}
          className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-600 focus-visible:ring-offset-2 disabled:cursor-wait disabled:opacity-60">
          {loading ? 'Refreshing…' : error ? 'Retry analytics' : 'Refresh analytics'}
        </button>
      </div>
      {loading && (
        <p role="status" aria-live="polite" className="mb-3 rounded-lg bg-white p-4 text-sm text-slate-600 shadow-sm">
          {hasData ? 'Refreshing analytics. Showing the last successfully loaded data.' : 'Loading analytics…'}
        </p>
      )}
      {error && <p role="alert" className="mb-3 rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p>}
      {error && hasData && <p className="mb-3 text-sm text-slate-600">Showing the last successfully loaded data.</p>}
      {hasData && children}
      {!loading && !error && !hasData && <p className="rounded-lg bg-white p-4 text-sm text-slate-500 shadow-sm">Analytics are not available yet.</p>}
    </section>
  );
}

export function DataState({
  loading,
  error,
  onRetry,
  isEmpty,
  loadingMessage = 'Loading data…',
  emptyMessage = 'No records available.',
}) {
  if (!loading && !error && !isEmpty) return null;

  return (
    <div className="space-y-2">
      {loading && (
        <p role="status" aria-live="polite" className="rounded-lg bg-white p-3 text-sm text-slate-600">
          {loadingMessage}
        </p>
      )}
      {error && (
        <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-lg bg-red-50 p-3 text-sm text-red-800">
          <p>{error}</p>
          {onRetry && (
            <button type="button" onClick={onRetry}
              className="rounded border border-red-300 bg-white px-3 py-1.5 font-semibold text-red-800 hover:bg-red-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-700 focus-visible:ring-offset-2">
              Retry
            </button>
          )}
        </div>
      )}
      {!loading && !error && isEmpty && (
        <p role="status" className="rounded-lg bg-white p-3 text-sm text-slate-600">
          {emptyMessage}
        </p>
      )}
    </div>
  );
}

export function MutationFeedback({ feedback }) {
  if (!feedback?.message) return null;
  const isError = feedback.type === 'error';
  return (
    <p role={isError ? 'alert' : 'status'} aria-live={isError ? 'assertive' : 'polite'}
      className={`mx-auto mt-4 max-w-6xl rounded p-3 ${isError
        ? 'bg-red-50 text-red-800' : 'bg-green-50 text-green-800'}`}>
      {feedback.message}
    </p>
  );
}

export function ActivityList({ items }) {
  return (
    <section className="rounded-xl border border-slate-100 bg-white p-5 shadow-sm">
      <h3 className="font-semibold text-slate-900">Recent application activity</h3>
      {!items?.length ? <p className="mt-3 text-sm text-slate-500">No recent application activity.</p> : (
        <ol className="mt-3 divide-y divide-slate-100">
          {items.map((item, index) => (
            <li key={`${item.changedAt}-${item.status}-${index}`} className="py-3 first:pt-0 last:pb-0">
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <p className="font-medium text-slate-800">{item.jobTitle || 'Application'}</p>
                  <p className="text-sm text-slate-500">{item.companyName || 'Company not specified'}</p>
                </div>
                <span className="rounded-full bg-indigo-50 px-2.5 py-1 text-xs font-semibold text-indigo-700">
                  {(item.status || 'Unknown').replaceAll('_', ' ')}
                </span>
              </div>
              {item.note && <p className="mt-2 text-sm text-slate-600">{item.note}</p>}
              {item.changedAt && <time className="mt-1 block text-xs text-slate-400"
                dateTime={item.changedAt}>{new Date(item.changedAt).toLocaleString()}</time>}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
