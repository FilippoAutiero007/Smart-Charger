export const api: any = new Proxy({}, {
    get: (_, prop) => {
        if (typeof prop === 'string') {
            return new Proxy({}, {
                get: (_, subProp) => {
                    if (typeof subProp === 'string') {
                        return `${prop}:${subProp}`;
                    }
                }
            });
        }
    }
});
