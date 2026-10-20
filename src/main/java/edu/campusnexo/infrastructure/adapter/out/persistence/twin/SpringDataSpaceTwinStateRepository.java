package edu.campusnexo.infrastructure.adapter.out.persistence.twin;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSpaceTwinStateRepository extends JpaRepository<SpaceTwinStateEntity, String> {
}
