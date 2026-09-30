import { beforeEach, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import api from '../api/client';
import SellerContact from './SellerContact';
import AccountSecurity from './AccountSecurity';
import PasswordRecoveryPage from '../pages/PasswordRecoveryPage';
import VerifyEmailPage from '../pages/VerifyEmailPage';
import ProfilePage from '../pages/ProfilePage';
import { useAuth } from '../context/useAuth';

vi.mock('../api/client', () => ({
    default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}));
vi.mock('../context/useAuth', () => ({ useAuth: vi.fn() }));

beforeEach(() => {
    api.get.mockReset().mockResolvedValue({ data: [] });
    api.post.mockReset().mockResolvedValue({});
    api.put.mockReset().mockResolvedValue({ data: {} });
    useAuth.mockReturnValue({
        user: {
            id: 7,
            name: 'Seller',
            phone: '+1234567890',
            roles: ['SELLER'],
            emailVerified: false,
        },
        isAuthenticated: true,
        hasRole: () => true,
        reloadProfile: vi.fn(),
        logout: vi.fn(),
        addRole: vi.fn(),
    });
});

it('does not request a seller contact until the buyer is authenticated', () => {
    render(
        <MemoryRouter>
            <SellerContact sellerId={7} authenticated={false} />
        </MemoryRouter>
    );
    expect(screen.getByRole('link', { name: 'Sign in to contact the seller' })).toBeInTheDocument();
    expect(api.get).not.toHaveBeenCalled();
});

it('shows a contact withheld by the seller without redirecting', async () => {
    api.get.mockRejectedValue({ response: { status: 404 } });
    render(
        <MemoryRouter>
            <SellerContact sellerId={7} authenticated />
        </MemoryRouter>
    );
    await userEvent.click(screen.getByRole('button', { name: 'Contact seller' }));
    expect(
        await screen.findByText('This seller has not shared a contact number.')
    ).toBeInTheDocument();
});

it('displays the phone returned by the consent-protected endpoint', async () => {
    api.get.mockResolvedValue({ data: { userId: 7, name: 'Seller', phone: '+1 (234) 567890' } });
    render(
        <MemoryRouter>
            <SellerContact sellerId={7} authenticated />
        </MemoryRouter>
    );
    await userEvent.click(screen.getByRole('button', { name: 'Contact seller' }));
    expect(await screen.findByRole('link', { name: '+1 (234) 567890' })).toHaveAttribute(
        'href',
        'tel:+1234567890'
    );
});

it('keeps recovery requests generic and explains mail outages', async () => {
    api.post.mockRejectedValueOnce({ response: { status: 503 } });
    render(
        <MemoryRouter initialEntries={['/forgot-password']}>
            <PasswordRecoveryPage />
        </MemoryRouter>
    );
    await userEvent.type(screen.getByLabelText(/Email/), 'owner@example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Send recovery link' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
        'Email delivery is temporarily unavailable'
    );
    await userEvent.click(screen.getByRole('button', { name: 'Send recovery link' }));
    expect(await screen.findByRole('status')).toHaveTextContent('If this email is registered');
});

it('submits a one-use reset token and matching passwords', async () => {
    render(
        <MemoryRouter initialEntries={['/reset-password?token=one-use']}>
            <PasswordRecoveryPage />
        </MemoryRouter>
    );
    await userEvent.type(screen.getByLabelText(/New password/), 'newpassword123');
    await userEvent.type(screen.getByLabelText(/Confirm password/), 'newpassword123');
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }));
    expect(api.post).toHaveBeenCalledWith('/auth/password-reset/confirm', {
        token: 'one-use',
        password: 'newpassword123',
    });
    expect(await screen.findByRole('status')).toHaveTextContent(
        'All previous sessions have been revoked'
    );
});

it('requires an explicit action to consume the email verification token', async () => {
    render(
        <MemoryRouter initialEntries={['/verify-email?token=verification']}>
            <VerifyEmailPage />
        </MemoryRouter>
    );
    expect(api.post).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Confirm my email' }));
    expect(api.post).toHaveBeenCalledWith('/auth/email-verification/confirm', {
        token: 'verification',
    });
    expect(await screen.findByRole('status')).toHaveTextContent('Your email is verified');
});

it('revokes all devices through the authentication context', async () => {
    const logout = vi.fn().mockResolvedValue();
    useAuth.mockReturnValue({ user: { emailVerified: true }, logout });
    render(<AccountSecurity />);
    await userEvent.click(screen.getByRole('button', { name: 'Sign out on all devices' }));
    expect(logout).toHaveBeenCalledWith(true);
});

it('requires an explicit opt-in before saving a shared seller phone', async () => {
    render(
        <MemoryRouter>
            <ProfilePage />
        </MemoryRouter>
    );
    await userEvent.click(screen.getByRole('button', { name: 'Edit profile' }));
    const consent = screen.getByRole('checkbox');
    expect(consent).not.toBeChecked();
    await userEvent.click(consent);
    await userEvent.click(screen.getByRole('button', { name: 'Save profile' }));
    expect(api.put).toHaveBeenCalledWith(
        '/profile/me',
        expect.objectContaining({ contactSharingEnabled: true })
    );
});
