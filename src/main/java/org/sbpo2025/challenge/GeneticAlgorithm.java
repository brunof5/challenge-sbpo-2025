package org.sbpo2025.challenge;

import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class GeneticAlgorithm {
    private final List<Map<Integer, Integer>> orders;
    private final List<Map<Integer, Integer>> aisles;
    private final int nItems;
    private final int waveSizeLB;
    private final int waveSizeUB;
    private Map<Integer, Set<Integer>> ordersItems;
    private Map<Integer, Set<Integer>> aislesItems;

    public GeneticAlgorithm(List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, int nItems, int waveSizeLB, int waveSizeUB) {
        this.orders = orders;
        this.aisles = aisles;
        this.nItems = nItems;
        this.waveSizeLB = waveSizeLB;
        this.waveSizeUB = waveSizeUB;
        ordersItems = new HashMap<>();
        aislesItems = new HashMap<>();

        // Inicializa pedidosItens
        for (int p = 0; p < orders.size(); p++) {
            ordersItems.put(p, orders.get(p).keySet());
        }
        // Inicializa corredoresItens
        for (int c = 0; c < aisles.size(); c++) {
            aislesItems.put(c, aisles.get(c).keySet());
        }

    }

    public ChallengeSolution solve() {
        Random random = new Random();
        double numGeracoes=10;
        double taxaMutacao = 0.05;
        double taxaCruzamento = 0.7;
        int nPopulacaoInicial = 16;
        List<Individual> populacao = new ArrayList<>();

        // Inicializar população genética
        populacao = inicializarPopulacao(populacao, nPopulacaoInicial, random);

        // Repetir teste de aptidão e cruzamentos até critério de parada
        for (int i=0; i<numGeracoes; i++) {
            List<Individual> pais = new ArrayList<>();
            System.out.print("GERAÇÃO " + i + " ");
            for(Individual ind :populacao){
                System.out.print(aptidao(ind) + " ");
            }
            System.out.println();
            for(int j=0;j<4;j++){
                pais.add(selecao(populacao, random));
            }
            List<Individual> novaPopulacao = new ArrayList<>();
            List<Individual> tempPopulacao = new ArrayList<>();
            novaPopulacao.addAll(cruzamento(pais.get(0), pais.get(1), taxaCruzamento, random));
            novaPopulacao.addAll(cruzamento(pais.get(0), pais.get(2), taxaCruzamento, random));
            novaPopulacao.addAll(cruzamento(pais.get(0), pais.get(3), taxaCruzamento, random));
            novaPopulacao.addAll(cruzamento(pais.get(1), pais.get(2), taxaCruzamento, random));
            novaPopulacao.addAll(cruzamento(pais.get(1), pais.get(3), taxaCruzamento, random));
            novaPopulacao.addAll(cruzamento(pais.get(2), pais.get(3), taxaCruzamento, random));
            novaPopulacao.addAll(cruzamento(pais.get(3), pais.get(2), taxaCruzamento, random));
            novaPopulacao.addAll(cruzamento(pais.get(3), pais.get(1), taxaCruzamento, random));
            for(Individual individuo: novaPopulacao){
                tempPopulacao.add(mutacao(individuo, taxaMutacao, random));
            }
            populacao = tempPopulacao;
            populacao.addAll(pais);
        }

        // Retornar melhor solução encontrada
        populacao.sort((ind1, ind2) -> Double.compare(aptidao(ind2), aptidao(ind1)));
        Individual individuo = populacao.get(0);
        boolean achouFact = false;
        for(Individual i:populacao){
            System.out.println(aptidao(i));
            if(aptidao(i) != -1.0){
                individuo = i;
                achouFact = true;
                break;
            }
        }
        if(!achouFact){
            populacao.sort((a, b) -> Integer.compare(b.converterIndividuoEmSolution().orders().size(), a.converterIndividuoEmSolution().orders().size()));
            individuo = populacao.get(0);
        }
        return individuo.converterIndividuoEmSolution();
    }

    private Individual selecao(List<Individual> populacao, Random random){
        int index1, index2;
        List<Individual> temp = new ArrayList<>();
        index1 = random.nextInt(populacao.size());
        index2 = index1;
        while (index2 == index1){
            index2 = random.nextInt(populacao.size());
        }
        temp.add(populacao.get(index1));
        temp.add(populacao.get(index2));
        temp.sort((ind1, ind2) -> Double.compare(aptidao(ind2), aptidao(ind1)));
        return temp.get(0);
    }

    private Double aptidao(Individual individuo){
        ChallengeSolution challengeSolution =  individuo.converterIndividuoEmSolution();
        Set<Integer> selectedOrders = challengeSolution.orders();
        Set<Integer> visitedAisles = challengeSolution.aisles();
        if (selectedOrders == null || visitedAisles == null || selectedOrders.isEmpty() || visitedAisles.isEmpty()) {
            return -1.0;
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
        double totalUnits = Arrays.stream(totalUnitsPicked).sum();
        if (totalUnits < waveSizeLB || totalUnits > waveSizeUB) {
            return -1.0;
        }

        // Check if the units picked do not exceed the units available
        for (int i = 0; i < nItems; i++) {
            if (totalUnitsPicked[i] > totalUnitsAvailable[i]) {
                return -1.0;
            }
        }

        return totalUnits/visitedAisles.size();
    }

    private List<Individual> inicializarPopulacao(List<Individual> populacao, int nIndividuos, Random random) {
        Individual individuo;
        populacao.add(new Individual(new ImprovedGreedyAlgorithm(orders, aisles, nItems, waveSizeLB, waveSizeUB).solve()));
        int nOrders, nAisles, indice;
        for (int i=0; i<nIndividuos; i++) {
            individuo = new Individual(orders.size(), aisles.size());
            nAisles = random.nextInt(aisles.size());
            nOrders = random.nextInt(orders.size());
            for (int j = 0; j < nOrders; j++) {
                indice = random.nextInt(nOrders);
                individuo.cadeiaOrders.set(indice);
            }
            for (int j = 0; j < nAisles; j++) {
                indice = random.nextInt(nAisles);
                individuo.cadeiaAisles.set(indice);
            }
            populacao.add(individuo);
            /*try {
                populacao.add(repair(individuo.converterIndividuoEmSolution()));
            }catch (ConcurrentModificationException e){
                System.out.println(e.getMessage());
            }*/
        }
        return populacao;
    }

    private Individual repair(ChallengeSolution solucao){
        ChallengeSolution novaSolucao = new ChallengeSolution(solucao.orders(), solucao.aisles());
        for (Integer p : solucao.orders()) {
            for (Integer item : ordersItems.get(p)) {
                boolean coberto = false;

                // Verifica se item já está coberto
                for (Integer c: solucao.aisles()) {
                    if (aislesItems.get(c).contains(item)) {
                        coberto = true;
                        break;
                    }
                }
                // Se não está coberto, ativa algum corredor que o contenha
                if (!coberto) {
                    for (Integer c : solucao.aisles()) {
                        if (aislesItems.get(c).contains(item)) {
                            novaSolucao.aisles().add(c);
                            break; // ativa o primeiro corredor que encontrar
                        }
                    }
                }
            }
        }

        // 2. Desativar corredores inúteis
        for (int c : solucao.aisles()) {
            boolean util = false;
            for (int p : solucao.orders()) {
                for (int item : ordersItems.get(p)) {
                    if (aislesItems.get(c).contains(item)) {
                        util = true;
                        break;
                    }
                }
                if (util) break;
            }
            if (!util) {
                novaSolucao.aisles().remove(c); // corredor não ajuda em nada
            }
        }
        return new Individual(novaSolucao);
    }


    private Individual mutacao(Individual ind, double taxa, Random random){
        for (int i=ind.cadeiaOrders.nextSetBit(0); i>=0; i = ind.cadeiaOrders.nextSetBit(i+1)){
            if(random.nextDouble(1) < taxa){
                ind.cadeiaOrders.flip(i);
            }
        }
        for (int i=ind.cadeiaAisles.nextSetBit(0); i>=0; i = ind.cadeiaAisles.nextSetBit(i+1)){
            if(random.nextDouble(1) < taxa){
                ind.cadeiaAisles.flip(i);
            }
        }
        return ind;
    }

    private List<Individual> cruzamento(Individual ind1, Individual ind2, double taxa, Random random){
        List<Individual> filhos = new ArrayList<>();
        int indice;
        if(random.nextDouble(1) < taxa){
            indice = random.nextInt(ind1.cadeiaOrders.size());
            for(int i=indice; i<ind1.cadeiaOrders.size(); i++){
                boolean a = ind1.cadeiaOrders.get(i);
                boolean b = ind2.cadeiaOrders.get(i);
                ind1.cadeiaOrders.set(i, b);
                ind2.cadeiaOrders.set(i, a);
            }
            indice = random.nextInt(ind1.cadeiaAisles.size());
            for(int i=indice; i<ind1.cadeiaAisles.size(); i++){
                boolean a = ind1.cadeiaAisles.get(i);
                boolean b = ind2.cadeiaAisles.get(i);
                ind1.cadeiaAisles.set(i, b);
                ind2.cadeiaAisles.set(i, a);
            }
        }
        filhos.add(ind1);
        filhos.add(ind2);
        return filhos;
    }
}


