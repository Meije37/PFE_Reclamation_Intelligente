package com.pfe.backend.entity;

import com.pfe.backend.entity.enums.CanalOTP;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OTPVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idOtp;
    private String code;
    @Enumerated(EnumType.STRING)
    private CanalOTP canal;
    private LocalDateTime dateCreation = LocalDateTime.now();
    private LocalDateTime dateExpiration;
    private String cible; // Email ou téléphone
}
