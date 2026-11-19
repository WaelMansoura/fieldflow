package com.fieldflow.persistance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TechnicienRepository extends JpaRepository<Technicien, String> {

    List<Technicien> findAllByOrderByNomAsc();
}
