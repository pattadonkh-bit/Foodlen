package com.example.demo.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChildRepository extends JpaRepository<Child, Long> {

    List<Child> findAllByOrderByNameAsc();

    List<Child> findByOwner_IdOrderByNameAsc(Long ownerId);
}
