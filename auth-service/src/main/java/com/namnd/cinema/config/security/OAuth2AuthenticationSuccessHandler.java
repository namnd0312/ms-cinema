package com.namnd.cinema.config.security;

import com.namnd.cinema.model.RefreshToken;
import com.namnd.cinema.model.Role;
import com.namnd.cinema.model.User;
import com.namnd.cinema.service.JwtService;
import com.namnd.cinema.service.OAuth2UserLinkingService;
import com.namnd.cinema.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import java.io.IOException;
import java.util.List;

/**
 * Handles successful OAuth2 authentication.
 * Finds/creates user, links provider, generates JWT+refresh token,
 * then redirects to frontend SPA with tokens as query params.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final OAuth2UserLinkingService oAuth2UserLinkingService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Value("${namnd.app.oauth2CallbackUrl}")
    private String oauth2CallbackUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oauthToken.getPrincipal();

        String providerName = oauthToken.getAuthorizedClientRegistrationId(); // "google"
        String providerUserId = oAuth2User.getAttribute("sub");
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        Boolean emailVerified = oAuth2User.getAttribute("email_verified");

        if (email == null) {
            log.error("OAuth2 login failed: Google did not provide email");
            response.sendRedirect(oauth2CallbackUrl + "?error=no_email");
            return;
        }

        // Find or create + link user
        User user = oAuth2UserLinkingService.processOAuth2User(
            providerName, providerUserId, email, name,
            Boolean.TRUE.equals(emailVerified));

        // Generate tokens (same as normal login)
        List<String> roles = user.getRoles().stream()
            .map(Role::getName)
            .toList();
        String jwt = jwtService.generateTokenFromEmail(user.getEmail(), user.getId(), roles);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

        // Redirect to frontend with tokens as query params
        String redirectUrl = UriComponentsBuilder.fromUriString(oauth2CallbackUrl)
            .queryParam("token", jwt)
            .queryParam("refreshToken", refreshToken.getToken())
            .build().toUriString();

        log.info("OAuth2 login success: email={}, redirecting to frontend", email);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
