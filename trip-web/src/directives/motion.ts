import type { App, Directive } from 'vue'

/** Enter only: content stays visible without motion APIs, and routing never remounts a form. */
export function setupMotion(app: App): void {
  const preference = window.matchMedia('(prefers-reduced-motion: reduce)')
  const pending = new Map<HTMLElement, number>()
  const active = new Map<HTMLElement, Animation>()

  function stop(el: HTMLElement): void {
    active.get(el)?.cancel()
    active.delete(el)
  }

  function enter(el: HTMLElement, delay = 0, duration = 280): void {
    stop(el)
    if (preference.matches || !el.animate) return
    const animation = el.animate([
      { opacity: 0, transform: 'translateY(8px)' },
      { opacity: 1, transform: 'translateY(0)' },
    ], { duration, delay, easing: 'cubic-bezier(.22, 1, .36, 1)', fill: 'backwards' })
    active.set(el, animation)
    animation.onfinish = () => { if (active.get(el) === animation) active.delete(el) }
    animation.oncancel = animation.onfinish
  }

  const observer = typeof IntersectionObserver === 'undefined' ? null : new IntersectionObserver(entries => {
    for (const entry of entries) {
      const el = entry.target as HTMLElement
      if (!entry.isIntersecting) continue
      const delay = pending.get(el)
      pending.delete(el)
      observer?.unobserve(el)
      if (delay !== undefined) enter(el, delay)
    }
  }, { threshold: 0.08 })

  function cleanup(el: HTMLElement): void {
    pending.delete(el)
    observer?.unobserve(el)
    stop(el)
  }

  const reveal: Directive<HTMLElement, number | undefined> = {
    mounted(el, binding) {
      if (preference.matches || !observer) return
      const index = binding.value ?? 0
      pending.set(el, Number.isFinite(index) && index >= 0 && index < 6 ? index * 35 : 0)
      observer.observe(el)
    },
    beforeUnmount: cleanup,
  }
  const pageEnter: Directive<HTMLElement, string> = {
    mounted(el) { enter(el, 0, 220) },
    updated(el, binding) { if (binding.value !== binding.oldValue) enter(el, 0, 220) },
    beforeUnmount: cleanup,
  }

  function reduceMotion(): void {
    if (!preference.matches) return
    observer?.disconnect()
    pending.clear()
    for (const el of active.keys()) stop(el)
  }
  preference.addEventListener('change', reduceMotion)
  app.directive('reveal', reveal)
  app.directive('page-enter', pageEnter)
  app.onUnmount(() => {
    preference.removeEventListener('change', reduceMotion)
    observer?.disconnect()
    pending.clear()
    for (const el of active.keys()) stop(el)
  })
}
