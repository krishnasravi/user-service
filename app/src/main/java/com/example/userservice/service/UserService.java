package com.example.userservice.service;

import com.example.userservice.model.User;
import com.example.userservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    public UserService(UserRepository userRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.userRepository = userRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public User register(User user) {
        User saved = userRepository.save(user);
        String message = "New user registered: " +
                         saved.getId() + ", " +
                         saved.getUsername() + ", " +
                         saved.getEmail();
        kafkaTemplate.send("user-topic", message);
        return saved;
    }

    public boolean login(String email, String password) {
        return userRepository.findByEmail(email)
                .map(u -> u.getPassword().equals(password))
                .orElse(false);
    }
}
