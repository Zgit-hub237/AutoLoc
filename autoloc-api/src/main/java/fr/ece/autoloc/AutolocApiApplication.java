package fr.ece.autoloc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // daily update of reservation and vehicle statuses (FlotteService)
public class AutolocApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(AutolocApiApplication.class, args);
	}

}
