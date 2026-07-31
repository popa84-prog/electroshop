package com.electroshop.security;

import com.electroshop.model.RoleName;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Bean invoked from {@code @PreAuthorize("@permissionService.has('...')")}
 * expressions. Resolves the current authentication's granted authorities
    * (role names, e.g. "ROLE_ADMIN") to {@link RoleName}s and checks whether any
    * of them grant the requested {@link Permission} via {@link RolePermissions}.
    */
@Service("permissionService")
  public class PermissionService {

    public boolean has(String permissionName) {
              var auth = SecurityContextHolder.getContext().getAuthentication();
              if (auth == null || !auth.isAuthenticated()) {
                            return false;
              }
              Permission target;
              try {
                            target = Permission.valueOf(permissionName);
              } catch (IllegalArgumentException e) {
                            return false;
              }
              for (GrantedAuthority authority : auth.getAuthorities()) {
                            RoleName role;
                            try {
                                              role = RoleName.valueOf(authority.getAuthority());
                            } catch (IllegalArgumentException e) {
                                              continue;
                            }
                            if (RolePermissions.of(role).contains(target)) {
                                              return true;
                            }
              }
              return false;
    }
  }
