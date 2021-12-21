package com.masters.som.config;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import com.masters.som.config.definition.DataBasicActor;
import com.masters.som.config.definition.NodeActor;
import com.masters.som.config.definition.ParticleProperties;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class NodeMaestro {

    public static int xMax = 10;
    public static int yMax = 10;
    public static int threshold = 99;
    public static Double radius = 10.0;
    public static Double learning_rate = 0.1;


    private final ActorContext<Command> ctx;
    private final String name;

    public HashMap<Integer, ArrayList<Integer>> inputVectorMap;
    public Map<ActorRef<NodeActor.Base>, ParticleProperties> nodePathMaps = new HashMap<>();
    ActorRef<NodeActor.Base> bmu = null;
    int bmuEuclideanDistance=0;

    // this info will come from the main method
    int currentIteration;
    int totalIterations;
    int featureMapIndex;
    int iteration_roll ;

    public NodeMaestro(ActorContext<Command> ctx, String name, HashMap<Integer, ArrayList<Integer>> inputVector) {
        this.ctx = ctx;
        this.name = name;
        this.currentIteration = 0;
        this.totalIterations = 20;
        this.inputVectorMap = inputVector;
        this.featureMapIndex = 0;
    }

    public static Behavior<Command> create(String name, HashMap<Integer, ArrayList<Integer>> inputVector) {
        return Behaviors.setup(ctx -> new NodeMaestro(ctx, name, inputVector).spawnNodes(ctx));
    }

    private Behavior<Command> spawnNodes(ActorContext<Command> ctx) {

        HashMap<ActorRef<NodeActor.Base>, ParticleProperties> particlePropertiesMap1 = new HashMap<>();
        ParticleProperties[][] grid = new ParticleProperties[xMax][yMax];

        for (int i = 0; i < xMax; i++) {
            for (int j = 0; j < yMax; j++) {

                ParticleProperties properties = new ParticleProperties("node" + i + "" + j, i, j, 1,
                        this.totalIterations);

                String name = UUID.randomUUID().toString();

                ActorRef<NodeActor.Base> node = ctx.spawn(NodeActor.create("node" + i + "" + j + name,
                        ctx.getSelf(), i, j, this.totalIterations, properties), "node" + +i + "" + j+name);
                properties.setNodePath(node.path());
                properties.setMyName(name);

                this.nodePathMaps.put(node, properties);
            }
        }

        System.out.println("created all the nodes");
        return createDataActorAndRetrieveInputVector(ctx);
    }

    private Behavior<Command> createDataActorAndRetrieveInputVector(ActorContext<Command> ctx) {
        ActorRef<DataBasicActor.BaseDataCmd> dataBasicActorActorRef = ctx.spawn(DataBasicActor.create(
                "data", ctx.getSelf()), "data");
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
        this.currentIteration++;
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
        this.currentIteration++;
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
            featureMapIndex++;
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

                        // tell them all to print here, but maybe we only have to do it for the first iteration
                        this.nodePathMaps.forEach((baseActorRef, particleProperties) -> {
                                baseActorRef.tell(new NodeActor.WriteFeaturesToFile
                                        (true, particleProperties, this.currentIteration, true));
                            });
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
            this.bmuEuclideanDistance = this.nodePathMaps.get(this.bmu).getEuclideanDistance();
            // remember that the sq flag doesn't matter anymore

            if (!particleProperties.getBMU()) {
                baseActorRef.tell(new NodeActor.ReceiveFeatures(bmuVectorPoints, true));
            }
        });
        AtomicInteger atomicInteger = new AtomicInteger();
        // need to add a loop in here for the iterations - don't think this is relevant any longer

        return Behaviors.receive(Command.class)
                .onMessage(ReceiveAllDistance.class, msg ->
                {
                    atomicInteger.getAndIncrement();

                    this.nodePathMaps.get(msg.meRef).setBmuDistance_squared_Influence(msg.euclideanDistance);
                    checkIfNodeIsInfluenced(msg);

                    // what value should this be compared to?

                    if (atomicInteger.intValue() == NodeMaestro.threshold) {

                        System.out.println("received all the bmu distances :) ");
                        atomicInteger.set(0);
                        return calculateLearningRateAdJustNeighbourHoodWeights(context);
                    }
                    return Behaviors.same();
                }).build();
    }

    private Behavior<Command> calculateLearningRateAdJustNeighbourHoodWeights(ActorContext<Command> context) {

        this.nodePathMaps.forEach((baseActorRef, particleProperties) -> {

            if(particleProperties.getBMU()==true) {

                Double theta = Math.exp(-(double) (0) /
                        (2 * NodeMaestro.radius) * (currentIteration));

                ArrayList<Integer> inputVector = new ArrayList<>();
                inputVector.add(this.inputVectorMap.get(this.featureMapIndex).get(0));
                inputVector.add(this.inputVectorMap.get(this.featureMapIndex).get(1));
                particleProperties.setWeightVector();

                Double tempLearningRate = NodeMaestro.learning_rate * Math.exp(-this.currentIteration / ((totalIterations)*1.0));
                System.out.println("new weight " + particleProperties.getWeight1());



                FileWriter fileWriter = null;
                try {
                    fileWriter = new FileWriter("learningrate.txt", true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                assert fileWriter != null;
                PrintWriter printWriter = new PrintWriter(fileWriter);
                printWriter.println("[" + currentIteration + ", theta: " + theta  + ", learningRate:" + tempLearningRate + "]");
                printWriter.close();
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                baseActorRef.tell(new NodeActor.AdjustWeights(tempLearningRate, particleProperties, currentIteration, theta, inputVector));

            }
            if (particleProperties.getNeighbourhoodFlag()) {

                // this one looks correct

                Double theta = Math.exp(-(double) particleProperties.getBmuDistance_squared_Influence() /
                        (2 * NodeMaestro.radius) * (currentIteration));

                ArrayList<Integer> inputVector = new ArrayList<>();
                inputVector.add(this.inputVectorMap.get(this.featureMapIndex).get(0));
                inputVector.add(this.inputVectorMap.get(this.featureMapIndex).get(1));

                particleProperties.setWeightVector();
                Double tempLearningRate = NodeMaestro.learning_rate * Math.exp(-this.currentIteration / ((totalIterations)*1.0));


                FileWriter fileWriter = null;
                try {
                    fileWriter = new FileWriter("learningrate.txt", true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                PrintWriter printWriter = new PrintWriter(fileWriter);
                printWriter.println("[" + currentIteration + ", theta: " + theta  + ", learningRate:" + tempLearningRate + "]"
                        + particleProperties.getMyName());
                printWriter.close();
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                System.out.println("new weight " + particleProperties.getWeight1());
                baseActorRef.tell(new NodeActor.AdjustWeights(tempLearningRate, particleProperties, currentIteration, theta, inputVector));
            }
        });
        return receivedConfirmedWeightsADjusted(context);  //Behaviors.same(); // return a different behavior for when the nodes are done adjusting their weights, maybe you can turn this into an ask?
    }

    private Behavior<Command> receivedConfirmedWeightsADjusted(ActorContext<Command> context) {
        System.out.println("in received weight adjusted behavior");

        AtomicInteger atomicInteger = new AtomicInteger();
        AtomicInteger totalNeighbourhood = new AtomicInteger();

        this.nodePathMaps.forEach((baseActorRef, particleProperties) -> {
            if (particleProperties.getNeighbourhoodFlag()) {
                totalNeighbourhood.getAndIncrement();
            }
        });

        totalNeighbourhood.getAndIncrement(); // catering for the bmu

        return Behaviors.receive(Command.class)
                .onMessage(AdjustedWeightComplete.class, msg ->
                {
                    atomicInteger.getAndIncrement();

                    if (atomicInteger.get() == totalNeighbourhood.get()) {
                        System.out.println("the neighbourhood has been adjusted");
                        atomicInteger.set(0);

                        this.nodePathMaps.forEach((baseActorRef, particleProperties) -> {
                            if (particleProperties.getNeighbourhoodFlag()) {
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
                                baseActorRef.tell(new NodeActor.WriteFeaturesToFile(
                                        true, particleProperties, this.currentIteration, false));
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
                                baseActorRef.tell(new NodeActor.WriteFeaturesToFile(true, particleProperties, this.currentIteration, false));
                            });
                        }
                    }
                    return Behaviors.same();
                }).build();
    }

    private void checkIfNodeIsInfluenced(ReceiveAllDistance msg) {
//
//        double width = NodeMaestro.radius * NodeMaestro.radius;

        if (msg.euclideanDistance < NodeMaestro.radius) {
            this.nodePathMaps.get(msg.meRef).setNeighbourhoodFlag(true);
        }
    }

    private void calculateSizeOfRadius() throws IOException {

        if (currentIteration != 1) {
            SplittableRandom random = new SplittableRandom();
            int dice_roll = random.nextInt(1, 8);

            if (dice_roll == 7) {
                NodeMaestro.radius = NodeMaestro.radius - 1.0;
                this.iteration_roll = 0;
            } else if ((dice_roll + iteration_roll) >= 7) {
                NodeMaestro.radius = NodeMaestro.radius - 1.0;
                this.iteration_roll =  Math.abs(dice_roll + iteration_roll) - 7;
            } else {
                this.iteration_roll = this.iteration_roll + dice_roll;
            }
        }

        // write to file, the size of the radius? and the iteration?

        FileWriter fileWriter = new FileWriter("Radius.txt", true);
        PrintWriter printWriter = new PrintWriter(fileWriter);

        printWriter.println(this.currentIteration+"," + NodeMaestro.radius );
        printWriter.close();
        fileWriter.close();
    }

    private void calculateBMU() {

        // check for a better search alogorithm here

        AtomicInteger lowest = new AtomicInteger();
        lowest.set(1000);

        // how to include the condition in the foreach? understanding the ternary operator
        // what happen if there is a tie
        // what exactly is the atomic variable

        AtomicReference<ActorRef<NodeActor.Base>> bmuRef = new AtomicReference<>();

        bmuRef = findLowestDistanceReference(lowest, bmuRef);

        bmuRef = randomizeBMURef(lowest, bmuRef);
        setPreviousBMUToFalse();

        System.out.println("the lowest value is: " + lowest);
        this.nodePathMaps.get(bmuRef.get()).setBMU(true);
        this.bmu = bmuRef.get();
    }

    private AtomicReference<ActorRef<NodeActor.Base>> findLowestDistanceReference(AtomicInteger lowest, AtomicReference<ActorRef<NodeActor.Base>> bmuRef) {
        this.nodePathMaps.forEach((baseActorRef, particleProperties) -> {
            System.out.println(particleProperties.getEuclideanDistance() + " BMU euclidean distance");
            if (particleProperties.getEuclideanDistance() < lowest.get()) {
                lowest.set(particleProperties.getEuclideanDistance());
                bmuRef.set(baseActorRef);
            }
        });
        return bmuRef;
    }

    private void setPreviousBMUToFalse() {
        nodePathMaps.forEach((baseActorRef, particleProperties) -> {
            if (Objects.nonNull(particleProperties.getBMU())) {
                particleProperties.setBMU(false);
            }
        });
    }

    private AtomicReference<ActorRef<NodeActor.Base>> randomizeBMURef(AtomicInteger lowest, AtomicReference<ActorRef<NodeActor.Base>> bmuRef) {
        List<ActorRef> bmuref_possible = new ArrayList<>();

        nodePathMaps.forEach((baseActorRef, particleProperties) -> {
            if (particleProperties.getEuclideanDistance() == lowest.get()) {
                bmuref_possible.add(baseActorRef);
            }
        });

        Random random = new Random();
        int bmuref_index = random.nextInt(bmuref_possible.size() - 0);
        bmuRef.set(bmuref_possible.get(bmuref_index));
        return bmuRef;
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
