package com.sanitaslink.app;

import com.sanitaslink.core.domain.User;
import com.sanitaslink.core.domain.UserRole;
import com.sanitaslink.core.domain.UserStatus;
import com.sanitaslink.core.repository.RoleRepository;
import com.sanitaslink.core.repository.UserRepository;
import com.sanitaslink.core.repository.UserRoleRepository;
import com.sanitaslink.core.tenant.TenantContextManager;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Seeds platform-level test data in its own transactions (RLS-aware). */
@Component
public class TestDataSeeder {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final UserRoleRepository userRoleRepository;
  private final PasswordEncoder passwordEncoder;
  private final TenantContextManager tenantContextManager;

  public TestDataSeeder(
      UserRepository userRepository,
      RoleRepository roleRepository,
      UserRoleRepository userRoleRepository,
      PasswordEncoder passwordEncoder,
      TenantContextManager tenantContextManager) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.userRoleRepository = userRoleRepository;
    this.passwordEncoder = passwordEncoder;
    this.tenantContextManager = tenantContextManager;
  }

  @Transactional
  public UUID createAdmin(String email, String password) {
    User user = User.invited(UUID.randomUUID(), email, "Platform", "Admin", null);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setStatus(UserStatus.ACTIVE);
    userRepository.save(user);

    var adminRole =
        roleRepository
            .findByCode("ADMIN")
            .orElseThrow(() -> new IllegalStateException("ADMIN role missing"));
    tenantContextManager.initialize(null, user.getId(), true);
    userRoleRepository.save(new UserRole(user.getId(), adminRole.getId(), null));
    return user.getId();
  }
}
