package ru.hvostid.listing.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hvostid.common.security.GatewayPreAuthentication;
import ru.hvostid.listing.dto.ListingDraftRequest;
import ru.hvostid.listing.service.ListingDraftService;

@RestController
@RequestMapping("/api/v1/listings/draft")
@PreAuthorize("hasRole(T(ru.hvostid.common.security.UserRole).SELLER.value())")
public class ListingDraftController {
    private final ListingDraftService draftService;

    public ListingDraftController(ListingDraftService draftService) {
        this.draftService = draftService;
    }

    @Operation(summary = "Restore the caller's unfinished listing creation form")
    @GetMapping
    public ResponseEntity<ListingDraftRequest> getDraft(@AuthenticationPrincipal UserDetails user) {
        return draftService
                .getDraft(GatewayPreAuthentication.currentUserId(user))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Save the caller's listing creation form before choosing a passport")
    @PutMapping
    public ResponseEntity<Void> saveDraft(
            @Valid @RequestBody ListingDraftRequest draft, @AuthenticationPrincipal UserDetails user) {
        draftService.saveDraft(GatewayPreAuthentication.currentUserId(user), draft);
        return ResponseEntity.noContent().build();
    }
}
