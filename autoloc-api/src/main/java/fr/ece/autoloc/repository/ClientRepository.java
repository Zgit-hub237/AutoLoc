package fr.ece.autoloc.repository;

import fr.ece.autoloc.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {
}
