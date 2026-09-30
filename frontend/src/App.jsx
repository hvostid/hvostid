import { lazy, Suspense } from 'react';
import { ErrorBoundary } from 'react-error-boundary';
import { Routes, Route } from 'react-router-dom';
import Navbar from './components/Navbar';
import ProtectedRoute from './components/ProtectedRoute';
const CreatePassportPage = lazy(() => import('./pages/CreatePassportPage'));
// Public pages
const CatalogPage = lazy(() => import('./pages/CatalogPage'));
const ListingDetailPage = lazy(() => import('./pages/ListingDetailPage'));
const LoginPage = lazy(() => import('./pages/LoginPage'));
const RegisterPage = lazy(() => import('./pages/RegisterPage'));

// Buyer pages
const ProfilePage = lazy(() => import('./pages/ProfilePage'));
const QuestionnairePage = lazy(() => import('./pages/QuestionnairePage'));
const MatchResultPage = lazy(() => import('./pages/MatchResultPage'));
const RecommendationsPage = lazy(() => import('./pages/RecommendationsPage'));

// Seller pages
const MyListingsPage = lazy(() => import('./pages/MyListingsPage'));
const CreateListingPage = lazy(() => import('./pages/CreateListingPage'));
const EditListingPage = lazy(() => import('./pages/EditListingPage'));
const PassportFormPage = lazy(() => import('./pages/PassportFormPage'));

// Moderator pages
const ModerationQueuePage = lazy(() => import('./pages/ModerationQueuePage'));
const ModerationDetailPage = lazy(() => import('./pages/ModerationDetailPage'));
const FlagsPage = lazy(() => import('./pages/FlagsPage'));

const PasswordRecoveryPage = lazy(() => import('./pages/PasswordRecoveryPage'));
const VerifyEmailPage = lazy(() => import('./pages/VerifyEmailPage'));

function UnexpectedError({ resetErrorBoundary }) {
    return (
        <div role="alert" className="p-8 text-center">
            <h1>Unable to display this page</h1>
            <button className="mt-4 text-indigo-600" onClick={resetErrorBoundary}>
                Return to catalog
            </button>
        </div>
    );
}

export default function App() {
    return (
        <ErrorBoundary
            FallbackComponent={UnexpectedError}
            onReset={() => window.location.assign('/')}
        >
            <div className="min-h-screen flex flex-col bg-gray-50">
                <Navbar />
                <main className="flex-1 max-w-7xl mx-auto px-4 py-6 w-full">
                    <Suspense fallback={<p role="status">Loading page...</p>}>
                        <Routes>
                            {/* Public */}
                            <Route path="/" element={<CatalogPage />} />
                            <Route path="/login" element={<LoginPage />} />
                            <Route path="/register" element={<RegisterPage />} />
                            <Route path="/listings/:id" element={<ListingDetailPage />} />

                            <Route path="/forgot-password" element={<PasswordRecoveryPage />} />
                            <Route path="/reset-password" element={<PasswordRecoveryPage />} />
                            <Route path="/verify-email" element={<VerifyEmailPage />} />
                            <Route
                                path="*"
                                element={
                                    <div className="p-8 text-center">
                                        <h1>Page not found</h1>
                                        <a href="/">Return to catalog</a>
                                    </div>
                                }
                            />
                            {/* Buyer */}
                            <Route
                                path="/profile"
                                element={
                                    <ProtectedRoute>
                                        <ProfilePage />
                                    </ProtectedRoute>
                                }
                            />
                            <Route
                                path="/profile/questionnaire"
                                element={
                                    <ProtectedRoute>
                                        <QuestionnairePage />
                                    </ProtectedRoute>
                                }
                            />
                            <Route
                                path="/listings/:id/match"
                                element={
                                    <ProtectedRoute>
                                        <MatchResultPage />
                                    </ProtectedRoute>
                                }
                            />
                            <Route
                                path="/recommendations"
                                element={
                                    <ProtectedRoute>
                                        <RecommendationsPage />
                                    </ProtectedRoute>
                                }
                            />

                            {/* Seller */}
                            <Route
                                path="/my-listings"
                                element={
                                    <ProtectedRoute requiredRole="SELLER">
                                        <MyListingsPage />
                                    </ProtectedRoute>
                                }
                            />
                            <Route
                                path="/my-listings/new"
                                element={
                                    <ProtectedRoute requiredRole="SELLER">
                                        <CreateListingPage />
                                    </ProtectedRoute>
                                }
                            />
                            <Route
                                path="/my-listings/:id/edit"
                                element={
                                    <ProtectedRoute requiredRole="SELLER">
                                        <EditListingPage />
                                    </ProtectedRoute>
                                }
                            />
                            <Route
                                path="/my-listings/:id/passport"
                                element={
                                    <ProtectedRoute requiredRole="SELLER">
                                        <PassportFormPage />
                                    </ProtectedRoute>
                                }
                            />
                            <Route
                                path="/passports/new"
                                element={
                                    <ProtectedRoute requiredRole="SELLER">
                                        <CreatePassportPage />
                                    </ProtectedRoute>
                                }
                            />

                            <Route
                                path="/passports/:passportId/edit"
                                element={
                                    <ProtectedRoute requiredRole="SELLER">
                                        <PassportFormPage />
                                    </ProtectedRoute>
                                }
                            />

                            {/* Moderator */}
                            <Route
                                path="/moderation"
                                element={
                                    <ProtectedRoute requiredRole={['MODERATOR', 'ADMIN']}>
                                        <ModerationQueuePage />
                                    </ProtectedRoute>
                                }
                            />
                            <Route
                                path="/moderation/:id"
                                element={
                                    <ProtectedRoute requiredRole={['MODERATOR', 'ADMIN']}>
                                        <ModerationDetailPage />
                                    </ProtectedRoute>
                                }
                            />
                            <Route
                                path="/moderation/flags"
                                element={
                                    <ProtectedRoute requiredRole={['MODERATOR', 'ADMIN']}>
                                        <FlagsPage />
                                    </ProtectedRoute>
                                }
                            />
                        </Routes>
                    </Suspense>
                </main>
            </div>
        </ErrorBoundary>
    );
}
