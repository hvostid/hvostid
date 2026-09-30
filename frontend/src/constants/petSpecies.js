export const PET_SPECIES = [
    { value: 'CAT', label: 'Cat' },
    { value: 'DOG', label: 'Dog' },
    { value: 'BIRD', label: 'Bird' },
    { value: 'RODENT', label: 'Rodent' },
    { value: 'RABBIT', label: 'Rabbit' },
    { value: 'FISH', label: 'Fish' },
    { value: 'OTHER', label: 'Other' },
];
export const CATALOG_SPECIES = [
    { value: '', label: 'Any species' },
    ...PET_SPECIES.map(({ value, label }) => ({ value: value.toLowerCase(), label })),
];
