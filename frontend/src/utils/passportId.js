export function canonicalPassportId(value) {
    const digits = String(value ?? '').replace(/^passport-/, '');
    if (!/^\d+$/.test(digits)) return null;
    const id = BigInt(digits);
    return id > 0n && id <= 9223372036854775807n ? id.toString() : null;
}
