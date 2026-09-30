export function returnPath(value, fallback = '/') {
    return typeof value === 'string' &&
        value.startsWith('/') &&
        !value.startsWith('//') &&
        !Array.from(value).some(
            (character) =>
                character === '\\' ||
                character.charCodeAt(0) < 32 ||
                character.charCodeAt(0) === 127
        )
        ? value
        : fallback;
}
