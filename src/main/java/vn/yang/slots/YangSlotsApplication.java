package vn.yang.slots;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class YangSlotsApplication {
  public static void main(String[] args) {
    SpringApplication.run(YangSlotsApplication.class, args);
  }

  @Bean
  Clock systemClock() {
    return Clock.systemUTC();
  }
}
