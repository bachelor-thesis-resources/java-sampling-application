package de.htwkleipzig.ba.rhein;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Main {

    public static Byte[] Nodes;

    public static AtomicInteger ScheduledThreadCounter = new AtomicInteger(0);

    public final static PriorityBlockingQueue<Trip> QUEUE = new PriorityBlockingQueue<>(10, (a, b) -> Integer.compare(b.size(), a.size()));

    private static ExecutorService service;

    public static float[][] distances;

    public static ReadWriteLock tripLock = new ReentrantReadWriteLock();

    public static int dimension;

    private static List<Trip> minimalTrips = new ArrayList<>();
    private static float minimalCosts = Float.POSITIVE_INFINITY;

    public static void addCompleteTrip(Trip t) {
        var wLock = tripLock.writeLock();
        try {
            wLock.lock();
            if (minimalTrips.isEmpty()) {
                minimalTrips.add(t);
                minimalCosts = t.costs;
            } else if (minimalTrips.get(0).costs > t.costs) {
                minimalTrips = new ArrayList<>();
                minimalTrips.add(t);
                minimalCosts = t.costs;
            } else if (minimalTrips.get(0).costs < t.costs) {
            } else {
                minimalTrips.add(t);
            }
        } finally {
            wLock.unlock();
        }
    }

    public static synchronized float minimalCosts() {
        var rLock = tripLock.readLock();
        try {
            rLock.lock();
            return minimalCosts;
        } finally {
            rLock.unlock();
        }
    }

    public static synchronized void scheduleExplorer(Explorer e) {
        service.execute(e);
    }

    public static synchronized Object getObject() {
        return null;
    }

    public final static float PI = 3.141592f;
    public final static float R = 6378.388f;

    public static float geo(float x) {
        int deg = (int) x;
        float min = x - deg;
        return PI * (deg + 5.0f * min / 3.0f) / 180.0f;
    }

    private static float getDistanceBetween(float[] a, float[] b) {
        assert a.length == 2;
        assert b.length == 2;
        var lat1 = geo(a[0]);
        var lat2 = geo(b[0]);
        var lon1 = geo(a[1]);
        var lon2 = geo(b[1]);

        var q1 = Math.cos(lon1 - lon2);
        var q2 = Math.cos(lat1 - lat2);
        var q3 = Math.cos(lat1 + lat2);

        return (float) Math.floor(R * Math.acos(0.5 * ((1.0 + q1) * q2 - (1.0 - q1) * q3)) + 1.0);
    }

    public static void parseGeoTspLib(String filepath) {
        try {
            File tspin = new File(filepath);
            BufferedReader buffR = new BufferedReader(new FileReader(tspin));

            buffR.readLine();
            buffR.readLine();
            buffR.readLine();

            dimension = Integer.parseInt(buffR.readLine().split("\\s")[1]);

            buffR.readLine();
            buffR.readLine();
            buffR.readLine();

            var it = buffR.lines().iterator();
            var i = 0;
            var coords = new float[dimension][2];
            while (it.hasNext()) {
                var line = it.next();
                if (!(line.isEmpty() || line.isBlank() || line.contains("EOF"))) {
                    var c = line.split("\\s");
                    coords[i][0] = Float.parseFloat(c[2]);
                    coords[i][1] = Float.parseFloat(c[3]);
                    i++;
                }
            }
            distances = new float[dimension][dimension];
            for (i = 0; i < dimension; i++) {
                for (var j = 0; j < dimension; j++) {
                    if (i == j) distances[i][j] = 0;
                    else distances[i][j] = getDistanceBetween(coords[i], coords[j]);
                }
            }
            buffR.close();
        } catch (IOException ex) {
            System.out.println(ex);
            return;
        }
    }

    public static void main(String[] args) {

        assert args.length == 2 : String.format("You must provide two arguments - first: TSP-Filepath, second: number of threads. You provided %d !", args.length);
        final String path = args[0];
        final int threadCount = Integer.parseInt(args[1]);
        System.out.println(String.format("Using File at %s\nUsing %d threads:", path, threadCount));

        final byte start = 0;

        service = Executors.newFixedThreadPool(threadCount);

        Instant from = Instant.now();

        parseGeoTspLib(path);

        Instant to = Instant.now();

        System.out.printf("Parsing Time: %s\n", Duration.between(from, to));

        from = Instant.now();

        Nodes = new Byte[dimension];
        Arrays.parallelSetAll(Nodes, e -> (byte) e);
        for (byte i = 1; i < dimension; i++) {
            var trip = new Trip(Arrays.asList(start, i));
            trip.costs = distances[start][i];
            QUEUE.put(trip);
        }
        to = Instant.now();
        System.out.printf("QUEUE initialized: %s\n", Duration.between(from, to));

        from = Instant.now();
        for (int i = 0; i < QUEUE.size(); i++) {
            ScheduledThreadCounter.incrementAndGet();
            service.execute(new Explorer(dimension, start));
        }
        var scheduledThreads = ScheduledThreadCounter.get();
        while (scheduledThreads != 0) {
            try {
                Thread.sleep(1250);
            } catch (InterruptedException ex) {
                System.out.println(ex);
            }
            scheduledThreads = ScheduledThreadCounter.get();
        }
        service.shutdown();
        to = Instant.now();
        System.out.printf("Computation: %s\n", Duration.between(from, to));
        System.out.printf("Costs: %.2f\n", minimalCosts);
        for (var t : minimalTrips) {
            System.out.println(t);
        }
    }
}