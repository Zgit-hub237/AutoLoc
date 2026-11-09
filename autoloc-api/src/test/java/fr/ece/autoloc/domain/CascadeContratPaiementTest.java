package fr.ece.autoloc.domain;

import fr.ece.autoloc.repository.ContratRepository;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Observe Contrat -> Paiement cascade behaviour against the real autoloc_db database.
 * Each step runs in its own transaction so the generated SQL is visible in the console.
 */
@SpringBootTest
class CascadeContratPaiementTest {

	@Autowired
	private ContratRepository contratRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void cascadeAllEtOrphanRemoval() {
		System.out.println("===== 1. CASCADE PERSIST : un seul save(contrat) =====");
		Contrat contrat = new Contrat();
		contrat.setDateSignature(LocalDate.now());
		contrat.setMontantTotal(new BigDecimal("300.00"));
		contrat.setValide(true);
		contrat.ajouterPaiement(nouveauPaiement("100.00", ModePaiement.CARTE));
		contrat.ajouterPaiement(nouveauPaiement("200.00", ModePaiement.VIREMENT));
		Long idContrat = contratRepository.save(contrat).getIdContrat();
		assertThat(nombrePaiementsEnBase(idContrat)).isEqualTo(2);

		System.out.println("===== 2. ORPHAN REMOVAL : retirer un paiement de la collection =====");
		transactionTemplate.executeWithoutResult(status -> {
			Contrat charge = contratRepository.findById(idContrat).orElseThrow();
			charge.retirerPaiement(charge.getPaiements().get(0));
		});
		assertThat(nombrePaiementsEnBase(idContrat)).isEqualTo(1);

		System.out.println("===== 3. CASCADE REMOVE : supprimer le contrat =====");
		contratRepository.deleteById(idContrat);
		assertThat(contratRepository.existsById(idContrat)).isFalse();
		assertThat(nombrePaiementsEnBase(idContrat)).isZero();
	}

	@Test
	void chargementLazyOuEager() {
		Contrat contrat = new Contrat();
		contrat.setDateSignature(LocalDate.now());
		contrat.setMontantTotal(new BigDecimal("150.00"));
		contrat.setValide(true);
		contrat.ajouterPaiement(nouveauPaiement("150.00", ModePaiement.ESPECES));
		Long idContrat = contratRepository.save(contrat).getIdContrat();

		System.out.println("===== FETCH : findById(contrat) sans toucher aux paiements =====");
		Contrat charge = contratRepository.findById(idContrat).orElseThrow();
		System.out.println("===== Paiements deja charges apres findById ? "
				+ Hibernate.isInitialized(charge.getPaiements()) + " =====");

		contratRepository.deleteById(idContrat);
	}

	private Paiement nouveauPaiement(String montant, ModePaiement mode) {
		Paiement paiement = new Paiement();
		paiement.setMontant(new BigDecimal(montant));
		paiement.setDatePaiement(LocalDate.now());
		paiement.setModePaiement(mode);
		return paiement;
	}

	private int nombrePaiementsEnBase(Long idContrat) {
		return jdbcTemplate.queryForObject(
				"select count(*) from paiement where contrat_id_contrat = ?", Integer.class, idContrat);
	}
}
