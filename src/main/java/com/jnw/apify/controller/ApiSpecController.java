package com.jnw.apify.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
public class ApiSpecController {
    @GetMapping("/api-spec")
    public String getApiSpec() throws Exception {
        Path path = Paths.get("src/main/resources/openapi-spec.yaml"); // Update if JSON format
        return new String(Files.readAllBytes(path));
    }
}
