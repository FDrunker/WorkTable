export const ADMIN_ROUTE_PREFIX = '/admin'

export function withAdminPrefix(path: string): string {
  if (!path || path === ADMIN_ROUTE_PREFIX || path.startsWith(`${ADMIN_ROUTE_PREFIX}/`)) {
    return path
  }
  if (/^https?:\/\//i.test(path)) {
    return path
  }
  if (path === '/') {
    return ADMIN_ROUTE_PREFIX
  }
  return path.startsWith('/')
    ? `${ADMIN_ROUTE_PREFIX}${path}`
    : `${ADMIN_ROUTE_PREFIX}/${path}`
}

export function prefixAdminRoutes(routes: any[]): any[] {
  return routes.map((route) => prefixAdminRoute(route, true))
}

function prefixAdminRoute(route: any, root: boolean): any {
  const next = { ...route }
  if (root || String(next.path || '').startsWith('/')) {
    next.path = withAdminPrefix(String(next.path || ''))
  }
  if (next.meta?.activeMenu) {
    next.meta = { ...next.meta, activeMenu: withAdminPrefix(String(next.meta.activeMenu)) }
  }
  if (Array.isArray(next.children)) {
    next.children = next.children.map((child: any) => prefixAdminRoute(child, false))
  }
  return next
}
