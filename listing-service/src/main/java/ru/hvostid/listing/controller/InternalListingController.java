package ru.hvostid.listing.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.hvostid.listing.service.ListingService;

@Hidden
@RestController
@RequestMapping("/internal/listings")
public class InternalListingController {
    private final ListingService listingService;

    public InternalListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @GetMapping("/passports/{passportId}/has-active")
    public PassportActiveStatusResponse hasActiveListing(@PathVariable Long passportId) {
        return new PassportActiveStatusResponse(listingService.hasActiveListingForPassport(passportId));
    }

    public record PassportActiveStatusResponse(boolean hasActiveListing) {}
}
