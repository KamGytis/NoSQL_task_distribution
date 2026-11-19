package lt.vu.tracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableScheduling
public class SearchConfig {
    @Bean("searchHttpClient")
    public HttpClient searchHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }
}