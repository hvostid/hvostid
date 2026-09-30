package ru.hvostid.auth.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.hvostid.auth.dto.*;
import ru.hvostid.auth.service.AccountLifecycleService;
import ru.hvostid.auth.service.AuthService;
import ru.hvostid.common.security.GatewayPreAuthentication;

@RestController
@RequestMapping("/api/v1/auth")
public class AccountLifecycleController {
    private final AccountLifecycleService accounts;
    private final AuthService auth;

    public AccountLifecycleController(AccountLifecycleService accounts, AuthService auth) {
        this.accounts = accounts;
        this.auth = auth;
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestReset(@Valid @RequestBody PasswordResetRequest request) {
        accounts.requestPasswordReset(request.email());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        accounts.resetPassword(request.token(), request.password());
    }

    @PostMapping("/email-verification/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestVerification(@AuthenticationPrincipal UserDetails user) {
        accounts.requestEmailVerification(GatewayPreAuthentication.currentUserId(user));
    }

    @PostMapping("/email-verification/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody AccountTokenRequest request) {
        accounts.verifyEmail(request.token());
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@AuthenticationPrincipal UserDetails user) {
        auth.logoutAll(GatewayPreAuthentication.currentUserId(user));
    }

    @GetMapping("/sessions")
    public List<SessionResponse> sessions(@AuthenticationPrincipal UserDetails user) {
        return auth.sessions(GatewayPreAuthentication.currentUserId(user));
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal UserDetails user, @PathVariable Long id) {
        auth.revokeSession(GatewayPreAuthentication.currentUserId(user), id);
    }
}
