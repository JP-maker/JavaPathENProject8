package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

public class TestRewardsService {

	// Ajout d'une instance de Logger pour la classe de test
	private static final Logger logger = LoggerFactory.getLogger(TestRewardsService.class);

	@Test
	public void userGetRewards() {
		logger.info("Test: userGetRewards - Démarrage");

		// Arrange: Configuration des services et des données de test
		logger.debug("Configuration: Initialisation des services GpsUtil et RewardCentral.");
		GpsUtil gpsUtil = new GpsUtil();
		RewardCentral rewardCentral = new RewardCentral();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		InternalTestHelper.setInternalUserNumber(0);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService, rewardCentral);

		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		Attraction attraction = gpsUtil.getAttractions().get(0);
		logger.debug("Configuration: Utilisateur '{}' et attraction '{}' créés.", user.getUserName(), attraction.attractionName);

		user.addToVisitedLocations(new VisitedLocation(user.getUserId(), attraction, new Date()));
		logger.debug("Action: Ajout d'une localisation visitée pour l'utilisateur à l'attraction.");

		// Act: Lancement de la méthode principale à tester
		logger.info("Action: Lancement du suivi de la localisation pour déclencher le calcul des récompenses.");
		tourGuideService.trackUserLocation(user);

		// Assert: Vérification des résultats
		List<UserReward> userRewards = user.getUserRewards();
		logger.info("Vérification: Nombre de récompenses obtenues = {}. Attendu = 1.", userRewards.size());

		tourGuideService.tracker.stopTracking();
		logger.debug("Nettoyage: Arrêt du tracker.");

		assertTrue(userRewards.size() == 1);
		logger.info("Test: userGetRewards - Terminé avec succès.");
	}

	@Test
	public void isWithinAttractionProximity() {
		logger.info("Test: isWithinAttractionProximity - Démarrage");

		// Arrange
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		Attraction attraction = gpsUtil.getAttractions().get(0);
		logger.debug("Configuration: Utilisation de l'attraction '{}'.", attraction.attractionName);

		// Act & Assert
		logger.info("Action: Vérification si une attraction est dans sa propre zone de proximité.");
		boolean isWithinProximity = rewardsService.isWithinAttractionProximity(attraction, attraction);

		assertTrue(isWithinProximity);
		logger.info("Test: isWithinAttractionProximity - Terminé avec succès.");
	}

	@Test
	public void nearAllAttractions() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		rewardsService.setProximityBuffer(Integer.MAX_VALUE);

		InternalTestHelper.setInternalUserNumber(1);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService, new RewardCentral());

		rewardsService.calculateRewards(tourGuideService.getAllUsers().get(0));
		List<UserReward> userRewards = tourGuideService.getUserRewards(tourGuideService.getAllUsers().get(0));
		tourGuideService.tracker.stopTracking();

		assertEquals(gpsUtil.getAttractions().size(), userRewards.size());
	}
}