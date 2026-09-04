package com.stocksense.stocksense_backend.repositories;

import com.stocksense.stocksense_backend.models.Session;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SessionRepository extends MongoRepository<Session, String> {

    Optional<Session> findBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);

    // Logout from all devices for a given user
    void deleteByUserEmail(String userEmail);
}
