package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.dto.NearbyAttractionDto;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import rewardCentral.RewardCentral;
import tripPricer.Provider;
import tripPricer.TripPricer;

@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardCentral;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;


	// Ajout de notre propre ExecutorService pour les tâches de TourGuide
	private final ExecutorService executorService = Executors.newFixedThreadPool(100);

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService, RewardCentral rewardCentral) {

		this.gpsUtil = gpsUtil;
		this.rewardCentral = rewardCentral;
		this.rewardsService = rewardsService;
		
		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
		// Si l'utilisateur n'a pas de localisation, on en traque une.
		// La méthode trackUserLocation est maintenant asynchrone, donc on attend le résultat ici.
		return (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
				: trackUserLocation(user).join(); // .join() attend la fin du CompletableFuture
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	/**
	 * Traque la localisation d'un utilisateur de manière asynchrone.
	 * Cette méthode retourne un CompletableFuture qui contiendra la VisitedLocation une fois obtenue.
	 * Elle lance également le calcul des récompenses en parallèle.
	 *
	 * @param user l'utilisateur à traquer.
	 * @return un CompletableFuture contenant la VisitedLocation.
	 */
	public CompletableFuture<VisitedLocation> trackUserLocation(User user) {
		return CompletableFuture.supplyAsync(() -> {
					// 1. Obtenir la localisation (appel potentiellement long)
					return gpsUtil.getUserLocation(user.getUserId());
				}, executorService).thenCompose(
						visitedLocation -> {
							// Une fois la localisation obtenue, on l'ajoute et on lance les récompenses
							user.addToVisitedLocations(visitedLocation);
							// On retourne le CompletableFuture du calcul des récompenses,
							// mais on le transforme pour qu'il retourne la VisitedLocation à la fin.
							return rewardsService.calculateRewards(user).thenApply(v -> visitedLocation);
						});
	}

	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
		List<Attraction> nearbyAttractions = new ArrayList<>();
		for (Attraction attraction : gpsUtil.getAttractions()) {
			if (rewardsService.isWithinAttractionProximity(attraction, visitedLocation.location)) {
				nearbyAttractions.add(attraction);
			}
		}

		return nearbyAttractions;
	}

	/**
	 * Nouvelle méthode pour traquer tous les utilisateurs en parallèle.
	 * C'est cette méthode que le Tracker devrait appeler.
	 */
	public void trackAllUsers() {
		List<User> users = getAllUsers();
		// On crée une liste de toutes les tâches asynchrones
		List<CompletableFuture<VisitedLocation>> futures = users.stream()
				.map(this::trackUserLocation) // this::trackUserLocation est équivalent à user -> trackUserLocation(user)
				.collect(Collectors.toList());

		// On attend que toutes les tâches soient terminées.
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
	}

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
				// Important : arrêter notre ExecutorService aussi !
				executorService.shutdown();
				rewardsService.shutdown();
			}
		});
	}

	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}


	/**
	 * Calcule les cinq attractions touristiques les plus proches de la position d'un utilisateur.
	 * Pour chaque attraction, des informations détaillées sont retournées, incluant la distance
	 * et les points de récompense.
	 *
	 * @param visitedLocation La dernière localisation connue de l'utilisateur.
	 * @param user L'utilisateur pour qui les récompenses sont calculées.
	 * @return Une liste de 5 objets DTO contenant les informations des attractions les plus proches.
	 */
	public List<NearbyAttractionDto> getFiveNearbyAttractions(VisitedLocation visitedLocation, User user) {
		List<Attraction> allAttractions = gpsUtil.getAttractions();
		final Location userLocation = visitedLocation.location;

		List<NearbyAttractionDto> nearbyAttractions = allAttractions.stream()
				// 1. Créez un objet temporaire contenant l'attraction et sa distance à l'utilisateur.
				.map(attraction -> new Object() {
					final Attraction attr = attraction;
					final double distance = rewardsService.getDistance(attraction, userLocation);
				})
				// 2. Triez ces objets par distance croissante.
				.sorted(Comparator.comparing(o -> o.distance))
				// 3. Gardez seulement les 5 plus proches.
				.limit(5)
				// 4. Transformez ces 5 objets en notre DTO final.
				.map(o -> new NearbyAttractionDto(
						o.attr.attractionName,
						o.attr.latitude,
						o.attr.longitude,
						userLocation.latitude,
						userLocation.longitude,
						o.distance,
						rewardCentral.getAttractionRewardPoints(o.attr.attractionId, user.getUserId())
				))
				// 5. Collectez les résultats dans une liste.
				.collect(Collectors.toList());

		return nearbyAttractions;
	}
}
