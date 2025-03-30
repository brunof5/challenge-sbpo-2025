package org.sbpo2025.challenge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GreedyAlgorithm {
    private final List<Map<Integer, Integer>> orders;
    private final List<Map<Integer, Integer>> aisles;
    private final int nItems;
    private final int waveSizeLB;
    private final int waveSizeUB;

    public GreedyAlgorithm(List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, int nItems, int waveSizeLB, int waveSizeUB) {
        this.orders = orders;
        this.aisles = aisles;
        this.nItems = nItems;
        this.waveSizeLB = waveSizeLB;
        this.waveSizeUB = waveSizeUB;
    }

    public ChallengeSolution solve() {
        Set<Integer> selectedOrders = new HashSet<>();
        Set<Integer> selectedAisles = new HashSet<>();
        int totalUnits = 0;

        // Ordena-se os corredores pela maior qtd de itens
        List<Integer> sortedAisles = sortEntities(aisles, false);

        // Ordena-se os pedidos pela maior qtd de itens
        List<Integer> sortedOrders = sortEntities(orders, true);

        //System.out.println("Pedidos ordenados: " + sortedOrders);
        //System.out.println("Corredores ordenados: " + sortedAisles);
        //System.out.println();

        // Para cada corredor
        for (int a : sortedAisles) {
            Map<Integer, Integer> aisleItems = new HashMap<>(aisles.get(a));
            Iterator<Integer> orderIterator = sortedOrders.iterator();

            //System.out.println("### Analisando corredor - " + a + " ###");

            // Para cada pedido
            while (orderIterator.hasNext()) {
                int o = orderIterator.next();
                Map<Integer, Integer> orderItems = orders.get(o);
                boolean canBeFulfilled = true;
                int ordersTotalUnits = orderItems.values().stream().mapToInt(Integer::intValue).sum();

                //System.out.println("Analisando pedido: " + o);

                if (totalUnits + ordersTotalUnits > waveSizeUB) {
                    //System.out.println("Ultrapassa o máximo");
                    continue;
                }

                for (int item : orderItems.keySet()) {
                    // Corredor não consegue suprir o pedido
                    if (!aisleItems.containsKey(item) || aisleItems.get(item) < orderItems.get(item)) {
                        //System.out.println("Corredor " + a + " não consegue suprir o pedido " + o);
                        canBeFulfilled = false;
                        break;
                    }
                }

                // Atualizando as variáveis
                if (canBeFulfilled) {
                    for (int item : orderItems.keySet()) {
                        aisleItems.put(item, aisleItems.get(item) - orderItems.get(item));
                        if (aisleItems.get(item) == 0) {
                            aisleItems.remove(item);
                        }
                    }

                    selectedOrders.add(o);
                    selectedAisles.add(a);
                    totalUnits += ordersTotalUnits;
                    orderIterator.remove();
                }

                if (totalUnits >= waveSizeUB) {
                    break;
                }
            }
            //System.out.println();
            if (totalUnits >= waveSizeUB) {
                break;
            }
        }

        //System.out.println("Pedidos iniciais selecionados: " + selectedOrders);
        //System.out.println("Corredores iniciais selecionados: " + selectedAisles);

        return new ChallengeSolution(selectedOrders, selectedAisles);
    }

    private List<Integer> sortEntities(List<Map<Integer, Integer>> entities, boolean isOrders) {
        List<Integer> sortedEntities = isOrders ? new LinkedList<>() : new ArrayList<>();
        
        for (int i = 0; i < entities.size(); i++) {
            sortedEntities.add(i);
        }

        sortedEntities.sort((e1, e2) -> Integer.compare(
            entities.get(e2).values().stream().mapToInt(Integer::intValue).sum(),
            entities.get(e1).values().stream().mapToInt(Integer::intValue).sum()
        ));

        return sortedEntities;
    }
}
