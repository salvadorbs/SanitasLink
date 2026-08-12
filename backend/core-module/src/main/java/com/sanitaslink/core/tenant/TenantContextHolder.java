package com.sanitaslink.core.tenant;

/** Thread-local holder for the current request {@link TenantContext}. */
public final class TenantContextHolder {

  private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

  private TenantContextHolder() {}

  public static void set(TenantContext context) {
    CONTEXT.set(context);
  }

  public static TenantContext get() {
    return CONTEXT.get();
  }

  /**
   * @return the current context or throws when no authenticated context is present.
   */
  public static TenantContext require() {
    TenantContext context = CONTEXT.get();
    if (context == null) {
      throw new IllegalStateException("No tenant context available");
    }
    return context;
  }

  public static void clear() {
    CONTEXT.remove();
  }
}
