package com.masters.som.config.definition;

import akka.actor.ActorPath;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import com.masters.som.config.NodeMaestro;
import scala.collection.immutable.List;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DataBasicActor extends AbstractBehavior<DataBasicActor.BaseDataCmd> {

    public static final ServiceKey<DataBasicActor.BaseDataCmd> dataServiceKey =
            ServiceKey.create(BaseDataCmd.class, "Data");

    private final ActorRef<Receptionist.Listing> listingResponseAdapter;
    private final ActorRef<NodeMaestro.Command> nodeMaestro;

    public interface BaseDataCmd {
    }

    public enum Receive implements BaseDataCmd {
        INSTANCE
    }

    enum Adjust implements BaseDataCmd {
        INSTANCE
    }

    HashMap<Integer, ArrayList<Integer>> features;
    String myName;

    public DataBasicActor(ActorContext<BaseDataCmd> context, ActorRef<NodeMaestro.Command> nodeMaestro, String myName) throws IOException {
        super(context);
        this.nodeMaestro = nodeMaestro;

        this.myName = myName;

        // use input values from within the application
//        this.initiatliseInputVectorMap();
        readInputDataFromFile();



        this.listingResponseAdapter =
                context.messageAdapter(Receptionist.Listing.class, ListingResponse::new);

    }

    void initiatliseInputVectorMap() throws IOException {
        ArrayList<Integer> one = new ArrayList();
        one.add(3);
        one.add(4);

        ArrayList<Integer> two = new ArrayList();
        two.add(56);
        two.add(63);

        ArrayList<Integer> three = new ArrayList();
        three.add(75);
        three.add(42);

        ArrayList<Integer> four = new ArrayList();
        four.add(96);
        four.add(21);

        this.features = new HashMap();
        features.put(1, one);
        features.put(2, two);
        features.put(3, three);
        features.put(4, four);

        // Insert method to read values from file
        // each line will be a list
        // put each list into the feature map



        // write vector to file
        // in future... I actually just have the input data in a file in the first place... it would read from the file
        // and send to the node maestro

        FileWriter fileWriter = new FileWriter("inputData.txt", true);
        PrintWriter printWriter = new PrintWriter(fileWriter);
        //printWriter.println("[" + NodeMaestro.xMax + "," + NodeMaestro.yMax + "]");

        features.forEach((integer, integers) -> {
            printWriter.println(integers);
        });




        printWriter.close();
        fileWriter.close();


    }

    public void readInputDataFromFile() throws IOException {

        Path path = Paths.get("inputData.txt");

        BufferedReader reader = Files.newBufferedReader(path);

        this.features = new HashMap<>();

        AtomicInteger atomicInteger = new AtomicInteger();
        atomicInteger.getAndSet(1);

       reader.lines().forEach(s -> {
           ArrayList<Integer> ints = new ArrayList();
          String[] arr = s.split(",");
           arr[0] = arr[0].substring(1);
           arr[1] = arr[1].substring(0,arr[1].length()-1);
            ints.add(Integer.parseInt(arr[0].trim()));
            ints.add(Integer.parseInt(arr[1].trim()));
            this.features.put(atomicInteger.get(), ints);
            atomicInteger.getAndIncrement();
       });
    }

// when should we spawn the data actors??? when we need them? should the nodes spawn them as need be?
// maybe we'll go with the node maestro for now
    public static Behavior<DataBasicActor.BaseDataCmd> create(String myName, ActorRef<NodeMaestro.Command> nodeMaestro) {

        return Behaviors.withStash(
                100,
                stash ->
                        Behaviors.setup(
                                context -> {
                                    context
                                            .getSystem()
                                            .receptionist()
                                            .tell(Receptionist.register(dataServiceKey, context.getSelf()));
                                    return new DataBasicActor(context, nodeMaestro, myName).behavior(context); //.behavior(context);
                                }
                        ));
    }

    @Override
    public akka.actor.typed.javadsl.Receive<BaseDataCmd> createReceive() {
        return null;
    }


    public Behavior<DataBasicActor.BaseDataCmd> behavior(ActorContext<DataBasicActor.BaseDataCmd> context) {

    return Behaviors.receive(DataBasicActor.BaseDataCmd.class)
            .onMessage(Receive.class, msg -> {

                this.nodeMaestro.tell(new NodeMaestro.ReceiveData(this.features));
                return Behaviors.same();
            }).build();

    }

    public static class ListingResponse implements DataBasicActor.BaseDataCmd {
        final Receptionist.Listing listing;

        public ListingResponse(Receptionist.Listing listing) {
            this.listing = listing;
        }

    }
}
