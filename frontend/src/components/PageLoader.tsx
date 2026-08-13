/** Minimal full-page loading marker used while the session is being resolved. */
export function PageLoader() {
  return (
    <div className="page-loader" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <span>Caricamento…</span>
    </div>
  );
}
