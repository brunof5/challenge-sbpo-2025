package org.sbpo2025.challenge;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.time.StopWatch;

import ilog.concert.IloException;

import ilog.cplex.IloCplex.Status;

public class ChallengeSolver {
    private final long MAX_RUNTIME = 600000; // milliseconds; 10 minutes
    private static final double EPSILON = 1e-3;
    
    // Constantes para distribuição de tempo
    private static final double IMPROVE_TIME_RATIO = 0.22;
    private static final double POLISH_TIME_RATIO = 0.56;
    private static final double MIN_PHASE_TIME_SECONDS = 5; // Tempo mínimo por fase

    protected List<Map<Integer, Integer>> orders;
    protected List<Map<Integer, Integer>> aisles;
    protected int nItems;
    protected int waveSizeLB;
    protected int waveSizeUB;

    private final CapacityGreedyAlgorithm capacityGreedyAlgorithm;
    private final PriorityGreedyAlgorithm priorityGreedyAlgorithm;

    private enum Phase {
        IMPROVE,
        POLISH,
        PROOF
    }

    public ChallengeSolver(
            List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, int nItems, int waveSizeLB, int waveSizeUB) {
        this.orders = orders;
        this.aisles = aisles;
        this.nItems = nItems;
        this.waveSizeLB = waveSizeLB;
        this.waveSizeUB = waveSizeUB;

        capacityGreedyAlgorithm = new CapacityGreedyAlgorithm(orders, aisles, nItems, waveSizeLB, waveSizeUB);
        priorityGreedyAlgorithm = new PriorityGreedyAlgorithm(orders, aisles, nItems, waveSizeLB, waveSizeUB);
    }

    public ChallengeSolution solve(StopWatch stopWatch) {
        int iteration = 0, maxIterations = Integer.MAX_VALUE;
        double bestQ = 0.0, q = 0.0;
        Phase currentPhase = Phase.IMPROVE;
        boolean forcePhaseChange = false;

        // Solução inicial com algoritmos gulosos
        ChallengeSolution currentSolution = capacityGreedyAlgorithm.solve();
        q = printGreedy(currentSolution, "Capacity");
        int aisleMaxBound = capacityGreedyAlgorithm.getMaxAisleBound();

        ChallengeSolution currentSolution2 = priorityGreedyAlgorithm.solve();
        double q2 = printGreedy(currentSolution2, "Priority");
        int aisleMaxBound2 = priorityGreedyAlgorithm.getMaxAisleBound();

        if (q2 > q) {
            currentSolution = currentSolution2;
            q = q2;
        }

        if (isSolutionFeasible(currentSolution)) {
            bestQ = q;
        }

        if (aisleMaxBound2 < aisleMaxBound) {
            aisleMaxBound = aisleMaxBound2;
        }

        // Configuração do solver paramétrico
        ParametricSolver paramSolver = createParametricSolver(aisleMaxBound);
        
        // Cálculo dos thresholds de tempo
        TimeThresholds timeThresholds = calculateTimeThresholds(stopWatch);
        printTimeThresholds(timeThresholds);

        System.out.println("\nTempo decorrido: " + getElapsedTime(stopWatch) + " seg.");
        System.out.println("\n### Parametric solver ###");

        try {
            do {
                if (paramSolver.getCb() != null) {
                    paramSolver.getCb().setInitialQ(q);
                }
                paramSolver.updateObjectiveFunction(q);

                if (currentSolution != null && isSolutionFeasible(currentSolution)) {
                    paramSolver.setInitialSolution(currentSolution);
                }

                long remainingTime = getRemainingTime(stopWatch);

                System.out.println("\nTempo restante: " + remainingTime + " seg, Fase: " + currentPhase);

                // Determinar a fase atual baseada no tempo restante
                Phase newPhase = determinePhase(remainingTime, timeThresholds, currentPhase, forcePhaseChange);
                if (newPhase != currentPhase) {
                    currentPhase = newPhase;
                    forcePhaseChange = false;
                    System.out.println("\n### Mudando para fase: " + currentPhase + " ###");
                }

                PhaseResult phaseResult = executePhase(paramSolver, currentPhase, remainingTime, timeThresholds, iteration);

                if (phaseResult.abortedEarly && currentPhase == Phase.IMPROVE) {
                    //System.out.println("Fase IMPROVE abortou mais cedo - forçando mudança para POLISH... ");
                    forcePhaseChange = true;
                }

                ChallengeSolution newSolution = phaseResult.solution;

                if (newSolution != null && isSolutionFeasible(newSolution)) {
                    double newQ = computeObjectiveFunction(newSolution);
                    
                    boolean optimal = shouldStop(newSolution, currentSolution, bestQ, paramSolver.getStatus());
                    if (processNewSolution(newSolution, currentSolution, newQ, bestQ, iteration)) {
                        bestQ = newQ;
                        currentSolution = newSolution;
                    }

                    if (optimal) {
                        break;
                    }

                    q = newQ;
                } else {
                    System.out.println("Solução não encontrada ou infactível");
                }
                
                System.out.println("Tempo decorrido: " + getElapsedTime(stopWatch) + " seg.");
                iteration++;
                
            } while (iteration < maxIterations && getRemainingTime(stopWatch) > MIN_PHASE_TIME_SECONDS);

        } catch (IloException e) {
            e.printStackTrace();
            return null;
        } finally {
            paramSolver.endModel();
        }

        System.out.println("\nTempo decorrido: " + getElapsedTime(stopWatch) + " seg.");
        return currentSolution;
    }

