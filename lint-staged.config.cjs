const escapeRegex = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

module.exports = {
    '*.java': (files) => {
        const pattern = files.map(escapeRegex).join(',');
        return `./gradlew --quiet spotlessApply -PspotlessFiles=${pattern}`;
    },
};
