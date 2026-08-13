import { Loader2 } from 'lucide-react';

/** Minimal full-page loading marker used while the session is being resolved. */
export function PageLoader() {
  return (
    <div className="flex min-h-svh flex-1 items-center justify-center gap-3" role="status" aria-live="polite">
      <Loader2 className="size-5 animate-spin" aria-hidden="true" />
      <span>Caricamento…</span>
    </div>
  );
}
