package edu.campusnexo.infrastructure.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import edu.campusnexo.application.port.out.TwinHistoryPort;
import edu.campusnexo.infrastructure.adapter.out.history.MongoTwinHistoryAdapter;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The twin history in MongoDB, only when {@code app.history.mongodb-uri} is set (MongoDB Atlas in production,
 * the {@code mongo} container in Compose). Without it the twin works the same and the history is empty.
 */
@Configuration
class HistoryConfiguration {
    private static final Logger log = LoggerFactory.getLogger(HistoryConfiguration.class);

    @Bean(destroyMethod = "close")
    @ConditionalOnExpression("'${app.history.mongodb-uri:}' != ''")
    MongoClient mongoClient(@Value("${app.history.mongodb-uri}") String uri) {
        return MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .applyToClusterSettings(cluster -> cluster.serverSelectionTimeout(3, TimeUnit.SECONDS))
                .applyToSocketSettings(socket -> socket.connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS))
                .build());
    }

    @Bean
    @ConditionalOnExpression("'${app.history.mongodb-uri:}' != ''")
    MongoTwinHistoryAdapter mongoTwinHistory(MongoClient client, @Value("${app.history.database}") String database) {
        MongoTwinHistoryAdapter adapter = new MongoTwinHistoryAdapter(client.getDatabase(database));
        try {
            adapter.createIndexes();
        } catch (RuntimeException unavailable) {
            // Atlas may still be waking up; the history is best effort and the twin must start anyway.
            log.warn("No se pudieron crear los índices del historial en MongoDB: {}", unavailable.getMessage());
        }
        return adapter;
    }

    @Bean
    @ConditionalOnMissingBean(TwinHistoryPort.class)
    TwinHistoryPort noTwinHistory() {
        return new TwinHistoryPort() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public List<OccupancySample> occupancy(String spaceId, Instant from, Instant to) {
                return List.of();
            }
        };
    }
}
