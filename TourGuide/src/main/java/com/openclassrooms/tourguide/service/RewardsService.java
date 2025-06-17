package com.openclassrooms.tourguide.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

/**
 * Ce service gère l'attribution de récompenses aux utilisateurs.
 * Il calcule les récompenses en se basant sur la proximité entre les lieux visités par les utilisateurs
 * et les attractions touristiques. Il interagit avec {@link GpsUtil} pour les données de localisation
 * et {@link RewardCentral} pour obtenir les points de récompense.
 */
@Service
public class RewardsService {
	/**
	 * Constante pour la conversion des miles nautiques en miles terrestres.
	 */
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
	/**
	 * Tampon de proximité par défaut en miles.
	 */
    private int defaultProximityBuffer = 10;
	/**
	 * Le tampon de proximité actuel utilisé pour déterminer si un utilisateur est assez proche d'une attraction pour une récompense.
	 * Peut être modifié via {@link #setProximityBuffer(int)}.
	 */
	private int proximityBuffer = defaultProximityBuffer;
	/**
	 * La portée de proximité pour une attraction, en miles. Utilisée par {@link #isWithinAttractionProximity(Attraction, Location)}.
	 */
	private int attractionProximityRange = 200;
	/**
	 * Dépendance vers le service GpsUtil pour obtenir les informations de localisation (attractions, etc.).
	 */
	private final GpsUtil gpsUtil;
	/**
	 * Dépendance vers le service RewardCentral pour obtenir les points de récompense.
	 */
	private final RewardCentral rewardsCentral;

	/**
	 * Constructeur pour l'injection des dépendances {@link GpsUtil} et {@link RewardCentral}.
	 *
	 * @param gpsUtil        Une instance de GpsUtil.
	 * @param rewardCentral  Une instance de RewardCentral.
	 */
	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}

	/**
	 * Définit le tampon de proximité en miles.
	 * Ce tampon est utilisé pour déterminer si un utilisateur est assez proche d'une attraction pour recevoir une récompense.
	 *
	 * @param proximityBuffer La nouvelle valeur du tampon de proximité en miles.
	 */
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	/**
	 * Réinitialise le tampon de proximité à sa valeur par défaut.
	 */
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	/**
	 * Calcule et attribue les récompenses pour un utilisateur donné.
	 * Pour chaque lieu visité par l'utilisateur, cette méthode vérifie s'il se trouve à proximité
	 * d'une attraction pour laquelle il n'a pas encore reçu de récompense.
	 * Si c'est le cas, une nouvelle récompense est ajoutée à l'utilisateur.
	 *
	 * @param user L'utilisateur pour lequel les récompenses doivent être calculées.
	 */
	public void calculateRewards(User user) {
		List<VisitedLocation> userLocations = user.getVisitedLocations();
		List<Attraction> attractions = gpsUtil.getAttractions();

		// 1. Créez un Set contenant les noms de toutes les attractions déjà récompensées.
		Set<String> rewardedAttractionNames = user.getUserRewards().stream()
				.map(r -> r.attraction.attractionName)
				.collect(Collectors.toSet());

		for(VisitedLocation visitedLocation : userLocations) {
			for(Attraction attraction : attractions) {
				// 2. La vérification est maintenant beaucoup plus simple et rapide !
				//    Et elle n'itère plus sur la liste des récompenses de l'utilisateur.
				if (!rewardedAttractionNames.contains(attraction.attractionName)) {

					if(nearAttraction(visitedLocation, attraction)) {
						// 3. On peut maintenant ajouter la récompense sans risque
						UserReward newReward = new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user));
						user.addUserReward(newReward);

						// 4. IMPORTANT : On met à jour notre Set pour ne pas ajouter la même récompense plusieurs fois dans cette même exécution
						rewardedAttractionNames.add(attraction.attractionName);
					}
				}
			}
		}
	}

	/**
	 * Vérifie si un point de localisation se trouve dans le rayon de proximité d'une attraction.
	 * Utilise la variable {@code attractionProximityRange} pour cette vérification.
	 *
	 * @param attraction L'attraction de référence.
	 * @param location   La localisation à vérifier.
	 * @return true si la localisation est dans le rayon de proximité, false sinon.
	 */
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}

	/**
	 * Vérifie si un lieu visité est suffisamment proche d'une attraction pour déclencher une récompense.
	 * Cette vérification se base sur la valeur de {@code proximityBuffer}.
	 *
	 * @param visitedLocation Le lieu visité par l'utilisateur.
	 * @param attraction      L'attraction à vérifier.
	 * @return true si le lieu est assez proche, false sinon.
	 */
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}

	/**
	 * Récupère le nombre de points de récompense pour une attraction et un utilisateur donnés
	 * en interrogeant le service {@link RewardCentral}.
	 *
	 * @param attraction L'attraction pour laquelle les points sont demandés.
	 * @param user       L'utilisateur concerné.
	 * @return Le nombre de points de récompense.
	 */
	private int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

	/**
	 * Calcule la distance en miles terrestres (statute miles) entre deux points de localisation GPS.
	 *
	 * @param loc1 Le premier point de localisation.
	 * @param loc2 Le deuxième point de localisation.
	 * @return La distance entre les deux points en miles.
	 */
	public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
	}

}
