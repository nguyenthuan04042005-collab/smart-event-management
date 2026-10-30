package com.event.sems.repository;

import com.event.sems.entity.UserAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
  @EntityGraph(attributePaths = "roles")
  Optional<UserAccount> findByUsernameIgnoreCase(String username);

  boolean existsByUsernameIgnoreCase(String username);

  boolean existsByEmailIgnoreCase(String email);

  Optional<UserAccount> findByEmailIgnoreCase(String email);

  @EntityGraph(attributePaths = "roles")
  @Query("select distinct u from UserAccount u order by u.id")
  List<UserAccount> findAllWithRoles();

  @EntityGraph(attributePaths = "roles")
  @Query("select u from UserAccount u where u.id = :id")
  Optional<UserAccount> findWithRolesById(Long id);
}
