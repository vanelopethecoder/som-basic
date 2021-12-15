package com.masters.som.config.definition;

import akka.actor.ActorPath;

import java.util.HashMap;
import java.util.Map;

public class ParticleProperties {

    // look at using lombok

    String myName;
    int euclideanDistance;
    int bmuDistance_squared_Influence;
    Boolean BMU;
    int x;
    int y;
    Map<Integer, Double> weightVector;
    Double weight1;
    Double weight2;
    int iteration;
    int totalIterations;
    ActorPath nodePath;
    Boolean neighbourhoodFlag;

    public ParticleProperties(String myName, int x, int y, int iteration, int totalIterations) {
        this.myName = myName;
        this.x = x;
        this.y = y;
        this.iteration = iteration;
        this.totalIterations = totalIterations;
        this.neighbourhoodFlag = false;
        this.BMU = false;
    }

    public void setWeightVector() {
        this.weightVector = new HashMap<>();
        weightVector.put(0, this.weight1);
        weightVector.put(1, this.weight2);
    }

    public Map<Integer, Double> getWeightVector() {
        return weightVector;
    }

    public void setWeight1(Double weight1) {
        this.weight1 = weight1;
    }


    public void setWeight2(Double weight2) {
        this.weight2 = weight2;
    }

    public Double getWeight1() {
        return weight1;
    }

    public Double getWeight2() {
        return weight2;
    }

    public Boolean getNeighbourhoodFlag() {
        return neighbourhoodFlag;
    }

    public void setNeighbourhoodFlag(Boolean neighbourhoodFlag) {
        this.neighbourhoodFlag = neighbourhoodFlag;
    }

    public int getBmuDistance_squared_Influence() {
        return bmuDistance_squared_Influence;
    }

    public void setBmuDistance_squared_Influence(int bmuDistance_squared_Influence) {
        this.bmuDistance_squared_Influence = bmuDistance_squared_Influence;
    }

    public void setNodePath(ActorPath nodePath) {
        this.nodePath = nodePath;
    }

    public String getMyName() {
        return myName;
    }

    public int getEuclideanDistance() {
        return euclideanDistance;
    }

    public Boolean getBMU() {
        return BMU;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getIteration() {
        return iteration;
    }

    public int getTotalIterations() {
        return totalIterations;
    }

    public ActorPath getNodePath() {
        return nodePath;
    }

    public void setMyName(String myName) {
        this.myName = myName;
    }

    public void setEuclideanDistance(int euclideanDistance) {
        this.euclideanDistance = euclideanDistance;
    }

    public void setBMU(Boolean BMU) {
        this.BMU = BMU;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public void setTotalIterations(int totalIterations) {
        this.totalIterations = totalIterations;
    }

    public StringBuilder toStringBuilder(String ammend, String weight2) {
        return new StringBuilder( "ParticleProperties{" +
                "myName='" + myName + '\'' +
                ", euclideanDistance=" + euclideanDistance +
                ", bmuDistance_squared_Influence=" + bmuDistance_squared_Influence +
                ", BMU=" + BMU +
                ", x=" + x +
                ", y=" + y +
                ", neighbourhoodFlag=" + neighbourhoodFlag +
                "weight1 " + ammend+
                "weight2 " + weight2 + ""+'}');
    }
}
