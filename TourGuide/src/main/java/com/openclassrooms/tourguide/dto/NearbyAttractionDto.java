package com.openclassrooms.tourguide.dto;

/**
 * DTO (Data Transfer Object) pour représenter une attraction proche d'un utilisateur.
 * Contient des informations sur l'attraction, la position de l'utilisateur,
 * la distance entre eux et les points de récompense.
 */
public class NearbyAttractionDto {

    private String attractionName;
    private double attractionLatitude;
    private double attractionLongitude;
    private double userLatitude;
    private double userLongitude;
    private double distanceInMiles;
    private int rewardPoints;

    public NearbyAttractionDto(String attractionName, double attractionLatitude, double attractionLongitude,
                               double userLatitude, double userLongitude, double distanceInMiles, int rewardPoints) {
        this.attractionName = attractionName;
        this.attractionLatitude = attractionLatitude;
        this.attractionLongitude = attractionLongitude;
        this.userLatitude = userLatitude;
        this.userLongitude = userLongitude;
        this.distanceInMiles = distanceInMiles;
        this.rewardPoints = rewardPoints;
    }

    public String getAttractionName() {
        return attractionName;
    }

    public void setAttractionName(String attractionName) {
        this.attractionName = attractionName;
    }

    public double getAttractionLatitude() {
        return attractionLatitude;
    }

    public void setAttractionLatitude(double attractionLatitude) {
        this.attractionLatitude = attractionLatitude;
    }

    public double getAttractionLongitude() {
        return attractionLongitude;
    }
    public void setAttractionLongitude(double attractionLongitude) {
        this.attractionLongitude = attractionLongitude;
    }

    public double getUserLatitude() {
        return userLatitude;
    }
    public void setUserLatitude(double userLatitude) {
        this.userLatitude = userLatitude;
    }

    public double getUserLongitude() {
        return userLongitude;
    }
    public void setUserLongitude(double userLongitude) {
        this.userLongitude = userLongitude;
    }

    public double getDistanceInMiles() {
        return distanceInMiles;
    }
    public void setDistanceInMiles(double distanceInMiles) {
        this.distanceInMiles = distanceInMiles;
    }

    public int getRewardPoints() {
        return rewardPoints;
    }
    public void setRewardPoints(int rewardPoints) {
        this.rewardPoints = rewardPoints;
    }
}