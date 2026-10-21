/**
 * 极简 Hash 路由（成员 D：路由管理）
 * 支持 /interviews/:id 这类路径参数，不引入第三方路由库，保持零构建依赖。
 */

export function parseLocation() {
    const hash = location.hash || '#/dashboard';
    const rawPath = hash.replace(/^#/, '') || '/dashboard';
    const [pathPart, queryPart] = rawPath.split('?');
    const path = pathPart || '/dashboard';
    const segments = path.split('/').filter(Boolean);
    const query = {};
    if (queryPart) {
        new URLSearchParams(queryPart).forEach((value, key) => {
            query[key] = value;
        });
    }
    return { path: '/' + segments.join('/'), segments, query };
}

export function navigate(path) {
    if (location.hash === '#' + path) {
        return;
    }
    location.hash = '#' + path;
}

/** 在路由表中匹配路由，返回 { route, params } 或 null */
export function matchRoute(routes, segments) {
    for (const route of routes) {
        const parts = route.path.split('/').filter(Boolean);
        if (parts.length !== segments.length) {
            continue;
        }
        const params = {};
        let matched = true;
        for (let i = 0; i < parts.length; i += 1) {
            if (parts[i].startsWith(':')) {
                params[parts[i].slice(1)] = decodeURIComponent(segments[i]);
            } else if (parts[i] !== segments[i]) {
                matched = false;
                break;
            }
        }
        if (matched) {
            return { route, params };
        }
    }
    return null;
}

/** 启动路由监听 */
export function startRouter(routes, onRoute) {
    const handle = () => {
        const locationInfo = parseLocation();
        const matched = matchRoute(routes, locationInfo.segments);
        onRoute(matched, locationInfo);
    };
    window.addEventListener('hashchange', handle);
    handle();
}
