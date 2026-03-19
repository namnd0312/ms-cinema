package com.namnd.cinema.controller;

import com.namnd.cinema.dto.*;
import com.namnd.cinema.dto.mapper.RegisterDtoMapper;
import com.namnd.cinema.model.RefreshToken;
import com.namnd.cinema.model.Role;
import com.namnd.cinema.model.User;
import com.namnd.cinema.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import io.micrometer.core.instrument.Counter;
import jakarta.validation.Valid;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Login, registration, password reset, token management")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RegisterDtoMapper registerDtoMapper;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private BlacklistedTokenService blacklistedTokenService;

    @Autowired
    private ActivationService activationService;

    @Autowired
    private AccountLockService accountLockService;

    @Autowired
    private Counter authLoginSuccessCounter;

    @Autowired
    private Counter authLoginFailureCounter;

    @Autowired
    private Counter authRegisterCounter;

    @Autowired
    private Counter authTokenRefreshCounter;

    @Autowired
    private PasswordHistoryService passwordHistoryService;

    @Autowired
    private Counter authLogoutCounter;

    @Operation(summary = "Login with email and password")
    @ApiResponse(responseCode = "200", description = "Login successful")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @ApiResponse(responseCode = "423", description = "Account locked")
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequestDto loginRequest) {
        try {
            // Pre-auth: check if account lock expired, auto-unlock if so
            Optional<User> userOpt = userService.findByEmail(loginRequest.getEmail());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                // OAuth-only users cannot login with password
                if (user.getPassword() == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("This account uses Google login. Please sign in with Google.");
                }
                if (!accountLockService.unlockIfExpired(user)) {
                    long remainingMs = accountLockService.getRemainingLockTimeMs(user);
                    long remainingMin = (remainingMs / 60000) + 1;
                    return ResponseEntity.status(423)
                            .body("Account is locked. Try again in " + remainingMin + " minute(s).");
                }
            }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Success: reset failed attempts
            accountLockService.resetFailedAttempts(loginRequest.getEmail());

            String jwt = jwtService.generateTokenLogin(authentication);
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User currentUser = userService.findByEmail(loginRequest.getEmail())
                    .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found"));

            RefreshToken refreshToken = refreshTokenService.createRefreshToken(currentUser.getId());
            authLoginSuccessCounter.increment();

            return ResponseEntity.ok(new JwtResponseDto(
                    currentUser.getId(),
                    jwt,
                    refreshToken.getToken(),
                    currentUser.getEmail(),
                    currentUser.getUsername(),
                    currentUser.getFullName(),
                    userDetails.getAuthorities()));

        } catch (BadCredentialsException e) {
            authLoginFailureCounter.increment();
            accountLockService.registerFailedAttempt(loginRequest.getEmail());

            // Check if this attempt caused a lock
            Optional<User> userOpt = userService.findByEmail(loginRequest.getEmail());
            if (userOpt.isPresent() && accountLockService.isLocked(userOpt.get())) {
                long remainingMs = accountLockService.getRemainingLockTimeMs(userOpt.get());
                long remainingMin = (remainingMs / 60000) + 1;
                return ResponseEntity.status(423)
                        .body("Too many failed attempts. Account locked for " + remainingMin + " minute(s).");
            }

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid email or password.");

        } catch (LockedException e) {
            authLoginFailureCounter.increment();
            return ResponseEntity.status(423)
                    .body("Account is locked. Please try again later.");

        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Account not activated. Please check your email for the activation link.");
        }
    }

    @Operation(summary = "Register a new user account")
    @ApiResponse(responseCode = "200", description = "Registration successful, activation email sent")
    @ApiResponse(responseCode = "400", description = "Email already in use or invalid")
    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@RequestBody RegisterDto registerDto) {
        // Validate email: required and unique
        if (registerDto.getEmail() == null || registerDto.getEmail().trim().isEmpty()) {
            return new ResponseEntity<>("Fail -> Email is required!",
                    HttpStatus.BAD_REQUEST);
        }
        if (userService.existsByEmail(registerDto.getEmail())) {
            return new ResponseEntity<>("Fail -> Email is already in use!",
                    HttpStatus.BAD_REQUEST);
        }

        Set<Role> roles = registerDto.getRoles();

        // Default to ROLE_USER when no roles provided (e.g. frontend registration)
        if (roles == null || roles.isEmpty()) {
            Role defaultRole = roleService.findByName("ROLE_USER");
            if (defaultRole == null) {
                defaultRole = new Role();
                defaultRole.setName("ROLE_USER");
                roleService.save(defaultRole);
                roleService.flush();
            }
            roles = Set.of(defaultRole);
            registerDto.setRoles(roles);
        } else {
            for (Role role : roles) {
                if (roleService.findByName(role.getName()) == null) {
                    roleService.save(role);
                    roleService.flush();
                } else {
                    role.setId(roleService.findByName(role.getName()).getId());
                }
            }
        }

        User user1 = registerDtoMapper.toEntity(registerDto);
        userService.save(user1);
        passwordHistoryService.savePasswordToHistory(user1, user1.getPassword());
        activationService.createActivationToken(user1);
        authRegisterCounter.increment();

        return ResponseEntity.ok().body("User registered successfully! Please check your email to activate your account.");
    }

    @Operation(summary = "Activate account via email token")
    @ApiResponse(responseCode = "200", description = "Account activated")
    @ApiResponse(responseCode = "400", description = "Invalid or expired token")
    @GetMapping("/activate")
    public ResponseEntity<?> activateAccount(@RequestParam("token") String token) {
        try {
            activationService.activateAccount(token);
            return ResponseEntity.ok("Account activated successfully! You can now login.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(summary = "Resend account activation email")
    @PostMapping("/resend-activation")
    public ResponseEntity<?> resendActivation(@RequestBody ForgotPasswordDto request) {
        // Always returns 200 to prevent email enumeration
        activationService.resendActivationToken(request.getEmail());
        return ResponseEntity.ok("If the email is registered and not yet activated, an activation email has been sent.");
    }

    @Operation(summary = "Request a password reset email")
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordDto forgotPasswordDto) {
        passwordResetService.createPasswordResetToken(forgotPasswordDto.getEmail());
        return ResponseEntity.ok("If the email exists, a password reset link has been sent.");
    }

    @Operation(summary = "Reset password using token from email")
    @ApiResponse(responseCode = "200", description = "Password reset successful")
    @ApiResponse(responseCode = "400", description = "Invalid or expired token")
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordDto resetPasswordDto) {
        try {
            passwordResetService.resetPassword(
                    resetPasswordDto.getToken(),
                    resetPasswordDto.getNewPassword());
            return ResponseEntity.ok("Password reset successful.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(summary = "Rotate refresh token and issue new access token")
    @ApiResponse(responseCode = "200", description = "New tokens issued")
    @ApiResponse(responseCode = "400", description = "Invalid or expired refresh token")
    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(
            @RequestBody RefreshTokenRequestDto refreshTokenRequestDto) {
        String requestRefreshToken = refreshTokenRequestDto.getRefreshToken();

        Optional<RefreshToken> tokenOptional = refreshTokenService
                .findByToken(requestRefreshToken);

        if (!tokenOptional.isPresent()) {
            return ResponseEntity.badRequest().body("Invalid refresh token.");
        }

        try {
            // Atomic: verify expiry + delete old + create new (single transaction)
            RefreshToken newRefreshToken = refreshTokenService
                    .rotateRefreshToken(tokenOptional.get());
            User user = newRefreshToken.getUser();

            List<String> roleNames = user.getRoles().stream()
                    .map(Role::getName)
                    .toList();
            String newAccessToken = jwtService.generateTokenFromEmail(
                    user.getEmail(), user.getId(), roleNames);

            authTokenRefreshCounter.increment();
            return ResponseEntity.ok(new TokenRefreshResponseDto(
                    newAccessToken, newRefreshToken.getToken()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(summary = "Logout and blacklist current access token")
    @ApiResponse(responseCode = "200", description = "Logged out successfully")
    @ApiResponse(responseCode = "400", description = "No valid token provided")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String jwt = getJwtFromRequest(request);

        if (jwt == null || !jwtService.validateJwtToken(jwt)) {
            return ResponseEntity.badRequest().body("No valid token provided.");
        }

        // Reject already-blacklisted tokens
        String jti = jwtService.getJtiFromToken(jwt);
        if (blacklistedTokenService.isTokenBlacklisted(jti)) {
            return ResponseEntity.badRequest().body("Token already invalidated.");
        }

        // Blacklist the current access token by JTI
        Date tokenExpiry = jwtService.getExpirationFromToken(jwt);
        blacklistedTokenService.blacklistToken(jti, tokenExpiry);

        // Delete the user's refresh token
        String email = jwtService.getEmailFromJwtToken(jwt);
        Optional<User> userOptional = userService.findByEmail(email);
        userOptional.ifPresent(user -> refreshTokenService.deleteByUserId(user.getId()));
        authLogoutCounter.increment();

        return ResponseEntity.ok("Logged out successfully.");
    }

    @Operation(summary = "Change password for authenticated user")
    @ApiResponse(responseCode = "200", description = "Password changed successfully")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @SecurityRequirement(name = "bearerAuth")
    @Transactional
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordDto dto) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userService.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

        // OAuth-only users must set password via forgot-password flow first
        if (user.getPassword() == null) {
            return ResponseEntity.badRequest()
                .body("Cannot change password for OAuth-only accounts. Use forgot-password to set a password first.");
        }

        if (!encoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body("Current password is incorrect");
        }

        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            return ResponseEntity.badRequest().body("New password and confirm password do not match");
        }

        if (passwordHistoryService.isPasswordReused(user, dto.getNewPassword())) {
            return ResponseEntity.badRequest().body("Cannot reuse your 3 most recent passwords");
        }

        String encoded = encoder.encode(dto.getNewPassword());
        user.setPassword(encoded);
        userService.save(user);
        passwordHistoryService.savePasswordToHistory(user, encoded);

        return ResponseEntity.ok("Password changed successfully");
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.replace("Bearer ", "");
        }
        return null;
    }
}
