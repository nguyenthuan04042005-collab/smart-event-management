package com.event.sems.repository;

import com.event.sems.entity.MailSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MailSettingsRepository extends JpaRepository<MailSettings, Long> {}
