package com.youlai.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动类
 *
 * @author Ray.Hao
 * @since 0.0.1
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync  // 启用异步支持 (Telegram Bot 需要)
public class YouLaiBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(YouLaiBootApplication.class, args);
    }

}
