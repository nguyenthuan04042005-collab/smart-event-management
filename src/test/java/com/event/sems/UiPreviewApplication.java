package com.event.sems;

import com.event.sems.entity.Venue;
import com.event.sems.repository.RoleRepository;
import com.event.sems.repository.UserAccountRepository;
import com.event.sems.repository.VenueRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Máy chủ kiểm tra giao diện với dữ liệu tạm H2, không dùng CSDL MySQL. */
public class UiPreviewApplication {
  public static void main(String[] args) {
    new SpringApplication(SemsApplication.class, PreviewData.class)
        .run(
            "--server.port=" + System.getProperty("preview.port", "8081"),
            "--server.address=127.0.0.1",
            "--spring.datasource.url=jdbc:h2:mem:ui_preview;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "--spring.datasource.driver-class-name=org.h2.Driver",
            "--spring.datasource.username=sa",
            "--spring.datasource.password=",
            "--spring.jpa.hibernate.ddl-auto=create-drop");
  }

  @TestConfiguration
  static class PreviewData {
    @Bean
    ApplicationRunner seedPreview(
        RoleRepository roles,
        UserAccountRepository users,
        PasswordEncoder passwords,
        VenueRepository venues) {
      return args -> {
        SampleAccounts.seed(roles, users, passwords);
        Venue venue = new Venue();
        venue.setOrganizer(users.findByUsernameIgnoreCase("organizer").orElseThrow());
        venue.setName("Hội trường A");
        venue.setAddress("Cơ sở chính");
        venue.setCapacity(120);
        venues.save(venue);
        Venue other = new Venue();
        other.setOrganizer(users.findByUsernameIgnoreCase("organizer").orElseThrow());
        other.setName("Phòng hội thảo B");
        other.setAddress("Tầng 2, khu B");
        other.setCapacity(60);
        venues.save(other);
      };
    }
  }
}
