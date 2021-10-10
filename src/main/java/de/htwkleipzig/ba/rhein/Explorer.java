package de.htwkleipzig.ba.rhein;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Explorer implements Runnable {

    private final int dimension;
    private final byte start;

    public Explorer(int dimension, byte start) {
        this.dimension = dimension;
        this.start = start;
    }

    @Override
    public void run() {
        var trip = Main.QUEUE.poll();
        var killed = false;
        if (trip != null) {
            while (trip.size() < dimension && !killed) {
                var nextTargets = Arrays.stream(Main.Nodes).filter(n -> !trip.contains(n)).collect(Collectors.toList());
                var myNextTarget = nextTargets.remove(0);
                for (var otherTarget : nextTargets) {
                    var otherTrip = ((Trip) trip.clone());
                    otherTrip.add(otherTarget);
                    otherTrip.costs += currentCosts(otherTrip);
                    if (otherTrip.costs < Main.minimalCosts()) {
                        Main.QUEUE.put(otherTrip);
                        Main.ScheduledThreadCounter.incrementAndGet();
                        Main.scheduleExplorer(new Explorer(dimension, start));
                    }
                }
                trip.add(myNextTarget);
                trip.costs += currentCosts(trip);
                if (trip.costs > Main.minimalCosts()) {
                    killed = true;
                }
            }
            if(!killed){
                trip.add(start);
                trip.costs += currentCosts(trip);
                if (Main.minimalCosts() >= trip.costs) {
                    Main.addCompleteTrip(trip);
                }
            }
        }
        Main.ScheduledThreadCounter.decrementAndGet();
    }

    private static float currentCosts(Trip t) {
        assert t.size() >= 2;
        var s = t.size();
        return Main.distances[t.get(s - 2)][t.get(s - 1)];
    }
}