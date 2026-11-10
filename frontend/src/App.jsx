import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import Login from './pages/Login';
import StudentDashboard from './pages/StudentDashboard';
import RecruiterDashboard from './pages/RecruiterDashboard';
import PlacementDashboard from './pages/PlacementDashboard';
import AdminDashboard from './pages/AdminDashboard';
import AuthFlow from './pages/AuthFlow';
import RecruiterInterviews from './pages/RecruiterInterviews';
import RecruiterOffers from './pages/RecruiterOffers';
import StudentOffers from './pages/StudentOffers';
import StudentInterviews from './pages/StudentInterviews';

function ProtectedRoute({ children, role }) {
  const { isAuthenticated, role: userRole } = useAuth();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  if (role && userRole !== role) {
    const fallback = {
      STUDENT: '/student',
      RECRUITER: '/recruiter',
      PLACEMENT_OFFICER: '/placement',
      ADMIN: '/admin',
    };
    return <Navigate to={fallback[userRole] || '/login'} replace />;
  }
  return children;
}

export default function App() {
  const { isAuthenticated, role } = useAuth();
  return (
    <Routes>
      <Route path="/verify-email" element={<AuthFlow />} />
      <Route path="/forgot-password" element={<AuthFlow />} />
      <Route path="/reset-password" element={<AuthFlow />} />
      <Route path="/login" element={isAuthenticated
        ? <Navigate to={{
            STUDENT: '/student',
            RECRUITER: '/recruiter',
            PLACEMENT_OFFICER: '/placement',
            ADMIN: '/admin',
          }[role] || '/login'} replace />
        : <Login />} />
      <Route path="/student" element={
        <ProtectedRoute role="STUDENT"><StudentDashboard /></ProtectedRoute>
      } />
      <Route path="/student/offers" element={<ProtectedRoute role="STUDENT"><StudentOffers /></ProtectedRoute>} />
      <Route path="/student/interviews" element={<ProtectedRoute role="STUDENT"><StudentInterviews /></ProtectedRoute>} />
      <Route path="/recruiter" element={
        <ProtectedRoute role="RECRUITER"><RecruiterDashboard /></ProtectedRoute>
      } />
      <Route path="/recruiter/interviews" element={<ProtectedRoute role="RECRUITER"><RecruiterInterviews /></ProtectedRoute>} />
      <Route path="/recruiter/offers" element={<ProtectedRoute role="RECRUITER"><RecruiterOffers /></ProtectedRoute>} />
      <Route path="/placement" element={
        <ProtectedRoute role="PLACEMENT_OFFICER"><PlacementDashboard /></ProtectedRoute>
      } />
      <Route path="/admin" element={
        <ProtectedRoute role="ADMIN"><AdminDashboard /></ProtectedRoute>
      } />
      <Route path="*" element={<Navigate to={isAuthenticated
        ? ({
            STUDENT: '/student',
            RECRUITER: '/recruiter',
            PLACEMENT_OFFICER: '/placement',
            ADMIN: '/admin',
          }[role] || '/login')
        : '/login'} replace />} />
    </Routes>
  );
}
