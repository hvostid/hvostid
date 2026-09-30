import { expect, it } from 'vitest';
import { returnPath } from './returnPath';

it('accepts internal routes and rejects external or ambiguous browser paths', () => {
    expect(returnPath('/listings/42/match?tab=details')).toBe('/listings/42/match?tab=details');
    for (const path of [
        'https://outside.example',
        '//outside.example',
        '/\\outside.example',
        '/\noutside.example',
        null,
    ]) {
        expect(returnPath(path, '/profile')).toBe('/profile');
    }
});
