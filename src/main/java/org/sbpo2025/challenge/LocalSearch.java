package org.sbpo2025.challenge;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class LocalSearch extends ChallengeSolver {
    
    private static final int MAX_AISLES_TO_CHECK = 5;
    private static final double MIN_IMPROVEMENT_THRESHOLD = 0.25;
    
    private ChallengeSolution currentSolution;
    private double currentObjectiveValue;
    
    private final RelationshipMaps relationshipMaps;
    private final OrderUnitsCache orderUnitsCache;
    private Map<Integer, Set<Integer>> selectedAisleToSelectedOrders;

    public LocalSearch(List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, 
                      int nItems, int waveSizeLB, int waveSizeUB, ChallengeSolution initialSolution) {
        super(orders, aisles, nItems, waveSizeLB, waveSizeUB);
        
        this.currentSolution = initialSolution;
        this.currentObjectiveValue = computeObjectiveFunction(initialSolution);
        this.relationshipMaps = new RelationshipMaps();
        this.orderUnitsCache = new OrderUnitsCache();
        
        initializeDataStructures();
    }

    /**
     * Resultado de aplicação dos movimentos de busca local
     */
    public static class MoveResult {
        public final Set<Integer> removedAislesMove1;
        public final Set<Integer> removedAislesMove2;

        public MoveResult(Set<Integer> move1Removals, Set<Integer> move2Removals) {
            this.removedAislesMove1 = Set.copyOf(move1Removals);
            this.removedAislesMove2 = Set.copyOf(move2Removals);
        }

        public boolean move1IsEmpty() {
            return removedAislesMove1.size() == 0;
        }

        public boolean move2IsEmpty() {
            return removedAislesMove2.size() == 0;
        }
    }

    /**
     * Encapsula os mapeamentos de relacionamentos entre itens, pedidos e corredores
     */
    private class RelationshipMaps {
        final Map<Integer, Set<Integer>> itemToOrders = new HashMap<>();
        final Map<Integer, Set<Integer>> itemToAisles = new HashMap<>();
        final Map<Integer, Set<Integer>> aisleToOrders = new HashMap<>();
        final Map<Integer, Set<Integer>> orderToAisles = new HashMap<>();

        void initialize() {
            buildItemToOrdersMapping();
            buildItemToAislesMapping();
            buildAisleToOrdersMapping();
            buildOrderToAislesMapping();
        }

        private void buildItemToOrdersMapping() {
            for (int orderIdx = 0; orderIdx < orders.size(); orderIdx++) {
                Map<Integer, Integer> order = orders.get(orderIdx);
                for (int item : order.keySet()) {
                    itemToOrders.computeIfAbsent(item, k -> new HashSet<>()).add(orderIdx);
                }
            }
        }

        private void buildItemToAislesMapping() {
            for (int aisleIdx = 0; aisleIdx < aisles.size(); aisleIdx++) {
                Map<Integer, Integer> aisle = aisles.get(aisleIdx);
                for (int item : aisle.keySet()) {
                    itemToAisles.computeIfAbsent(item, k -> new HashSet<>()).add(aisleIdx);
                }
            }
        }

        private void buildAisleToOrdersMapping() {
            for (int aisleIdx = 0; aisleIdx < aisles.size(); aisleIdx++) {
                Set<Integer> relatedOrders = new HashSet<>();
                for (int item : aisles.get(aisleIdx).keySet()) {
                    Set<Integer> ordersWithItem = itemToOrders.getOrDefault(item, Set.of());
                    relatedOrders.addAll(ordersWithItem);
                }
                aisleToOrders.put(aisleIdx, relatedOrders);
            }
        }

        private void buildOrderToAislesMapping() {
            for (int orderIdx = 0; orderIdx < orders.size(); orderIdx++) {
                Set<Integer> relatedAisles = new HashSet<>();
                for (int item : orders.get(orderIdx).keySet()) {
                    Set<Integer> aislesWithItem = itemToAisles.getOrDefault(item, Set.of());
                    relatedAisles.addAll(aislesWithItem);
                }
                orderToAisles.put(orderIdx, relatedAisles);
            }
        }
    }

    /**
     * Cache para unidades por pedido
     */
    private class OrderUnitsCache {
        private final Map<Integer, Integer> cache = new HashMap<>();

        void updateCache(Set<Integer> selectedOrders) {
            cache.clear();
            for (int order : selectedOrders) {
                int totalUnits = orders.get(order).values().stream()
                        .mapToInt(Integer::intValue).sum();
                cache.put(order, totalUnits);
            }
        }

        int getUnits(int order) {
            return cache.getOrDefault(order, 0);
        }
    }

    private void initializeDataStructures() {
        relationshipMaps.initialize();
        updateSolutionDependentStructures();
    }

    private void updateSolutionDependentStructures() {
        updateSelectedAisleToOrdersMapping();
        orderUnitsCache.updateCache(currentSolution.orders());
    }

    private void updateSelectedAisleToOrdersMapping() {
        selectedAisleToSelectedOrders = new HashMap<>();
        Set<Integer> selectedOrders = currentSolution.orders();

        for (int aisle : currentSolution.aisles()) {
            Set<Integer> relatedOrders = relationshipMaps.aisleToOrders.getOrDefault(aisle, Set.of());
            Set<Integer> filteredOrders = relatedOrders.stream()
                    .filter(selectedOrders::contains)
                    .collect(Collectors.toSet());
            selectedAisleToSelectedOrders.put(aisle, filteredOrders);
        }
    }

    /**
     * Aplica os movimentos de busca local iterativamente até não haver mais melhorias
     */
    public MoveResult apply(boolean enableMove1, boolean enableMove2) {
        Set<Integer> totalRemovedAislesMove1 = new HashSet<>();
        Set<Integer> totalRemovedAislesMove2 = new HashSet<>();

        boolean improvementFound;
        do {
            improvementFound = false;
            
            if (enableMove1) {
                Set<Integer> removedAisles = applyAisleRemovalMove();
                if (!removedAisles.isEmpty()) {
                    totalRemovedAislesMove1.addAll(removedAisles);
                    improvementFound = true;
                    continue;
                }
            }
            
            if (enableMove2) {
                Set<Integer> removedAisles = applyAisleAndOrderRemovalMove();
                if (removedAisles != null && !removedAisles.isEmpty()) {
                    totalRemovedAislesMove2.addAll(removedAisles);
                    improvementFound = true;
                }
            }
        } while (improvementFound);

        return new MoveResult(totalRemovedAislesMove1, totalRemovedAislesMove2);
    }

    /**
     * Movimento 1: Remove corredores inutilizados
     */
    private Set<Integer> applyAisleRemovalMove() {
        Set<Integer> removedAisles = new HashSet<>();
        double bestObjective = currentObjectiveValue;

        Set<Integer> aislesToCheck = new HashSet<>(currentSolution.aisles());
        
        for (int aisleToRemove : aislesToCheck) {
            ChallengeSolution candidateSolution = createSolutionWithoutAisle(aisleToRemove);
            
            if (isSolutionFeasible(candidateSolution)) {
                double newObjective = computeObjectiveFunction(candidateSolution);
                if (newObjective > bestObjective) {
                    currentSolution = candidateSolution;
                    bestObjective = newObjective;
                    removedAisles.add(aisleToRemove);
                }
            }
        }

        if (bestObjective > currentObjectiveValue) {
            currentObjectiveValue = bestObjective;
            updateSolutionDependentStructures();
        }

        return removedAisles;
    }

    private ChallengeSolution createSolutionWithoutAisle(int aisleToRemove) {
        Set<Integer> newAisles = new HashSet<>(currentSolution.aisles());
        newAisles.remove(aisleToRemove);
        return new ChallengeSolution(currentSolution.orders(), newAisles);
    }

    /**
     * Movimento 2: Remove corredores junto com pedidos específicos
     */
    private Set<Integer> applyAisleAndOrderRemovalMove() {
        List<Integer> candidateAisles = getCandidateAislesForMove2();
        if (candidateAisles.isEmpty()) {
            return null;
        }

        int currentTotalUnits = getTotalUnits(currentSolution);
        int futureNumAisles = currentSolution.aisles().size() - 1;
        
        for (int i = 0; i < Math.min(candidateAisles.size(), MAX_AISLES_TO_CHECK); i++) {
            int aisleToRemove = candidateAisles.get(i);
            
            Optional<Set<Integer>> improvement = tryRemoveAisleWithOrders(
                aisleToRemove, currentTotalUnits, futureNumAisles);
            
            if (improvement.isPresent()) {
                return improvement.get();
            }
        }
        
        return null;
    }

    private List<Integer> getCandidateAislesForMove2() {
        return currentSolution.aisles().stream()
            .filter(this::aisleHasAssociatedOrders)
            .sorted(this::compareAislesByOptimizationPotential)
            .collect(Collectors.toList());
    }

    private boolean aisleHasAssociatedOrders(int aisle) {
        Set<Integer> affectedOrders = selectedAisleToSelectedOrders.getOrDefault(aisle, Set.of());
        return !affectedOrders.isEmpty();
    }

    private int compareAislesByOptimizationPotential(int aisle1, int aisle2) {
        Set<Integer> orders1 = selectedAisleToSelectedOrders.get(aisle1);
        Set<Integer> orders2 = selectedAisleToSelectedOrders.get(aisle2);
        
        // Prioriza corredores com menos pedidos
        int size1 = orders1 != null ? orders1.size() : 0;
        int size2 = orders2 != null ? orders2.size() : 0;
        
        if (size1 != size2) {
            return Integer.compare(size1, size2);
        }
        
        // Em caso de empate, prioriza menor soma de unidades
        int sum1 = orders1 != null ? 
            orders1.stream().mapToInt(orderUnitsCache::getUnits).sum() : 0;
        int sum2 = orders2 != null ? 
            orders2.stream().mapToInt(orderUnitsCache::getUnits).sum() : 0;
        
        return Integer.compare(sum1, sum2);
    }

    private Optional<Set<Integer>> tryRemoveAisleWithOrders(int aisleToRemove, 
                                                          int currentTotalUnits, int futureNumAisles) {
        Set<Integer> affectedOrders = selectedAisleToSelectedOrders.get(aisleToRemove);
        
        int maxUnitsToRemove = calculateMaxUnitsToRemove(currentTotalUnits, futureNumAisles);
        if (maxUnitsToRemove <= 0) {
            return Optional.empty();
        }

        List<Integer> viableOrders = getViableOrdersForRemoval(affectedOrders, 
                                                              currentTotalUnits, maxUnitsToRemove);
        if (viableOrders.isEmpty()) {
            return Optional.empty();
        }

        return attemptOrderRemovalSequence(aisleToRemove, viableOrders, maxUnitsToRemove);
    }

    private int calculateMaxUnitsToRemove(int currentTotalUnits, int futureNumAisles) {
        return (int) Math.ceil(currentTotalUnits - (currentObjectiveValue * futureNumAisles));
    }

    private List<Integer> getViableOrdersForRemoval(Set<Integer> affectedOrders, 
                                                   int currentTotalUnits, int maxUnitsToRemove) {
        double unitPercentageThreshold = (double) maxUnitsToRemove / currentTotalUnits;
        
        List<Integer> viableOrders = affectedOrders.stream()
            .filter(order -> orderUnitsCache.getUnits(order) <= Math.ceil(currentTotalUnits * unitPercentageThreshold))
            .sorted(Comparator.comparingInt(orderUnitsCache::getUnits))
            .collect(Collectors.toList());

        // Verifica se a soma total não excede muito o limite
        int totalViableUnits = viableOrders.stream().mapToInt(orderUnitsCache::getUnits).sum();
        if (totalViableUnits > maxUnitsToRemove) {
            double excessRatio = (double) (totalViableUnits - maxUnitsToRemove) / totalViableUnits;
            if (excessRatio >= MIN_IMPROVEMENT_THRESHOLD) {
                return Collections.emptyList();
            }
        }

        return viableOrders;
    }

    private Optional<Set<Integer>> attemptOrderRemovalSequence(int aisleToRemove, 
                                                             List<Integer> viableOrders, 
                                                             int maxUnitsToRemove) {
        Set<Integer> newOrders = new HashSet<>(currentSolution.orders());
        Set<Integer> newAisles = new HashSet<>(currentSolution.aisles());
        newAisles.remove(aisleToRemove);
        
        Set<Integer> removedOrders = new HashSet<>();
        int runningUnitsRemoved = 0;

        for (int orderToRemove : viableOrders) {
            int units = orderUnitsCache.getUnits(orderToRemove);
            
            if (runningUnitsRemoved + units >= maxUnitsToRemove) {
                break;
            }
            
            removedOrders.add(orderToRemove);
            newOrders.remove(orderToRemove);
            runningUnitsRemoved += units;

            ChallengeSolution candidateSolution = new ChallengeSolution(newOrders, newAisles);
            
            if (isSolutionFeasible(candidateSolution)) {
                double newObjective = computeObjectiveFunction(candidateSolution);
                if (newObjective > currentObjectiveValue) {
                    // Aplica a melhoria encontrada
                    currentObjectiveValue = newObjective;
                    currentSolution = candidateSolution;
                    updateSolutionDependentStructures();
                    return Optional.of(Set.of(aisleToRemove));
                }
            }
        }
        
        return Optional.empty();
    }

    private int getTotalUnits(ChallengeSolution solution) {
        Set<Integer> selectedOrders = solution.orders();
        if (selectedOrders == null || selectedOrders.isEmpty()) {
            return 0;
        }

        int[] totalUnitsPicked = new int[nItems];
        for (int order : selectedOrders) {
            for (Map.Entry<Integer, Integer> entry : orders.get(order).entrySet()) {
                totalUnitsPicked[entry.getKey()] += entry.getValue();
            }
        }

        return Arrays.stream(totalUnitsPicked).sum();
    }

    public ChallengeSolution getSolution() {
        return currentSolution;
    }

    public double getCurrentObjectiveValue() {
        return currentObjectiveValue;
    }
}
