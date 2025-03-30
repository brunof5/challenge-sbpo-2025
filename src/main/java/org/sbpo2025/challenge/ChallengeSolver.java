package org.sbpo2025.challenge;

import org.apache.commons.lang3.time.StopWatch;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;

import ilog.cplex.IloCplex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
        try {
            IloCplex cplex = new IloCplex();
            cplex.setParam(IloCplex.Param.TimeLimit, getRemainingTime(stopWatch));
    
            int nOrders = orders.size();
            int nAisles = aisles.size();
            double epsilon = 1e-6;
    
            // Variáveis de decisão
            IloNumVar[] x = cplex.boolVarArray(nAisles);
            IloNumVar[] y = cplex.boolVarArray(nOrders);
            IloNumVar t = cplex.numVar(0, Double.MAX_VALUE, "t");
            IloNumVar[] z = cplex.numVarArray(nOrders, 0, Double.MAX_VALUE);
            IloNumVar[] w = cplex.numVarArray(nAisles, 0, Double.MAX_VALUE);
            
            // Restrições
            for (int o = 0; o < nOrders; o++) {
                cplex.addLe(z[o], t);
                cplex.addLe(z[o], y[o]);
                cplex.addGe(z[o], cplex.sum(t, cplex.prod(-1, cplex.diff(1, y[o]))));
            }

            for (int a = 0; a < nAisles; a++) {
                cplex.addLe(w[a], t);
                cplex.addLe(w[a], x[a]);
                cplex.addGe(w[a], cplex.sum(t, cplex.prod(-1, cplex.diff(1, x[a]))));
            }
            
            IloLinearNumExpr totalUnits = cplex.linearNumExpr();
            for (int o = 0; o < nOrders; o++) {
                for (int i : orders.get(o).keySet()) {
                    totalUnits.addTerm(orders.get(o).get(i), z[o]);
                }
            }
            cplex.addGe(totalUnits, cplex.prod(waveSizeLB, t));
            cplex.addLe(totalUnits, cplex.prod(waveSizeUB, t));
            
            for (int i = 0; i < nItems; i++) {
                IloLinearNumExpr pickedUnits = cplex.linearNumExpr();
                IloLinearNumExpr availableUnits = cplex.linearNumExpr();
                
                for (int o = 0; o < nOrders; o++) {
                    if (orders.get(o).containsKey(i)) {
                        pickedUnits.addTerm(orders.get(o).get(i), z[o]);
                    }
                }
                for (int a = 0; a < nAisles; a++) {
                    if (aisles.get(a).containsKey(i)) {
                        availableUnits.addTerm(aisles.get(a).get(i), w[a]);
                    }
                }
                cplex.addLe(pickedUnits, availableUnits);
            }
            
            IloLinearNumExpr sumWa = cplex.linearNumExpr();
            for (int a = 0; a < nAisles; a++) {
                sumWa.addTerm(1.0, w[a]);
            }
            cplex.addGe(sumWa, 1 - epsilon, "sum_w_minus");
            cplex.addLe(sumWa, 1 + epsilon, "sum_w_plus");
            
            // Função objetivo
            cplex.addMaximize(totalUnits);

            // Solução inicial (gulosa)
            ChallengeSolution initialSolution = greedyAlgorithm.solve();
            if (initialSolution != null && isSolutionFeasible(initialSolution)) {
                System.out.println("Solução inicial viável encontrada!");
                List<IloNumVar> mipStartVars = new ArrayList<>();
                List<Double> mipStartValues = new ArrayList<>();

                // y e z
                for (int o = 0; o < nOrders; o++) {
                    mipStartVars.add(y[o]);
                    mipStartValues.add(initialSolution.orders().contains(o) ? 1.0 : 0.0);

                    mipStartVars.add(z[o]);
                    mipStartValues.add(initialSolution.orders().contains(o) ? 1.0 / initialSolution.aisles().size() : 0.0);
                }

                // x e w
                for (int a = 0; a < nAisles; a++) {
                    mipStartVars.add(x[a]);
                    mipStartValues.add(initialSolution.aisles().contains(a) ? 1.0 : 0.0);

                    mipStartVars.add(w[a]);
                    mipStartValues.add(initialSolution.aisles().contains(a) ? 1.0 / initialSolution.aisles().size() : 0.0);
                }

                // t
                mipStartVars.add(t);
                mipStartValues.add(1.0 / initialSolution.aisles().size());

                // Adicionando ao CPLEX
                cplex.addMIPStart(
                    mipStartVars.toArray(new IloNumVar[0]),
                    mipStartValues.stream().mapToDouble(Double::doubleValue).toArray()
                );
            }
            
            // Resolvendo
            Set<Integer> selectedOrders = new HashSet<>();
            Set<Integer> selectedAisles = new HashSet<>();
            if (cplex.solve()) {
                for (int o = 0; o < nOrders; o++) {
                    if (cplex.getValue(y[o]) > 0.5) selectedOrders.add(o);
                }
                for (int a = 0; a < nAisles; a++) {
                    if (cplex.getValue(x[a]) > 0.5) selectedAisles.add(a);
                }

                //System.out.println("Pedidos selecionados:" + selectedOrders);
                //System.out.println("Corredores selecionados:" + selectedAisles);
            }
            cplex.end();
            return new ChallengeSolution(selectedOrders, selectedAisles);

        } catch (IloException e) {
            e.printStackTrace();
        }
        return null;
    }

    /*
     * Get the remaining time in seconds
     */
    protected long getRemainingTime(StopWatch stopWatch) {
        return Math.max(
                TimeUnit.SECONDS.convert(MAX_RUNTIME - stopWatch.getTime(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS),
                0);
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
