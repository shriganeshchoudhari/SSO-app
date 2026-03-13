package com.openidentity.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "user_role")
@IdClass(UserRoleId.class)
public class UserRoleEntity {
  @Id
  @Column(name = "user_id", nullable = false)
  private UUID user;

  @Id
  @Column(name = "role_id", nullable = false)
  private UUID role;

  public UUID getUser() {
    return user;
  }
  public void setUser(UUID user) {
    this.user = user;
  }
  public UUID getRole() {
    return role;
  }
  public void setRole(UUID role) {
    this.role = role;
  }
}

