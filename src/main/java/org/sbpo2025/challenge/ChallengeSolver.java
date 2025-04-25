package org.sbpo2025.challenge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;

import ilog.cplex.IloCplex;

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
        int nOrders = orders.size();
        int nAisles = aisles.size();
        int iteration = 0, maxIterations = 15;
        double bestQ = 0.0, lastQ, q = 0.0, epsilon = 1e-4;

        Set<Integer> selectedOrdersGlobal = new HashSet<>();
        Set<Integer> selectedAislesGlobal = new HashSet<>();

        // Solução inicial (gulosa)
        ChallengeSolution initialSolution = greedyAlgorithm.solve();

        List<Double> mipStartValues = new ArrayList<>();
        if (initialSolution != null && isSolutionFeasible(initialSolution)) {
            // y
            for (int o = 0; o < nOrders; o++) {
                mipStartValues.add(initialSolution.orders().contains(o) ? 1.0 : 0.0);
            }

            // x
            for (int a = 0; a < nAisles; a++) {
                mipStartValues.add(initialSolution.aisles().contains(a) ? 1.0 : 0.0);
            }
        }

        do {
            lastQ = q;
            try {
                IloCplex cplex = new IloCplex();
                cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 1);
                //cplex.setOut(null);
                //double remainingTime = getRemainingTime(stopWatch);
                //double maxAllowedTime = remainingTime / Math.max(1, maxIterations - iteration);
                //double iterationLimit = Math.min(remainingTime, maxAllowedTime);
        
                // Variáveis de decisão
                IloNumVar[] x = new IloNumVar[nAisles];
                for (int a = 0; a < nAisles; a++) {
                    x[a] = cplex.boolVar("x" + a);
                }
                IloNumVar[] y = new IloNumVar[nOrders];
                for (int o = 0; o < nOrders; o++) {
                    y[o] = cplex.boolVar("y" + o);
                }

                // Função objetivo
                IloLinearNumExpr obj = cplex.linearNumExpr();
                for (int o = 0; o < nOrders; o++) {
                    for (int i : orders.get(o).keySet()) {
                        obj.addTerm(orders.get(o).get(i), y[o]);
                    }
                }
                for (int a = 0; a < nAisles; a++) {
                    obj.addTerm(-q, x[a]);
                }
                cplex.addMaximize(obj);

                // Restrições
                IloLinearNumExpr totalUnits = cplex.linearNumExpr();
                for (int o = 0; o < nOrders; o++) {
                    for (int i : orders.get(o).keySet()) {
                        totalUnits.addTerm(orders.get(o).get(i), y[o]);
                    }
                }
                cplex.addGe(totalUnits, waveSizeLB);
                cplex.addLe(totalUnits, waveSizeUB);

                for (int i = 0; i < nItems; i++) {
                    IloLinearNumExpr pickedUnits = cplex.linearNumExpr();
                    IloLinearNumExpr availableUnits = cplex.linearNumExpr();
                    
                    for (int o = 0; o < nOrders; o++) {
                        if (orders.get(o).containsKey(i)) {
                            pickedUnits.addTerm(orders.get(o).get(i), y[o]);
                        }
                    }
                    for (int a = 0; a < nAisles; a++) {
                        if (aisles.get(a).containsKey(i)) {
                            availableUnits.addTerm(aisles.get(a).get(i), x[a]);
                        }
                    }
                    cplex.addLe(pickedUnits, availableUnits);
                }
                
                IloLinearNumExpr sumXa = cplex.linearNumExpr();
                for (int a = 0; a < nAisles; a++) {
                    sumXa.addTerm(1.0, x[a]);
                }
                cplex.addGe(sumXa, 1);

                if (initialSolution != null && isSolutionFeasible(initialSolution)) {
                    List<IloNumVar> mipStartVars = new ArrayList<>();
                    // y
                    for (int o = 0; o < nOrders; o++) {
                        mipStartVars.add(y[o]);
                    }
        
                    // x
                    for (int a = 0; a < nAisles; a++) {
                        mipStartVars.add(x[a]);
                    }
        
                    // Adicionando ao CPLEX
                    cplex.addMIPStart(
                        mipStartVars.toArray(new IloNumVar[0]),
                        mipStartValues.stream().mapToDouble(Double::doubleValue).toArray()
                    );
                }

                cplex.setParam(IloCplex.Param.TimeLimit, getRemainingTime(stopWatch));

                // Resolvendo
                if (cplex.solve()) {
                    Set<Integer> selectedOrders = new HashSet<>();
                    Set<Integer> selectedAisles = new HashSet<>();
                    double totalPicked = 0;
                    double totalAisles = 0;

                    for (int o = 0; o < nOrders; o++) {
                        if (cplex.getValue(y[o]) > 0.5) {
                            selectedOrders.add(o);
                            for (int i : orders.get(o).keySet()) {
                                totalPicked += orders.get(o).get(i);
                            }
                        }
                    }
                    for (int a = 0; a < nAisles; a++) {
                        if (cplex.getValue(x[a]) > 0.5) {
                            selectedAisles.add(a);
                            totalAisles += 1;
                        }
                    }

                    if (totalAisles > 0) {
                        System.out.println();
                        System.out.println("Total unidades: " + totalPicked);
                        System.out.println("Total corredores: " + totalAisles);
                        q = totalPicked / totalAisles;
                    }
                    
                    System.out.println();
                    System.out.println("q: " + q);
                    System.out.println("it: " + iteration);

                    if (q > bestQ) {
                        bestQ = q;
                        selectedAislesGlobal = selectedAisles;
                        selectedOrdersGlobal = selectedOrders;
                    }

                    if (Math.abs(q - lastQ) < epsilon) {
                        break;
                    }
                }
                cplex.end();

            } catch (IloException e) {
                e.printStackTrace();
                return null;
            }
            iteration++;

            // Verificando se o tempo limite foi atingido
            if (getRemainingTime(stopWatch) <= 1) {
                break;
            }

        } while (iteration < maxIterations);

        System.out.println("\nTempo decorrido: " + getElapsedTime(stopWatch) + " seg.");

        return new ChallengeSolution(selectedOrdersGlobal, selectedAislesGlobal);
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