    private ParametricSolver createParametricSolver(int aisleMaxBound) {
        if (aisleMaxBound == -1) {
            return new ParametricSolver(orders, aisles, nItems, waveSizeLB, waveSizeUB, null);
        } else {
            return new ParametricSolver(orders, aisles, nItems, waveSizeLB, waveSizeUB, aisleMaxBound);
        }
    }

    private static class TimeThresholds {
        final long totalTime;
        final long improveTime;
        final long polishTime;
        final long proofTime;
        final long polishThreshold;
        final long proofThreshold;
        private long moreTime;

        TimeThresholds(long totalTime, long improveTime, long polishTime, long proofTime, long polishThreshold, long proofThreshold) {
            this.totalTime = totalTime;
            this.improveTime = improveTime;
            this.polishTime = polishTime;
            this.proofTime = proofTime;
            this.polishThreshold = polishThreshold;
            this.proofThreshold = proofThreshold;
            this.moreTime = 0;
        }

        public long getMoreTime() {
            return moreTime;
        }

        public void setMoreTime(long moreTime) {
            this.moreTime = moreTime;
        }
    }

    private TimeThresholds calculateTimeThresholds(StopWatch stopWatch) {
        long totalTime = getRemainingTime(stopWatch);
        long improveTime = Math.max((long)(totalTime * IMPROVE_TIME_RATIO), (long)MIN_PHASE_TIME_SECONDS);
        long polishTime = Math.max((long)(totalTime * POLISH_TIME_RATIO), (long)MIN_PHASE_TIME_SECONDS);
        long proofTime = Math.max(totalTime - improveTime - polishTime, (long)MIN_PHASE_TIME_SECONDS);
        
        // Ajustar se a soma exceder o tempo total
        long sum = improveTime + polishTime + proofTime;
        if (sum > totalTime) {
            System.out.println("Ajustando tempos...");
            double factor = (double) totalTime / sum;
            improveTime = Math.max((long)(improveTime * factor), (long)MIN_PHASE_TIME_SECONDS);
            polishTime = Math.max((long)(polishTime * factor), (long)MIN_PHASE_TIME_SECONDS);
            proofTime = Math.max(totalTime - improveTime - polishTime, (long)MIN_PHASE_TIME_SECONDS);
        }

        long proofThreshold = proofTime;
        long polishThreshold = proofThreshold + polishTime;

        return new TimeThresholds(totalTime, improveTime, polishTime, proofTime, polishThreshold, proofThreshold);
    }

    private void printTimeThresholds(TimeThresholds thresholds) {
        System.out.println("\nTotal time: " + thresholds.totalTime);
        System.out.println("Improve time: " + thresholds.improveTime);
        System.out.println("Polish time: " + thresholds.polishTime);
        System.out.println("Proof time: " + thresholds.proofTime);
        System.out.println("Proof threshold: " + thresholds.proofThreshold);
        System.out.println("Polish threshold: " + thresholds.polishThreshold);
    }

    private Phase determinePhase(long remainingTime, TimeThresholds thresholds, Phase currentPhase, boolean forcePhaseChange) {
        if (forcePhaseChange) {
            if (currentPhase == Phase.IMPROVE) {
                return Phase.POLISH;
            }
        }
        
        if (remainingTime > thresholds.polishThreshold) {
            return Phase.IMPROVE;
        } else if (remainingTime > thresholds.proofThreshold) {
            return Phase.POLISH;
        } else {
            return Phase.PROOF;
        }
    }

    private static class PhaseResult {
        final ChallengeSolution solution;
        final boolean abortedEarly;

