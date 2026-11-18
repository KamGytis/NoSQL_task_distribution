package lt.vu.tracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.Properties;

@Configuration
public class ClickHouseConfig {
    @Bean("clickHouseJdbcTemplate")
    public JdbcTemplate clickHouseJdbcTemplate(
            @Value("${clickhouse.url}") String url,
            @Value("${clickhouse.username}") String username,
            @Value("${clickhouse.password}") String password
    ) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        
        Properties properties = new Properties();
        properties.setProperty("compress", "false");
        properties.setProperty("decompress", "false");
        dataSource.setConnectionProperties(properties);

        return new JdbcTemplate(dataSource);
    }
}