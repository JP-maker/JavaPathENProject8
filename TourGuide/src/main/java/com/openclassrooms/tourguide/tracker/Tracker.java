package com.openclassrooms.tourguide.tracker;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;

/**
 * Gère un thread en arrière-plan (background thread) dont le rôle est de déclencher
 * périodiquement la mise à jour de la localisation de tous les utilisateurs enregistrés.
 * <p>
 * Ce tracker s'exécute dans une boucle infinie jusqu'à ce qu'il soit explicitement arrêté
 * via la méthode {@link #stopTracking()}. Il utilise le {@link TourGuideService} pour
 * effectuer le suivi de la localisation.
 */
public class Tracker extends Thread {
	/**
	 * Logger pour cette classe.
	 */
	private final Logger logger = LoggerFactory.getLogger(Tracker.class);

	/**
	 * L'intervalle de temps en secondes entre chaque cycle complet de suivi de tous les utilisateurs.
	 * La valeur par défaut est de 5 minutes.
	 */
	private static final long trackingPollingInterval = TimeUnit.MINUTES.toSeconds(5);

	/**
	 * Gère l'exécution du tracker dans un thread unique et dédié.
	 * C'est une manière robuste de contrôler le cycle de vie du thread.
	 */
	private final ExecutorService executorService = Executors.newSingleThreadExecutor();

	/**
	 * Le service principal de l'application, utilisé pour récupérer la liste des utilisateurs
	 * et pour lancer le suivi de leur localisation.
	 */
	private final TourGuideService tourGuideService;

	/**
	 * Un drapeau (flag) volatile pour indiquer au thread de s'arrêter proprement.
	 */
	private boolean stop = false;

	/**
	 * Construit une nouvelle instance de Tracker.
	 * Au moment de la construction, le tracker soumet sa propre tâche (this) à l'ExecutorService,
	 * ce qui démarre immédiatement le processus de suivi en arrière-plan.
	 *
	 * @param tourGuideService le service TourGuide à utiliser pour le suivi.
	 */
	public Tracker(TourGuideService tourGuideService) {
		this.tourGuideService = tourGuideService;
		executorService.submit(this);
	}

	/**
	 * Arrête le processus de suivi.
	 * Cette méthode positionne le drapeau d'arrêt à `true` et demande l'arrêt immédiat
	 * de l'ExecutorService, ce qui interrompt le thread en cours d'exécution.
	 */
	public void stopTracking() {
		stop = true;
		executorService.shutdownNow();
	}

	/**
	 * La logique principale du thread de suivi, exécutée en boucle.
	 * <p>
	 * Le déroulement est le suivant :
	 * 1. Vérifie si le thread doit s'arrêter.
	 * 2. Récupère la liste de tous les utilisateurs.
	 * 3. Pour chaque utilisateur, appelle {@link TourGuideService#trackUserLocation(User)}.
	 * 4. Calcule et affiche le temps total pris pour le cycle.
	 * 5. Met le thread en pause pour la durée de {@code trackingPollingInterval}.
	 * 6. Recommence.
	 * <p>
	 * La boucle se termine si le thread est interrompu ou si la méthode {@link #stopTracking()} est appelée.
	 */
	@Override
	public void run() {
		StopWatch stopWatch = new StopWatch();
		while (true) {
			if (Thread.currentThread().isInterrupted() || stop) {
				logger.debug("Tracker stopping");
				break;
			}

			List<User> users = tourGuideService.getAllUsers();
			logger.debug("Begin Tracker. Tracking " + users.size() + " users.");
			stopWatch.start();

			users.forEach(tourGuideService::trackUserLocation);

			stopWatch.stop();
			logger.debug("Tracker Time Elapsed: " + TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime()) + " seconds.");
			stopWatch.reset();
			try {
				logger.debug("Tracker sleeping");
				TimeUnit.SECONDS.sleep(trackingPollingInterval);
			} catch (InterruptedException e) {
				// Si le thread est interrompu pendant son sommeil, on sort de la boucle.
				break;
			}
		}
	}
}