        PhaseResult(ChallengeSolution solution, boolean abortedEarly) {
            this.solution = solution;
            this.abortedEarly = abortedEarly;
        }
    }

    private PhaseResult executePhase(ParametricSolver paramSolver, Phase phase, long remainingTime, TimeThresholds thresholds, int iteration) throws IloException {
        long timeLimit;
        boolean abortedEarly = false;
        
        switch (phase) {
            case IMPROVE:
                timeLimit = Math.min(remainingTime - thresholds.polishThreshold, thresholds.improveTime);
                timeLimit = Math.max(timeLimit, (long)MIN_PHASE_TIME_SECONDS);
                paramSolver.configureForImprove(timeLimit);
                System.out.println("\n### Improve Phase - Time limit: " + timeLimit + " ###");
                break;
                
            case POLISH:
                timeLimit = Math.min(remainingTime - thresholds.proofThreshold + thresholds.getMoreTime(), thresholds.polishTime + thresholds.getMoreTime());
                timeLimit = Math.max(timeLimit, (long)MIN_PHASE_TIME_SECONDS);
                paramSolver.configureForPolish(timeLimit);
                System.out.println("\n### Polish Phase - Time limit: " + timeLimit + " ###");
                break;
                
            case PROOF:
                timeLimit = Math.min(remainingTime, thresholds.proofTime);
                timeLimit = Math.max(timeLimit, (long)MIN_PHASE_TIME_SECONDS);
                paramSolver.configureForProof(timeLimit);
                System.out.println("\n### Proof Phase - Time limit: " + timeLimit + " ###");
                break;
                
            default:
                throw new IllegalStateException("Fase desconhecida: " + phase);
        }

        long startTime = System.currentTimeMillis();
        ChallengeSolution solution = paramSolver.solveModel();
        long elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
        
        if (phase == Phase.IMPROVE && paramSolver.getCb() != null && paramSolver.getCb().hasAbortedForTimeout()) {
            //System.out.println("Fase IMPROVE terminou em " + elapsedTime + "s (limite era " + timeLimit + "s)");
            abortedEarly = true;
            thresholds.setMoreTime(timeLimit - elapsedTime);
        }

        return new PhaseResult(solution, abortedEarly);
    }

    private boolean processNewSolution(ChallengeSolution newSolution, ChallengeSolution currentSolution, double newQ, double bestQ, int iteration) {
        System.out.println();
        System.out.print("it: " + iteration + ", ");
        System.out.println("q: " + newQ);

        int totalUnitsPicked = 0;
        for (int order : newSolution.orders()) {
            totalUnitsPicked += orders.get(order).values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
        }
        int numVisitedAisles = newSolution.aisles().size();

        System.out.println("Total units: " + totalUnitsPicked);
        System.out.println("Visited aisles: " + numVisitedAisles);

        boolean improved = false;

        if (newQ > bestQ || (newQ == bestQ && newSolution.orders().size() > currentSolution.orders().size())) {
            improved = true;
        }

        return improved;
    }

    private boolean shouldStop(ChallengeSolution newSolution, ChallengeSolution currentSolution, double q, Status status) {
        if (status != Status.Optimal) {
            return false;
        }
        
        int totalUnitsPicked = 0;
        for (int order : newSolution.orders()) {
            totalUnitsPicked += orders.get(order).values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
        }
        int numVisitedAisles = newSolution.aisles().size();
        
        double Fq = totalUnitsPicked - q * numVisitedAisles;
        return Math.abs(Fq) < EPSILON;
    }

    private double printGreedy(ChallengeSolution currentSolution, String type) {
        int totalUnitsPicked = 0;
        for (int order : currentSolution.orders()) {
            totalUnitsPicked += orders.get(order).values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
        }
        int numVisitedAisles = currentSolution.aisles().size();

        double q = 0.0;

        System.out.println("\n### Greedy - " + type + " ###");
        //System.out.println(currentSolution.orders());
        //System.out.println(currentSolution.aisles());
        System.out.println("\nTotal units: " + totalUnitsPicked);
        System.out.println("Visited aisles: " + numVisitedAisles);
        System.out.println("Feasible: " + isSolutionFeasible(currentSolution));
        if (numVisitedAisles > 0) {
            q = (double) totalUnitsPicked / numVisitedAisles;
            System.out.println("q: " + q);
        }

        return q;
    }

    /*
     * Get the remaining time in seconds
     */
    protected long getRemainingTime(StopWatch stopWatch) {
        return Math.max(
                TimeUnit.SECONDS.convert(MAX_RUNTIME - stopWatch.getTime(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS),
                0);
    }

    protected String getElapsedTime(StopWatch stopWatch) {
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
