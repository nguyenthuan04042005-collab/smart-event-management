package com.event.sems.repository;

import com.event.sems.entity.RevokedJwt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevokedJwtRepository extends JpaRepository<RevokedJwt, String> { }
