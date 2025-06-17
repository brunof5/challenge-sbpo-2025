package org.sbpo2025.challenge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ImprovedGreedyAlgorithm {
    private final List<Map<Integer, Integer>> orders;
    private final List<Map<Integer, Integer>> aisles;
    private final int nItems;
    private final int waveSizeLB;
    private final int waveSizeUB;

    public ImprovedGreedyAlgorithm(List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, int nItems, int waveSizeLB, int waveSizeUB) {
        this.orders = orders;
        this.aisles = aisles;
        this.nItems = nItems;
        this.waveSizeLB = waveSizeLB;
        this.waveSizeUB = waveSizeUB;
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

        List<Integer> sumPerAisleSorted = IntStream.range(0, aisles.size())
            .boxed()
            .sorted((i, j) -> sumPerAisle.get(j) - sumPerAisle.get(i))
            .collect(Collectors.toList());

        int bestObjValue = 0;
        ChallengeSolution bestSolution = null;

        // Inicializar v como nAisles
        for (int v = aisles.size(); v >= 1; v--) {
            Set<Integer> selectedAisles = new HashSet<>();
            Map<Integer, Integer> availability = new HashMap<>();

            // Selecionar os v corredores com maior capacidade
            for (int i = 0; i < v; i++) {
                int aisleIdx = sumPerAisleSorted.get(i);
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

            if (totalUnits < waveSizeLB) continue;

            // Calcular valor objetivo
            int currentObj = totalUnits / selectedAisles.size();

            // Atualiza melhor solução conforme os critérios
            if (currentObj > bestObjValue ||
                (currentObj == bestObjValue && 
                 bestSolution != null && selectedOrders.size() > bestSolution.orders().size())) {

                bestObjValue = currentObj;
                bestSolution = new ChallengeSolution(new HashSet<>(selectedOrders), new HashSet<>(selectedAisles));
            }
        }

        return bestSolution;
    }
}

