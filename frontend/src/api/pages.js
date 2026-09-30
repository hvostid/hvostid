export async function collectPages(fetchPage, signal) {
    const content = [];
    for (let page = 0; ; page += 1) {
        signal?.throwIfAborted();
        const result = await fetchPage(page, signal);
        content.push(...(result.content || []));
        if (page + 1 >= result.totalPages || !result.content?.length) return content;
    }
}

export async function mapConcurrent(items, mapper, limit = 4) {
    const results = new Array(items.length);
    let next = 0;
    await Promise.all(
        Array.from({ length: Math.min(limit, items.length) }, async () => {
            while (next < items.length) {
                const index = next++;
                results[index] = await mapper(items[index]);
            }
        })
    );
    return results;
}
