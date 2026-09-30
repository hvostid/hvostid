import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/useAuth';
import { updateProfile } from '../api/profile';
import Input from '../components/Input';
import AccountSecurity from '../components/AccountSecurity';

const fieldsFrom = (profile) => ({
    name: profile?.name || '',
    phone: profile?.phone || '',
    city: profile?.city || '',
    bio: profile?.bio || '',
    contactSharingEnabled: Boolean(profile?.contactSharingEnabled),
});

export default function ProfilePage() {
    const { user, hasRole, addRole, reloadProfile } = useAuth();
    const [form, setForm] = useState(() => fieldsFrom(user));
    const [editing, setEditing] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const seller = hasRole('SELLER');
    const change = (field) => (event) =>
        setForm((current) => ({ ...current, [field]: event.target.value }));
    async function save(event) {
        event.preventDefault();
        setBusy(true);
        setError('');
        setMessage('');
        try {
            await updateProfile(form);
            await reloadProfile();
            setEditing(false);
            setMessage('Profile updated.');
        } catch (err) {
            setError(err.response?.data?.detail || 'Changes could not be saved. Please retry.');
        } finally {
            setBusy(false);
        }
    }
    async function becomeSeller() {
        setBusy(true);
        setError('');
        try {
            await addRole('SELLER');
            setMessage('Your seller account is ready.');
        } catch (err) {
            setError(err.response?.data?.detail || 'Seller access could not be enabled.');
        } finally {
            setBusy(false);
        }
    }
    return (
        <div className="mx-auto max-w-2xl space-y-6">
            <h1 className="text-2xl font-bold">My profile</h1>
            {error && (
                <p role="alert" className="text-red-700">
                    {error}
                </p>
            )}
            {message && (
                <p role="status" className="text-green-700">
                    {message}
                </p>
            )}
            <section className="rounded-lg bg-white p-6 shadow space-y-4">
                <p>Email: {user?.email}</p>
                {editing ? (
                    <form onSubmit={save} className="space-y-4">
                        <Input
                            id="profile-name"
                            label="Name"
                            value={form.name}
                            onChange={change('name')}
                            required
                            maxLength={255}
                        />
                        <Input
                            id="profile-phone"
                            label="Phone"
                            type="tel"
                            value={form.phone}
                            onChange={change('phone')}
                            maxLength={30}
                        />
                        <Input
                            id="profile-city"
                            label="City"
                            value={form.city}
                            onChange={change('city')}
                            maxLength={255}
                        />
                        <label htmlFor="profile-bio" className="block">
                            About you
                        </label>
                        <textarea
                            id="profile-bio"
                            value={form.bio}
                            onChange={change('bio')}
                            maxLength={2000}
                            className="w-full rounded border p-2"
                            rows={3}
                        />
                        {seller && (
                            <label className="flex items-start gap-2">
                                <input
                                    type="checkbox"
                                    checked={form.contactSharingEnabled}
                                    onChange={(event) =>
                                        setForm((current) => ({
                                            ...current,
                                            contactSharingEnabled: event.target.checked,
                                        }))
                                    }
                                />
                                Share my phone number with signed-in buyers so they can contact me
                                about my listings.
                            </label>
                        )}
                        <div className="flex gap-3">
                            <button
                                disabled={busy}
                                className="rounded bg-indigo-600 px-4 py-2 text-white"
                            >
                                {busy ? 'Saving...' : 'Save profile'}
                            </button>
                            <button
                                type="button"
                                disabled={busy}
                                onClick={() => {
                                    setEditing(false);
                                    setError('');
                                }}
                            >
                                Cancel
                            </button>
                        </div>
                    </form>
                ) : (
                    <>
                        <dl className="space-y-2">
                            <div>
                                <dt className="font-medium">Name</dt>
                                <dd>{user?.name || '-'}</dd>
                            </div>
                            <div>
                                <dt className="font-medium">Phone</dt>
                                <dd>{user?.phone || '-'}</dd>
                            </div>
                            <div>
                                <dt className="font-medium">City</dt>
                                <dd>{user?.city || '-'}</dd>
                            </div>
                            <div>
                                <dt className="font-medium">About you</dt>
                                <dd>{user?.bio || '-'}</dd>
                            </div>
                        </dl>
                        {seller && (
                            <p>
                                Phone sharing:{' '}
                                {user?.contactSharingEnabled
                                    ? 'Enabled for signed-in buyers'
                                    : 'Off'}
                            </p>
                        )}
                        <button
                            className="text-indigo-700 underline"
                            onClick={() => {
                                setForm(fieldsFrom(user));
                                setEditing(true);
                            }}
                        >
                            Edit profile
                        </button>
                    </>
                )}
                <p>Roles: {user?.roles?.join(', ')}</p>
                <Link to="/profile/questionnaire" className="text-indigo-700 underline">
                    Compatibility questionnaire
                </Link>
                {!seller && (
                    <div className="border-t pt-4">
                        <p className="mb-3">Enable seller access to create animal listings.</p>
                        <button
                            disabled={busy}
                            onClick={becomeSeller}
                            className="rounded bg-indigo-600 px-4 py-2 text-white"
                        >
                            Become a seller
                        </button>
                    </div>
                )}
            </section>
            <AccountSecurity />
        </div>
    );
}
