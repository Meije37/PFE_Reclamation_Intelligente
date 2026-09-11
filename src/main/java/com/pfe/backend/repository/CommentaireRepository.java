package com.pfe.backend.repository;

import com.pfe.backend.entity.Commentaire;
import com.pfe.backend.entity.Reclamation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentaireRepository extends JpaRepository<Commentaire, Long> {

    List<Commentaire> findByReclamationOrderByDateCommentaireAsc(Reclamation reclamation);
}