// Mirrors ru.hvostid.passport.service.PassportDocumentValidator in passport-service.
// Backend rejects everything else with HTTP 415, so keep these in sync.

export const PHOTO_MIME_TYPES = ['image/jpeg', 'image/png'];
export const DOCUMENT_MIME_TYPES = ['image/jpeg', 'image/png', 'application/pdf'];

// String for the `accept` attribute on <input type="file">. Narrower than
// `image/*` so the file picker hides webp/gif/svg the backend would refuse.
export const PHOTO_ACCEPT = PHOTO_MIME_TYPES.join(',');
export const DOCUMENT_ACCEPT = DOCUMENT_MIME_TYPES.join(',');
