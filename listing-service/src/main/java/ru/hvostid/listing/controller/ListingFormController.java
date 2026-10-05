package ru.hvostid.listing.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import ru.hvostid.common.security.GatewayPreAuthentication;
import ru.hvostid.listing.dto.ListingFormDraft;
import ru.hvostid.listing.service.ListingFormService;

@RestController
@RequestMapping("/api/v1/listings/drafts/{formId}")
@PreAuthorize("hasRole(T(ru.hvostid.common.security.UserRole).SELLER.value())")
public class ListingFormController {
    private final ListingFormService forms;

    public ListingFormController(ListingFormService forms) {
        this.forms = forms;
    }

    @GetMapping
    public ResponseEntity<ListingFormDraft> get(@PathVariable UUID formId, @AuthenticationPrincipal UserDetails user) {
        return forms.get(GatewayPreAuthentication.currentUserId(user), formId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    public ListingFormDraft save(
            @PathVariable UUID formId,
            @Valid @RequestBody ListingFormDraft draft,
            @AuthenticationPrincipal UserDetails user) {
        return forms.save(GatewayPreAuthentication.currentUserId(user), formId, draft);
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(
            @PathVariable UUID formId,
            @RequestParam @jakarta.validation.constraints.Positive long version,
            @AuthenticationPrincipal UserDetails user) {
        forms.delete(GatewayPreAuthentication.currentUserId(user), formId, version);
        return ResponseEntity.noContent().build();
    }
}
