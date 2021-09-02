package com.masters.som.config;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import com.masters.som.config.definition.DataBasicActor;
import com.masters.som.config.definition.NodeActor;
import com.masters.som.config.definition.ParticleProperties;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class NodeMaestro {

    public static int xMax = 10;
    public static int yMax = 10;
    public static int numberParticles = 25;
    public static int threshold = 80;
    public static Double radius = 50.0;
    public static Double learning_rate = 0.1;


    private final ActorContext<Command> ctx;
    private final String name;

    public HashMap<Integer, ArrayList<Integer>> inputVectorMap;
    public Map<ActorRef<NodeActor.Base>, ParticleProperties> nodePathMaps = new HashMap<>();
    ActorRef<NodeActor.Base> bmu = null;

    // this info will come from the main method
    int currentIteration;
    int totalIterations;
    int featureMapIndex;
    double neighbourhoodRadius;

    public NodeMaestro(ActorContext<Command> ctx, String name, HashMap<Integer, ArrayList<Integer>> inputVector) {
        this.ctx = ctx;
        this.name = name;
        this.currentIteration = 0;
        this.totalIterations = 10;
        this.inputVectorMap = inputVector;
        this.featureMapIndex = 0;
        this.neighbourhoodRadius = 0;
    }

    public static Behavior<Command> create(String name, HashMap<Integer, ArrayList<Integer>> inputVector) {
        return Behaviors.setup(ctx -> new NodeMaestro(ctx, name, inputVector).spawnNodes(ctx));
    }

    private Behavior<Command> spawnNodes(ActorContext<Command> ctx) {

        HashMap<ActorRef<NodeActor.Base>, ParticleProperties> particlePropertiesMap1 = new HashMap<>();
       ParticleProperties [][] grid = new ParticleProperties[xMax][yMax];

        for (int i = 0; i < xMax; i++) {
            for (int j = 0; j < yMax; j++) {

                ParticleProperties properties = new ParticleProperties("node" + i+""+j, i, j, 1,
                                    this.totalIterations, (Math.random() * ((5) + 1)), (Math.random() * ((5) + 1)));
                ActorRef<NodeActor.Base> node = ctx.spawn(NodeActor.create("node" +i+""+j, ctx.getSelf(),
                                    i, j, this.totalIterations, properties), "node" + +i+""+j);
                            properties.setNodePath(node.path());

                           this.nodePathMaps.put(node, properties);
            }
        }

        System.out.println("created all the nodes");

        return createDataActorAndRetrieveInputVector(ctx);
    }

    private Behavior<Command> createDataActorAndRetrieveInputVector(ActorContext<Command> ctx) {
        ActorRef<DataBasicActor.BaseDataCmd> dataBasicActorActorRef = ctx.spawn(DataBasicActor.create(
                "data", ctx.getSelf()), "data");

        final Duration timeout = Duration.ofSeconds(3);

        // tell that you need the data
        dataBasicActorActorRef.tell(DataBasicActor.Receive.INSTANCE);

        return sendFeaturesToNodes(ctx);
    }

    private Behavior<Command> sendFeaturesToNodes(ActorContext<Command> ctx) {

        return Behaviors.receive(Command.class)
                .onMessage(ReceiveData.class, msg -> {
                    return onlySendFeaturesToNodesAfterFirstIteration(ctx, msg);
                }).build();
    }

    private Behavior<Command> onlySendFeaturesToNodesAfterFirstIteration(ActorContext<Command> ctx, ReceiveData msg) {
        this.currentIteration ++;
        this.inputVectorMap = msg.features;
        System.out.println("this is iteration " + currentIteration);
        confirmFeatureMapIndex();
        // send to all of the nodes
        this.nodePathMaps.forEach((baseActorRef, particleProperties) ->
                {
                    baseActorRef.tell(new NodeActor.ReceiveFeatures(this.inputVectorMap.get(featureMapIndex), false));
                }
        );
        return distancesReceived(ctx);
    }

    private Behavior<Command> onlySendFeaturesToNodesAfterFirstIteration(ActorContext<Command> ctx) {
        confirmFeatureMapIndex();
        // send to all of the nodes
        this.currentIteration ++;
        System.out.println("this is iteration " + currentIteration);
        this.nodePathMaps.forEach((baseActorRef, particleProperties) ->
                {

                    baseActorRef.tell(new NodeActor.ReceiveFeatures(this.inputVectorMap.get(featureMapIndex), false));
                }
        );
        return distancesReceived(ctx);
    }

    private void confirmFeatureMapIndex() {
        if (featureMapIndex < this.inputVectorMap.size()) {
            featureMapIndex ++;
        } else {
            featureMapIndex = 1;
        }
    }

    private Behavior<Command> distancesReceived(ActorContext<Command> context) {

        AtomicInteger count = new AtomicInteger();

        return Behaviors.receive(Command.class)
                .onMessage(ReceiveAllDistance.class, msg ->
                {
                    count.getAndIncrement();
                    this.nodePathMaps.get(msg.meRef).setEuclideanDistance(msg.euclideanDistance);

                    if (count.intValue() == this.nodePathMaps.size()) {
                        // return behavior for next step to find the BMU
                        System.out.println("received all the distances :) ");
                        calculateBMU();
                        // you can look at putting the properties into the yml for spring
                        calculateSizeOfRadius();
                        count.set(0);
                        return findNeighboursBehavior(context);
                    }
                    return Behaviors.same();
                }).build();
    }

    private Behavior<Command> findNeighboursBehavior(ActorContext<Command> context) {

        this.nodePathMaps.forEach((baseActorRef, particleProperties) -> {

            ArrayList<Integer> bmuVectorPoints = new ArrayList();
            bmuVectorPoints.add(this.nodePathMaps.get(bmu).getX());
            bmuVectorPoints.add(this.nodePathMaps.get(bmu).getY());

            if (Objects.nonNull(particleProperties.getBMU()) != true) {
                baseActorRef.tell(new NodeActor.ReceiveFeatures(bmuVectorPoints, true));
            }
            });
        AtomicInteger atomicInteger = new AtomicInteger();

        // need to add a loop in here for the iterations

        return Behaviors.receive(Command.class)
                .onMessage(ReceiveAllDistance.class, msg ->
                {
                    atomicInteger.getAndIncrement();

                    // this.nodePathMaps.get(msg.meRef).setEuclideanDistance(msg.euclideanDistance);
                    // add the check here, does the node fall within the radius
                    // you have the distances here otoke???

                    // setting the distance to BMU here
                    this.nodePathMaps.get(msg.meRef).setBmuDistance_squared_Influence(msg.euclideanDistance);
                    checkIfNodeIsInfluenced(msg);

                    if (atomicInteger.intValue() == NodeMaestro.threshold ) {
                        // return behavior for next step to find the BMU
                        System.out.println("received all the bmu distances :) ");
                        atomicInteger.set(0);
                        return calculateLearningRateAdJustNeighbourHoodWeights(context);
                    }
                   return Behaviors.same();
                }).build();

    }

    private Behavior<Command> calculateLearningRateAdJustNeighbourHoodWeights(ActorContext<Command> context) {

        //calculate by how much its weights are adjusted
        // what is needed to adjust weights?
        // should the nodes be adjusting their own weights? -> probably, since we need to use the actor message passing

        this.nodePathMaps.forEach((baseActorRef, particleProperties) -> {

            if (particleProperties.getNeighbourhoodFlag() == true) {

                Double theta = Math.exp(-(double) particleProperties.getBmuDistance_squared_Influence() /
                        (2 * NodeMaestro.radius * NodeMaestro.radius) * (currentIteration));

                particleProperties.setWeightVector();
                NodeMaestro.learning_rate = NodeMaestro.learning_rate * Math.exp(-this.currentIteration / totalIterations);
                System.out.println("new weight " + particleProperties.getWeight1());
                baseActorRef.tell(new NodeActor.AdjustWeights(NodeMaestro.learning_rate, particleProperties, currentIteration, theta, this.inputVectorMap));

            }
        });
        return receivedConfirmedWeightsADjusted(context);  //Behaviors.same(); // return a different behavior for when the nodes are done adjusting their weights, maybe you can turn this into an ask?
    }

    private Behavior<Command> receivedConfirmedWeightsADjusted(ActorContext<Command> context) {

        System.out.println("in received weight adjusted behavior");

        AtomicInteger atomicInteger = new AtomicInteger();

        AtomicInteger totalNeighbourhood = new AtomicInteger();

        this.nodePathMaps.forEach((baseActorRef, particleProperties) -> {
            if (particleProperties.getNeighbourhoodFlag()){
                totalNeighbourhood.getAndIncrement();
            }
        });

     return Behaviors.receive(Command.class)
                .onMessage(AdjustedWeightComplete.class, msg ->
                {
                atomicInteger.getAndIncrement();

                if(atomicInteger.get() == totalNeighbourhood.get()) {
                    System.out.println("the neighbourhood has been adjusted");
                    atomicInteger.set(0);

                    this.nodePathMaps.forEach((baseActorRef, particleProperties) -> {
                        if (particleProperties.getNeighbourhoodFlag()){
                            particleProperties.setNeighbourhoodFlag(false);
                        }
                    });

                    if (this.currentIteration < this.totalIterations) {

                        FileWriter fileWriter = new FileWriter("uMatrixBefore" + currentIteration + ".txt", true);
                        PrintWriter printWriter = new PrintWriter(fileWriter);
                        printWriter.println("[" + NodeMaestro.xMax + "," + NodeMaestro.yMax + "]");
                        printWriter.close();
                        fileWriter.close();

                        this.nodePathMaps.forEach((baseActorRef, particleProperties) -> {
                            baseActorRef.tell(new NodeActor.WriteFeaturesToFile(true, particleProperties, this.currentIteration));
                        });

                        return onlySendFeaturesToNodesAfterFirstIteration(context);

                    } else {
                        System.out.println("we're done for now");

                        FileWriter fileWriter = new FileWriter("uMatrixBefore.txt", true);
                        PrintWriter printWriter = new PrintWriter(fileWriter);
                        printWriter.println("[" + NodeMaestro.xMax + "," + NodeMaestro.yMax + "]");
                        printWriter.close();
                        fileWriter.close();

                        this.nodePathMaps.forEach((baseActorRef, particleProperties) -> {
                            baseActorRef.tell(new NodeActor.WriteFeaturesToFile(true, particleProperties, this.currentIteration));
                        });
                    }
                }
                return Behaviors.same();
                }).build();
    }

    private void checkIfNodeIsInfluenced(ReceiveAllDistance msg) {

        double width = NodeMaestro.radius * NodeMaestro.radius;

        if (msg.euclideanDistance < width) {
            this.nodePathMaps.get(msg.meRef).setNeighbourhoodFlag(true);
        }
    }

    private void calculateSizeOfRadius() {

        if (currentIteration != 1) {
            int m_dMapRadius = Math.max(NodeMaestro.xMax, NodeMaestro.yMax) / 2;

            double m_dTimeConstant = this.totalIterations / Math.log(m_dMapRadius);

            // double check if this is correct
            double m_dNeighbourhoodRadius = m_dMapRadius * Math.exp(-this.currentIteration / m_dTimeConstant);

            System.out.println(m_dNeighbourhoodRadius);
           // NodeMaestro.radius = m_dNeighbourhoodRadius;
            NodeMaestro.radius = NodeMaestro.radius - m_dNeighbourhoodRadius;
        }
    }

    private void calculateBMU() {

        // check for a better search alogorithm here

        AtomicInteger lowest = new AtomicInteger();
        lowest.set(1000);

        // how to include the condition in the foreach? understanding the ternary operator
        // what happen if there is a tie
        // what exactly is the atomic variable

        AtomicReference<ActorRef<NodeActor.Base>> bmuRef = new AtomicReference<>();

        this.nodePathMaps.forEach((baseActorRef, particleProperties) -> {
            System.out.println(particleProperties.getEuclideanDistance() + " BMU euclidean distance");
            if (particleProperties.getEuclideanDistance() < lowest.get()) {
                lowest.set(particleProperties.getEuclideanDistance());
                bmuRef.set(baseActorRef);
            }
        });

        // resetting the previous bmu to false
        nodePathMaps.forEach((baseActorRef, particleProperties) -> {
            if (Objects.nonNull(particleProperties.getBMU())) {
                particleProperties.setBMU(false);
            }
        });

        System.out.println("the lowest value is: " + lowest);
        this.nodePathMaps.get(bmuRef.get()).setBMU(true);
        this.bmu = bmuRef.get();
    }


    enum CreateNodes implements Command {
        INSTANCE
    }

    public interface Command {
    }

    public static class ReceiveData implements Command {
        public HashMap<Integer, ArrayList<Integer>> features;

        public ReceiveData(HashMap<Integer, ArrayList<Integer>> features) {
            this.features = features;
        }
    }

    public static class AdjustedWeightComplete implements Command {
        Boolean done;

        public AdjustedWeightComplete(Boolean done) {
            this.done = done;
        }
    }

    public static class ReceiveAllDistance implements Command {
        int euclideanDistance;
        String myName;
        ActorRef<NodeActor.Base> meRef;

        public ReceiveAllDistance(int euclideanDistance, String myName, ActorRef<NodeActor.Base> meRef) {
            this.euclideanDistance = euclideanDistance;
            this.myName = myName;
            this.meRef = meRef;
        }
    }
}
