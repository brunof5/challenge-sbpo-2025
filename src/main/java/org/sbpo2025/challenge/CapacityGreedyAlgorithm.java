package org.sbpo2025.challenge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.sbpo2025.challenge.LocalSearch.MoveResult;

public class CapacityGreedyAlgorithm {
    protected final List<Map<Integer, Integer>> orders;
    protected final List<Map<Integer, Integer>> aisles;
    protected final int nItems;
    protected final int waveSizeLB;
    protected final int waveSizeUB;

    private int maxAisleBound;

    public CapacityGreedyAlgorithm(List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, int nItems, int waveSizeLB, int waveSizeUB) {
        this.orders = orders;
        this.aisles = aisles;
        this.nItems = nItems;
        this.waveSizeLB = waveSizeLB;
        this.waveSizeUB = waveSizeUB;
        this.maxAisleBound = -1;
    }

    public ChallengeSolution solve() {
        List<Integer> sumPerOrder = new ArrayList<>();
        for (Map<Integer, Integer> order : orders) {
            sumPerOrder.add(order.values().stream().mapToInt(Integer::intValue).sum());
        }

        List<Integer> sumPerOrderSorted = IntStream.range(0, orders.size())
            .boxed()
            .sorted((i, j) -> sumPerOrder.get(j) - sumPerOrder.get(i))
            .collect(Collectors.toList());

        List<Integer> sumPerAisle = new ArrayList<>();
        for (Map<Integer, Integer> aisle : aisles) {
            sumPerAisle.add(aisle.values().stream().mapToInt(Integer::intValue).sum());
        }

        // Usar Map para facilitar remoção de corredores
        Map<Integer, Integer> availableAisles = IntStream.range(0, aisles.size())
            .boxed()
            .collect(Collectors.toMap(i -> i, sumPerAisle::get));

        double bestObjValue = 0;
        ChallengeSolution bestSolution = null;

        // Inicializar v como 1
        for (int v = 1; v <= aisles.size() && !availableAisles.isEmpty(); v++) {
            if (bestObjValue > (double) waveSizeUB / v) {
                maxAisleBound = v - 1;
                break;
            }

            //System.out.println("\n=====   " + v + " corredor(es)  =====");

            // Selecionar os v corredores com maior capacidade dentre os disponíveis
            List<Integer> sortedAvailableAisles = availableAisles.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue() - e1.getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            
            if (sortedAvailableAisles.size() < v) {
                break; // Não há corredores suficientes
            }

            Set<Integer> selectedAisles = new HashSet<>();
            Map<Integer, Integer> availability = new HashMap<>();

            // Selecionar os v corredores com maior capacidade
            for (int i = 0; i < v; i++) {
                int aisleIdx = sortedAvailableAisles.get(i);
                selectedAisles.add(aisleIdx);
                Map<Integer, Integer> aisle = aisles.get(aisleIdx);
                for (Map.Entry<Integer, Integer> entry : aisle.entrySet()) {
                    availability.put(entry.getKey(), availability.getOrDefault(entry.getKey(), 0) + entry.getValue());
                }
            }

            Set<Integer> selectedOrders = new HashSet<>();
            int totalUnits = 0;

            // Tentar incluir pedidos na ordem decrescente de unidades
            for (int orderIdx : sumPerOrderSorted) {
                Map<Integer, Integer> order = orders.get(orderIdx);

                // Verifica se o pedido pode ser atendido com a disponibilidade atual
                boolean canFulfill = true;
                for (Map.Entry<Integer, Integer> item : order.entrySet()) {
                    if (availability.getOrDefault(item.getKey(), 0) < item.getValue()) {
                        canFulfill = false;
                        break;
                    }
                }

                if (!canFulfill) continue;

                // Verifica se excederia o waveSizeUB
                int orderUnits = sumPerOrder.get(orderIdx);
                if (totalUnits + orderUnits > waveSizeUB) continue;

                // Atualiza disponibilidade
                for (Map.Entry<Integer, Integer> item : order.entrySet()) {
                    availability.put(item.getKey(), availability.get(item.getKey()) - item.getValue());
                }

                selectedOrders.add(orderIdx);
                totalUnits += orderUnits;
            }

            //System.out.println("Total units: " + totalUnits);

            if (totalUnits < waveSizeLB) continue;

            // Calcular valor objetivo
            double currentObj = (double) totalUnits / selectedAisles.size();

            //System.out.println("q: " + currentObj);

            ChallengeSolution newSolution = new ChallengeSolution(new HashSet<>(selectedOrders), new HashSet<>(selectedAisles));

            LocalSearch localSearch = new LocalSearch(orders, aisles, nItems, waveSizeLB, waveSizeUB, newSolution);
            MoveResult removedAisles = localSearch.apply(true, true);

            removedAisles.removedAislesMove1.size();

            if (!removedAisles.move1IsEmpty()) {
                for (int removedAisle : removedAisles.removedAislesMove1) {
                    availableAisles.remove(removedAisle);
                    //System.out.println("Corredor " + removedAisle + " marcado como removido");
                }
            }

            if (!removedAisles.move2IsEmpty()) {
                for (int removedAisle : removedAisles.removedAislesMove2) {
                    availableAisles.remove(removedAisle);
                    //System.out.println("Corredor " + removedAisle + " marcado como removido");
                }
            }

            ChallengeSolution localSearchSol = localSearch.getSolution();
            double localSearchObj = localSearch.computeObjectiveFunction(localSearchSol);

            if (localSearchObj > currentObj || 
                (localSearchObj == currentObj && 
                 localSearchSol != null && localSearchSol.orders().size() > selectedOrders.size())) {

                currentObj = localSearchObj;
                newSolution = localSearchSol;
            }

            //System.out.println("\nTotal corredores: " + newSolution.aisles().size());

            // Atualiza melhor solução conforme os critérios
            if (currentObj > bestObjValue ||
                (currentObj == bestObjValue && 
                 bestSolution != null && selectedOrders.size() > bestSolution.orders().size())) {

                bestObjValue = currentObj;
                bestSolution = newSolution;
            }
        }

        return bestSolution;
    }

    public int getMaxAisleBound() {
        return maxAisleBound;
    }
}
