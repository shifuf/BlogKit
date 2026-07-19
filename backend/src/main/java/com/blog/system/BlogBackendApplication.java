package com.blog.system;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class BlogBackendApplication {

	/** 强制 JVM 默认时区为北京时间，避免容器/宿主机时区不一致导致存库时间偏差。 */
	@PostConstruct
	void initTimeZone() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
	}

	public static void main(String[] args) {
		SpringApplication.run(BlogBackendApplication.class, args);
	}

}
