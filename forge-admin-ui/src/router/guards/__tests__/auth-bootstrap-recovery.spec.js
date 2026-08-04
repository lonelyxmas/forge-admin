import { describe, expect, it, vi } from 'vitest'
import { recoverFromAuthBootstrapFailure } from '../auth-bootstrap-recovery'

describe('auth bootstrap recovery', () => {
  it('clears the invalid login state and redirects to login once', () => {
    const authStore = { resetLoginState: vi.fn() }
    const appStore = { setRouteGuardCompleted: vi.fn() }
    const next = vi.fn()

    recoverFromAuthBootstrapFailure({
      authStore,
      appStore,
      to: { path: '/system/user', fullPath: '/system/user?pageNum=2' },
      next,
    })

    expect(authStore.resetLoginState).toHaveBeenCalledTimes(1)
    expect(appStore.setRouteGuardCompleted).toHaveBeenCalledOnce()
    expect(appStore.setRouteGuardCompleted).toHaveBeenCalledWith(true)
    expect(next).toHaveBeenCalledOnce()
    expect(next).toHaveBeenCalledWith({
      path: '/login',
      query: { redirect: '/system/user?pageNum=2' },
      replace: true,
    })
  })

  it('does not create a self redirect when the failed target is login', () => {
    const next = vi.fn()

    recoverFromAuthBootstrapFailure({
      authStore: { resetLoginState: vi.fn() },
      appStore: { setRouteGuardCompleted: vi.fn() },
      to: { path: '/login', fullPath: '/login' },
      next,
    })

    expect(next).toHaveBeenCalledWith({
      path: '/login',
      replace: true,
    })
  })
})
