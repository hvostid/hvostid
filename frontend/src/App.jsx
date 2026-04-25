import { Routes, Route } from 'react-router-dom';
import Navbar from './components/Navbar';
import ProtectedRoute from './components/ProtectedRoute';

// Public pages
import CatalogPage from './pages/CatalogPage';
import ListingDetailPage from './pages/ListingDetailPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';

// Buyer pages
import ProfilePage from './pages/ProfilePage';
import QuestionnairePage from './pages/QuestionnairePage';
import MatchResultPage from './pages/MatchResultPage';
import RecommendationsPage from './pages/RecommendationsPage';

// Seller pages
import MyListingsPage from './pages/MyListingsPage';
import CreateListingPage from './pages/CreateListingPage';
import EditListingPage from './pages/EditListingPage';
import PassportFormPage from './pages/PassportFormPage';

// Moderator pages
import ModerationQueuePage from './pages/ModerationQueuePage';
import ModerationDetailPage from './pages/ModerationDetailPage';
import FlagsPage from './pages/FlagsPage';

export default function App() {
  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />
      <main className="max-w-7xl mx-auto px-4 py-6">
        <Routes>
          {/* Public */}
          <Route path="/" element={<CatalogPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/listings/:id" element={<ListingDetailPage />} />

          {/* Buyer */}
          <Route path="/profile" element={
            <ProtectedRoute><ProfilePage /></ProtectedRoute>
          } />
          <Route path="/profile/questionnaire" element={
            <ProtectedRoute><QuestionnairePage /></ProtectedRoute>
          } />
          <Route path="/listings/:id/match" element={
            <ProtectedRoute><MatchResultPage /></ProtectedRoute>
          } />
          <Route path="/recommendations" element={
            <ProtectedRoute><RecommendationsPage /></ProtectedRoute>
          } />

          {/* Seller */}
          <Route path="/my-listings" element={
            <ProtectedRoute requiredRole="SELLER"><MyListingsPage /></ProtectedRoute>
          } />
          <Route path="/my-listings/new" element={
            <ProtectedRoute requiredRole="SELLER"><CreateListingPage /></ProtectedRoute>
          } />
          <Route path="/my-listings/:id/edit" element={
            <ProtectedRoute requiredRole="SELLER"><EditListingPage /></ProtectedRoute>
          } />
          <Route path="/my-listings/:id/passport" element={
            <ProtectedRoute requiredRole="SELLER"><PassportFormPage /></ProtectedRoute>
          } />

          {/* Moderator */}
          <Route path="/moderation" element={
            <ProtectedRoute requiredRole="MODERATOR"><ModerationQueuePage /></ProtectedRoute>
          } />
          <Route path="/moderation/:id" element={
            <ProtectedRoute requiredRole="MODERATOR"><ModerationDetailPage /></ProtectedRoute>
          } />
          <Route path="/moderation/flags" element={
            <ProtectedRoute requiredRole="MODERATOR"><FlagsPage /></ProtectedRoute>
          } />
        </Routes>
      </main>
    </div>
  );
}
