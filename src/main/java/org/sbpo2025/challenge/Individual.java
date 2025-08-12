package org.sbpo2025.challenge;

import java.util.*;

public class Individual {
    public BitSet cadeiaOrders;
    public BitSet cadeiaAisles;

    public Individual(int nOrders, int nAisles){
        this.cadeiaOrders = new BitSet(nOrders);
        this.cadeiaAisles = new BitSet(nAisles);
    }

    public ChallengeSolution converterIndividuoEmSolution(){
        Set<Integer> pedidos = new HashSet<>();
        Set<Integer> corredores = new HashSet<>();
        for (int i = cadeiaOrders.nextSetBit(0); i >= 0; i = cadeiaOrders.nextSetBit(i + 1)) {
            pedidos.add(i);
        }
        for (int i = cadeiaAisles.nextSetBit(0); i >= 0; i = cadeiaAisles.nextSetBit(i + 1)) {
            corredores.add(i);
        }
        return new ChallengeSolution(pedidos, corredores);
    }

    @Override
    public String toString(){
        ChallengeSolution solucao = converterIndividuoEmSolution();
        return "{Pedidos: " + solucao.orders() + "; Corredores: " + solucao.aisles() + "}";
    }

}
