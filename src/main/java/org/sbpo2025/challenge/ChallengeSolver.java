package org.sbpo2025.challenge;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;

import ilog.concert.IloException;

public class ChallengeSolver {
    private final long MAX_RUNTIME = 600000; // milliseconds; 10 minutes

    protected List<Map<Integer, Integer>> orders;
    protected List<Map<Integer, Integer>> aisles;
    protected int nItems;
    protected int waveSizeLB;
    protected int waveSizeUB;

    private final GreedyAlgorithm greedyAlgorithm;

    public ChallengeSolver(
            List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, int nItems, int waveSizeLB, int waveSizeUB) {
        this.orders = orders;
        this.aisles = aisles;
        this.nItems = nItems;
        this.waveSizeLB = waveSizeLB;
        this.waveSizeUB = waveSizeUB;

        greedyAlgorithm = new GreedyAlgorithm(orders, aisles, nItems, waveSizeLB, waveSizeUB);
    }

    public ChallengeSolution solve(StopWatch stopWatch) {
        int iteration = 0, maxIterations = 15;
        double bestQ = 0.0, q = 0.0, epsilon = 1e-4;

        // Solução inicial
        ChallengeSolution currentSolution = greedyAlgorithm.solve();

        //System.out.println("\nTempo decorrido: " + getElapsedTime(stopWatch) + " seg.");
        //System.out.println();

        ParametricSolver paramSolver = new ParametricSolver(orders, aisles, nItems, waveSizeLB, waveSizeUB);

        try {
            do {
                paramSolver.updateObjectiveFunction(orders, aisles, q);
                
                if (currentSolution != null && isSolutionFeasible(currentSolution)) {
                    paramSolver.setInitialSolution(currentSolution);
                }

                paramSolver.setTimeLimit(getRemainingTime(stopWatch));

                ChallengeSolution newSolution = paramSolver.solveModel();

                System.out.println();
                System.out.print("it: " + iteration + ", ");

                if (newSolution != null && isSolutionFeasible(newSolution)) {
                    double newQ = computeObjectiveFunction(newSolution);

                    System.out.println("q: " + newQ);

                    if (newQ > bestQ) {
                        bestQ = newQ;
                        currentSolution = newSolution;
                    }

                    int totalUnitsPicked = 0;
                    for (int order : newSolution.orders()) {
                        totalUnitsPicked += orders.get(order).values().stream()
                                .mapToInt(Integer::intValue)
                                .sum();
                    }
                    int numVisitedAisles = newSolution.aisles().size();

                    System.out.println("Total units: " + totalUnitsPicked);
                    System.out.println("Visited aisles: " + numVisitedAisles);

                    double Fq = totalUnitsPicked - q * numVisitedAisles;

                    if (Math.abs(Fq) < epsilon) break;

                    q = newQ;
                } else {
                    System.out.println("Solução não encontrada ou infactível");
                }

                System.out.println("Tempo decorrido: " + getElapsedTime(stopWatch) + " seg.");

                iteration++;
            } while (iteration < maxIterations && getRemainingTime(stopWatch) > 1);

        } catch (IloException e) {
            e.printStackTrace();
            return null;
        } finally {
            paramSolver.endModel();
        }

        System.out.println("\nTempo decorrido: " + getElapsedTime(stopWatch) + " seg.");

        return currentSolution;
    }

    /*
     * Get the remaining time in seconds
     */
    protected long getRemainingTime(StopWatch stopWatch) {
        return Math.max(
                TimeUnit.SECONDS.convert(MAX_RUNTIME - stopWatch.getTime(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS),
                0);
    }

    public String getElapsedTime(StopWatch stopWatch) {
        long elapsedTimeInMillis = stopWatch.getTime(TimeUnit.MILLISECONDS);
        double elapsedTimeInSeconds = elapsedTimeInMillis / 1000.0;
        return String.format("%.2f", elapsedTimeInSeconds);
    }

    protected boolean isSolutionFeasible(ChallengeSolution challengeSolution) {
        Set<Integer> selectedOrders = challengeSolution.orders();
        Set<Integer> visitedAisles = challengeSolution.aisles();
        if (selectedOrders == null || visitedAisles == null || selectedOrders.isEmpty() || visitedAisles.isEmpty()) {
            return false;
        }

        int[] totalUnitsPicked = new int[nItems];
        int[] totalUnitsAvailable = new int[nItems];

        // Calculate total units picked
        for (int order : selectedOrders) {
            for (Map.Entry<Integer, Integer> entry : orders.get(order).entrySet()) {
                totalUnitsPicked[entry.getKey()] += entry.getValue();
            }
        }

        // Calculate total units available
        for (int aisle : visitedAisles) {
            for (Map.Entry<Integer, Integer> entry : aisles.get(aisle).entrySet()) {
                totalUnitsAvailable[entry.getKey()] += entry.getValue();
            }
        }

        // Check if the total units picked are within bounds
        int totalUnits = Arrays.stream(totalUnitsPicked).sum();
        if (totalUnits < waveSizeLB || totalUnits > waveSizeUB) {
            return false;
        }

        // Check if the units picked do not exceed the units available
        for (int i = 0; i < nItems; i++) {
            if (totalUnitsPicked[i] > totalUnitsAvailable[i]) {
                return false;
            }
        }

        return true;
    }

    protected double computeObjectiveFunction(ChallengeSolution challengeSolution) {
        Set<Integer> selectedOrders = challengeSolution.orders();
        Set<Integer> visitedAisles = challengeSolution.aisles();
        if (selectedOrders == null || visitedAisles == null || selectedOrders.isEmpty() || visitedAisles.isEmpty()) {
            return 0.0;
        }
        int totalUnitsPicked = 0;

        // Calculate total units picked
        for (int order : selectedOrders) {
            totalUnitsPicked += orders.get(order).values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
        }

        // Calculate the number of visited aisles
        int numVisitedAisles = visitedAisles.size();

        // Objective function: total units picked / number of visited aisles
        return (double) totalUnitsPicked / numVisitedAisles;
    }
}
