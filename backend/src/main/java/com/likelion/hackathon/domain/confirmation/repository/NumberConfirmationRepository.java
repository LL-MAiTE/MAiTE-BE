package com.likelion.hackathon.domain.confirmation.repository;

import com.likelion.hackathon.domain.confirmation.entity.NumberConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NumberConfirmationRepository extends JpaRepository<NumberConfirmation, UUID> {}
