package com.namnd.cinema.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/test")
@Tag(name = "Test", description = "Role-based access test endpoints")
public class TestController {

    @Operation(summary = "User-level access test")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/user")
    @PreAuthorize("hasRole('USER') or hasRole('PM') or hasRole('ADMIN')")
    public ResponseEntity<?> userAccess(Authentication authentication) {
        return ResponseEntity.ok(buildResponse(authentication, "User Content"));
    }

    @Operation(summary = "PM-level access test")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/pm")
    @PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
    public ResponseEntity<?> pmAccess(Authentication authentication) {
        return ResponseEntity.ok(buildResponse(authentication, "Project Manager Board"));
    }

    @Operation(summary = "Admin-level access test")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminAccess(Authentication authentication) {
        return ResponseEntity.ok(buildResponse(authentication, "Admin Board"));
    }

    private Map<String, Object> buildResponse(Authentication auth, String content) {
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("message", content);
        response.put("username", auth.getName());
        response.put("roles", roles);
        return response;
    }
}
