package com.pfe.backend.controller;

import com.pfe.backend.dto.LoginRequestDTO;
import com.pfe.backend.dto.LoginResponseDTO;
import com.pfe.backend.dto.RegisterRequestDTO;
import com.pfe.backend.dto.RegisterResponseDTO;
import com.pfe.backend.entity.Utilisateur;
import com.pfe.backend.repository.UtilisateurRepository;
import com.pfe.backend.service.JwtService;
import com.pfe.backend.service.UtilisateurService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UtilisateurService utilisateurService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UtilisateurRepository utilisateurRepository;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> registerCitizen(
            @Valid @RequestBody RegisterRequestDTO request) {

        RegisterResponseDTO response = utilisateurService.registerCitizen(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO request) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getMotDePasse()
                )
        );

        Utilisateur utilisateur = utilisateurRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        String token = jwtService.generateToken(utilisateur.getEmail());

        LoginResponseDTO response = new LoginResponseDTO(
                token,
                utilisateur.getEmail(),
                utilisateur.getRole().name()
        );

        return ResponseEntity.ok(response);
    }
}