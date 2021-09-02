package com.masters.som.config;

import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;

public class TempMainMethod {

    private final ActorContext<NotUsed> context;

    public TempMainMethod( ActorContext<NotUsed> context1) {
        context = context1;
    }

    public static void main(String [] args) {
        ActorSystem.create(TempMainMethod.create(), "ParticlesWithStates");
    }

    private static Behavior<NotUsed> create() {
    return Behaviors.setup(context -> new TempMainMethod(context).behavior());
    }

    private Behavior<NotUsed> behavior() {
        ActorRef<NodeMaestro.Command> stateController = context.spawn(NodeMaestro.create("ControlFreak", null),
                "ControlFreak");

        return Behaviors.empty();
    }
}
