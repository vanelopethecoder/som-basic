package com.masters.som.config.definition;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import com.masters.som.config.NodeMaestro;


import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;

public class NodeActor extends AbstractBehavior<NodeActor.Base> {

    public static final ServiceKey<Base> nodeServiceKey =
            ServiceKey.create(Base.class, "Node");

    private final ActorRef<Receptionist.Listing> listingResponseAdapter;
    private final ActorRef<NodeMaestro.Command> nodeMaestro;

    String myName;
    BigDecimal euclideanDistance;
    Boolean BMU;
    int x;
    int y;
    int iteration;
    int totalIterations;
    Double weight1;
    Double weight2;
    ParticleProperties particleProperties;

    public NodeActor(ActorContext<Base> context, String myName,
                     int xMax, int yMax, int totalIterations, ActorRef<NodeMaestro.Command> nodeMaestro, ParticleProperties particleProperties) {
        super(context);

        this.euclideanDistance = BigDecimal.ZERO;
        this.myName = myName;
        this.x = (int) (Math.random() * ((xMax) + 1));
        this.y = (int) (Math.random() * ((yMax) + 1));
        this.weight1 =  (Math.random() * ((5) + 1));
        this.weight2 = (Math.random() * ((5) + 1));
        this.particleProperties = particleProperties;

        this.totalIterations = totalIterations;
        this.iteration = 1;

        this.nodeMaestro = nodeMaestro;

        this.listingResponseAdapter =
                context.messageAdapter(Receptionist.Listing.class, ListingResponse::new);

    }

    public static Behavior<NodeActor.Base> create(String myName, ActorRef<NodeMaestro.Command> nodeMaestro,
                                                  int xmax, int ymax, int totalIterations, ParticleProperties particleProperties) {
        System.out.println("Hi there I am creating a typed Node");
        return Behaviors.withStash(
                100,
                stash ->
                        Behaviors.setup(
                                context -> {
                                    context
                                            .getSystem()
                                            .receptionist()
                                            .tell(Receptionist.register(nodeServiceKey, context.getSelf()));
                                    return new NodeActor(context, myName, xmax, ymax, totalIterations, nodeMaestro, particleProperties).behavior(context);
                                }
                        ));
    }

    private Behavior<Base> behavior(ActorContext<Base> context) {
        System.out.println("created");

        return Behaviors.receive(Base.class)
                .onMessage(ReceiveFeatures.class, msg -> {
                    msg.features.forEach((integer) -> System.out.println(integer + " " + " " + this.myName));


                    int euclideanDistance = 0;

                    if(msg.sq == true) {
                        euclideanDistance = (int) (Math.pow(msg.features.get(0) - this.x, 2) +
                                Math.pow(msg.features.get(1) - this.y, 2));
                    }
                    else {
                        euclideanDistance = (int) Math.sqrt((Math.pow(msg.features.get(0) - this.x, 2)) +
                                Math.pow(msg.features.get(1) - this.y, 2));
                    }

                    this.nodeMaestro.tell(new NodeMaestro.ReceiveAllDistance(euclideanDistance, myName, context.getSelf()));

                    return Behaviors.same();
                })
                .onMessage(AdjustWeights.class, msg -> {
                    System.out.println("time to adjust my weight " + this.weight1 + " " + this.myName);

                    StringBuilder details = msg.particleProperties.toStringBuilder(weight1+"", weight2+"");

                    this.weight1 = this.weight1 + (msg.theta) * (msg.currentIteration) * (msg.influence)*
                            (msg.inputVector.get(iteration).get(0) - this.weight1);

                    details.append(this.weight1);

                    this.weight2 = this.weight2 + (msg.theta) * (msg.currentIteration) * (msg.influence)*
                            (msg.inputVector.get(iteration).get(1) - this.weight2);

                    details.append(this.weight2);

                    System.out.println(" new  " + this.weight1 + " " + this.myName);

//                    writeToFile(details);
//                    if(msg.currentIteration == totalIterations) {
//                        writeUmatrixDeets(msg.particleProperties);
//                    }
                    this.nodeMaestro.tell(new NodeMaestro.AdjustedWeightComplete(true));
//
//                    particleProperties.setWeight1(particleProperties.getWeight1() + (theta) * (currentIteration) * (NodeMaestro.learning_rate)*
////                        (this.inputVectorMap.get(1) - particleProperties.getWeight1()));

                   return Behaviors.same();
                } ).onMessage(WriteFeaturesToFile.class, msg ->
                {
                    writeUmatrixDeets(msg.particleProperties, msg.iteration);
                    return Behaviors.same();
                })
                .build();
    }

    private void writeUmatrixDeets(ParticleProperties particleProperties, int iteration) throws IOException {
        FileWriter fileWriter = new FileWriter("uMatrixBefore"+ iteration +".txt", true);
        PrintWriter printWriter = new PrintWriter(fileWriter);
        printWriter.println("(" +particleProperties.x + "," + particleProperties.y + ")"
                + "#(" + this.weight1 + "," + this.weight2 + ")" );
        printWriter.close();

    }

    private void writeToFile(StringBuilder details) throws IOException {

        FileWriter fileWriter = new FileWriter("nodeDetails" +  ".txt", true);
        PrintWriter printWriter = new PrintWriter(fileWriter);
        printWriter.println(details);
        printWriter.close();

    }

    @Override
    public Receive createReceive() {
        return null;
    }

    public interface Base {
    }

    public static class ReceiveFeatures implements Base {
        public ArrayList<Integer> features;
        Boolean sq;

        public ReceiveFeatures(ArrayList<Integer> features, Boolean sq) {
            this.features = features;
            this.sq = sq;
        }
    }


    public static class WriteFeaturesToFile implements Base {
        Boolean sq;
        ParticleProperties particleProperties;
        int iteration;

        public WriteFeaturesToFile( Boolean sq, ParticleProperties particleProperties, int iteration) {
            this.sq = sq;
            this.particleProperties = particleProperties;
            this.iteration = iteration;
        }
    }

    public static class ListingResponse implements Base {
        final Receptionist.Listing listing;

        public ListingResponse(Receptionist.Listing listing) {
            this.listing = listing;
        }
    }

    public static class AdjustWeights implements Base {
        Double influence;
        Double theta;
        ParticleProperties particleProperties;// to update the particle properties
        int currentIteration; // think of a better to increment iteration
        HashMap<Integer, ArrayList<Integer>> inputVector;

        public AdjustWeights(Double influence, ParticleProperties particleProperties, int currentIteration, Double theta,
                             HashMap<Integer, ArrayList<Integer>> inputVector) {
            this.influence = influence;
            this.particleProperties = particleProperties;
            this.currentIteration = currentIteration;
            this.theta = theta;
            this.inputVector = inputVector;
        }
    }
}
