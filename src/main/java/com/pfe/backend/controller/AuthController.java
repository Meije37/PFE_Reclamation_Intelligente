package com.pfe.backend.controller;

import com.pfe.backend.dto.RegisterRequestDTO;
import com.pfe.backend.dto.RegisterResponseDTO;
import com.pfe.backend.service.UtilisateurService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UtilisateurService utilisateurService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> registerCitizen(
            @Valid @RequestBody RegisterRequestDTO request) {

        RegisterResponseDTO response = utilisateurService.registerCitizen(request);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}