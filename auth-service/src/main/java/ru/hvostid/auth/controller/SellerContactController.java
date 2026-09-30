package ru.hvostid.auth.controller;

import org.springframework.web.bind.annotation.*;
import ru.hvostid.auth.dto.SellerContactResponse;
import ru.hvostid.auth.service.ProfileService;

@RestController
@RequestMapping("/api/v1/users")
public class SellerContactController {
    private final ProfileService profileService;

    public SellerContactController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/{id}/contact")
    public SellerContactResponse contact(@PathVariable Long id) {
        return profileService.getSellerContact(id);
    }
}
