/**
 * 可靠返回上一页。
 * router.replace(back) 会导致 history.state.back 指向替换前的页面，
 * 形成 A→B→replace(A) 后 A.back=B、再返回又 replace(B) 的死循环。
 * 因此在有历史记录时改用 router.back() 真正弹出栈顶。
 *
 * @param {import('vue-router').Router} router
 * @param {string | { path: string, query?: object }} fallback 无历史时的去向
 */
export function safeBack(router, fallback = '/policies') {
  const back = window.history.state?.back
  const current = router.currentRoute.value.fullPath
  if (typeof back === 'string' && back.startsWith('/') && back !== current) {
    router.back()
    return
  }
  if (typeof fallback === 'string') {
    router.replace(fallback)
    return
  }
  router.replace(fallback)
}