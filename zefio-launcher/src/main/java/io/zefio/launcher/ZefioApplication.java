package io.zefio.launcher;

import io.zefio.core.ZefioCoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@ComponentScan(basePackages = {"io.zefio"})
public class ZefioApplication implements CommandLineRunner {

	@Autowired
	private ZefioCoreService service;

	@Override
	public void run(String... args) {
		this.service.execute();
	}

	public static void main(String[] args) {
		SpringApplication.run(ZefioApplication.class, args);
	}
}
