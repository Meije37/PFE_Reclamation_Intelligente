package com.pfe.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/** Lancer : mvn test -Dtest=ProtectionConnexionServiceTest */
class ProtectionConnexionServiceTest {

    /** Horloge qu'on peut avancer à la main (pas besoin d'attendre 15 minutes). */
    static class HorlogeTest extends Clock {
        Instant maintenant = Instant.parse("2026-11-02T08:00:00Z");
        void avancer(Duration d) { maintenant = maintenant.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return maintenant; }
    }

    HorlogeTest horloge;
    ProtectionConnexionService protection;

    @BeforeEach
    void preparer() {
        horloge = new HorlogeTest();
        protection = new ProtectionConnexionService(5, 20, 5, 3, 10,
                Duration.ofMinutes(15), Duration.ofMinutes(15), horloge);
    }

    @Test
    void cinqMotsDePasseFauxBloquentLEmailQuinzeMinutes() {
        for (int i = 4; i >= 1; i--) {
            assertEquals(i, protection.echecConnexion("ali@chikayti.mr", "10.0.0.1"));
            assertEquals(0, protection.attenteConnexion("ali@chikayti.mr", "10.0.0.1"));
        }
        assertEquals(0, protection.echecConnexion("ali@chikayti.mr", "10.0.0.1"));   // 5e échec
        assertEquals(15 * 60, protection.attenteConnexion("ali@chikayti.mr", "10.0.0.1"));

        horloge.avancer(Duration.ofMinutes(15));
        assertEquals(0, protection.attenteConnexion("ali@chikayti.mr", "10.0.0.1"));
    }

    @Test
    void lEmailEstReconnuSansTenirCompteDesMajuscules() {
        for (int i = 0; i < 5; i++) protection.echecConnexion("Ali@Chikayti.mr ", "10.0.0.1");
        assertTrue(protection.attenteConnexion("ali@chikayti.mr", "10.0.0.2") > 0);
    }

    @Test
    void uneConnexionReussieRemetLeCompteurAZero() {
        for (int i = 0; i < 4; i++) protection.echecConnexion("ali@chikayti.mr", "10.0.0.1");
        protection.succesConnexion("ali@chikayti.mr");
        assertEquals(4, protection.echecConnexion("ali@chikayti.mr", "10.0.0.1"));
    }

    @Test
    void lesEchecsAnciensSortentDeLaFenetre() {
        for (int i = 0; i < 4; i++) protection.echecConnexion("ali@chikayti.mr", "10.0.0.1");
        horloge.avancer(Duration.ofMinutes(16));
        assertEquals(4, protection.echecConnexion("ali@chikayti.mr", "10.0.0.1"));
    }

    @Test
    void uneMemeIpQuiEssaieBeaucoupDeComptesEstBloquee() {
        for (int i = 0; i < 20; i++) protection.echecConnexion("compte" + i + "@chikayti.mr", "10.0.0.9");
        assertTrue(protection.attenteConnexion("nouveau@chikayti.mr", "10.0.0.9") > 0);
        assertEquals(0, protection.attenteConnexion("nouveau@chikayti.mr", "10.0.0.1"));
    }

    @Test
    void cinqCodesFauxBloquentLaReinitialisation() {
        for (int i = 0; i < 4; i++) assertTrue(protection.echecCode("ali@chikayti.mr") > 0);
        assertEquals(0, protection.echecCode("ali@chikayti.mr"));
        assertTrue(protection.attenteCode("ali@chikayti.mr") > 0);
    }

    @Test
    void troisDemandesDeCodeMaximumParQuartDHeure() {
        assertEquals(0, protection.demandeCode("ali@chikayti.mr", "10.0.0.1"));
        assertEquals(0, protection.demandeCode("ali@chikayti.mr", "10.0.0.1"));
        assertEquals(0, protection.demandeCode("ali@chikayti.mr", "10.0.0.1"));
        assertTrue(protection.demandeCode("ali@chikayti.mr", "10.0.0.1") > 0);
    }

    @Test
    void dureeLisible() {
        assertEquals("40 secondes", ProtectionConnexionService.duree(40));
        assertEquals("1 minute", ProtectionConnexionService.duree(60));
        assertEquals("15 minutes", ProtectionConnexionService.duree(900));
        assertEquals("2 minutes", ProtectionConnexionService.duree(61));
    }
}
