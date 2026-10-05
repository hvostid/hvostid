import { expect, it } from 'vitest';
import { collectPages, mapConcurrent } from './pages';
import { canonicalPassportId } from '../utils/passportId';

it('collects entries beyond the first 100 without dropping a page', async () => {
    const values = await collectPages(async (page) => ({
        content: Array.from({ length: page ? 1 : 100 }, (_, index) => page * 100 + index),
        totalPages: 2,
    }));
    expect(values).toHaveLength(101);
    expect(values.at(-1)).toBe(100);
});
it('bounds in-flight enrichment work and preserves ordering', async () => {
    let active = 0,
        maximum = 0;
    const result = await mapConcurrent(
        [1, 2, 3, 4, 5],
        async (n) => {
            active += 1;
            maximum = Math.max(active, maximum);
            await new Promise((resolve) => setTimeout(resolve, 1));
            active -= 1;
            return n * 2;
        },
        2
    );
    expect(maximum).toBe(2);
    expect(result).toEqual([2, 4, 6, 8, 10]);
});
it('normalizes legacy IDs without losing 64-bit precision', () => {
    expect(canonicalPassportId('passport-00042')).toBe('42');
    expect(canonicalPassportId('9223372036854775807')).toBe('9223372036854775807');
    expect(canonicalPassportId('9223372036854775808')).toBeNull();
    expect(canonicalPassportId('no-id')).toBeNull();
    expect(canonicalPassportId('0')).toBeNull();
});
