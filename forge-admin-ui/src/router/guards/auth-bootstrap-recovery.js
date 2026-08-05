export function recoverFromAuthBootstrapFailure({ authStore, appStore, to, next }) {
  authStore.resetLoginState()
  appStore.setRouteGuardCompleted(true)

  const redirect = to?.path !== '/login' ? to?.fullPath : undefined
  next({
    path: '/login',
    ...(redirect ? { query: { redirect } } : {}),
    replace: true,
  })
}
