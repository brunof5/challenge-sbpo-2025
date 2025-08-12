package org.sbpo2025.challenge;

import org.apache.commons.lang3.tuple.Pair;

import java.util.*;



public class GeneticAlgorithm {
    private final List<Map<Integer, Integer>> orders;
    private final List<Map<Integer, Integer>> aisles;
    private final int nItems;
    private final int waveSizeLB;
    private final int waveSizeUB;

    public GeneticAlgorithm(List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, int nItems, int waveSizeLB, int waveSizeUB) {
        this.orders = orders;
        this.aisles = aisles;
        this.nItems = nItems;
        this.waveSizeLB = waveSizeLB;
        this.waveSizeUB = waveSizeUB;
    }

    public ChallengeSolution solve() {
        Random random = new Random();
        double numGeracoes=5;
        double taxaMutacao = 0.02;
        double taxaCruzamento = 0.7;
        int nPopulacaoInicial = 10;
        List<Individual> populacao = new ArrayList<>();

        // Inicializar população genética
        populacao = inicializarPopulacao(populacao, nPopulacaoInicial, random);

        // Repetir teste de aptidão e cruzamentos até critério de parada
        for (int i=0; i<numGeracoes; i++) {

            // Testar aptidão da população inicial;
            populacao.sort((ind1, ind2) -> Double.compare(aptidao(ind2), aptidao(ind1)));

            // Seleciona 2 mais aptos
            Individual ind1 = populacao.get(0);
            Individual ind2 = populacao.get(1);

            // Fazer cruzamento
            List<Individual> filhos;
            filhos = cruzamento(ind1, ind2, taxaCruzamento, random);
            ind1 = mutacao(filhos.get(0), taxaMutacao);
            ind2 = mutacao(filhos.get(1), taxaMutacao);

            populacao.add(ind1);
            populacao.add(ind2);
        }

        // Retornar melhor solução encontrada
        Individual individuo = populacao.get(0);

        return individuo.converterIndividuoEmSolution();
    }

    private Double aptidao(Individual individuo){
        double numItens, numCorredores=0.0;
        Map<Integer, Integer> relacaoItemQuantidade = new HashMap<>();
        BitSet p = individuo.cadeiaOrders;
        BitSet c = individuo.cadeiaAisles;
        for (int i = p.nextSetBit(0); i >= 0; i = p.nextSetBit(i + 1)) {
            Map<Integer, Integer> pedido = orders.get(i);
            for (Map.Entry<Integer, Integer> entrada : pedido.entrySet()) {
                Integer chave = entrada.getKey();
                Integer valor = entrada.getValue();
                relacaoItemQuantidade.put(chave, relacaoItemQuantidade.getOrDefault(chave, 0)+valor);
            }
        }

        numItens = relacaoItemQuantidade.size();
        if (numItens < waveSizeLB || numItens > waveSizeUB){
            return -1.0;
        }
        for (int i = c.nextSetBit(0); i >= 0; i = c.nextSetBit(i + 1)) {
            Map<Integer, Integer> corredor = aisles.get(i);
            for (Map.Entry<Integer, Integer> entrada : corredor.entrySet()) {
                Integer chave = entrada.getKey();
                Integer valor = entrada.getValue();
                relacaoItemQuantidade.put(chave, relacaoItemQuantidade.getOrDefault(chave, 0)-valor);
            }
            numCorredores++;
        }
        for (Map.Entry<Integer, Integer> entrada : relacaoItemQuantidade.entrySet()) {
            if(entrada.getValue() > 0){
                return -1.0;
            }
        }
        return numItens/numCorredores;
    }

    private List<Individual> inicializarPopulacao(List<Individual> populacao, int nIndividuos, Random random) {
        Individual individuo;
        int nOrders, nAisles, indice;
        for (int i=0; i<nIndividuos; i++){
            individuo = new Individual(orders.size(), aisles.size());
            nOrders = random.nextInt(orders.size());
            for (int j=0; j<nOrders; j++){
                indice = random.nextInt(nOrders);
                individuo.cadeiaOrders.set(indice);
            }
            nAisles = random.nextInt(aisles.size());
            for (int j=0; j<nAisles; j++){
                indice = random.nextInt(nAisles);
                individuo.cadeiaAisles.set(indice);
            }
            populacao.add(individuo);
        }
        return populacao;
    }

    private Individual mutacao(Individual ind, double taxa){
        for (int i=ind.cadeiaOrders.nextSetBit(0); i>=0; i = ind.cadeiaOrders.nextSetBit(i+1)){
            if(Math.random() < taxa){
                ind.cadeiaOrders.flip(i);
            }
        }
        for (int i=ind.cadeiaAisles.nextSetBit(0); i>=0; i = ind.cadeiaAisles.nextSetBit(i+1)){
            if(Math.random() < taxa){
                ind.cadeiaAisles.flip(i);
            }
        }
        return ind;
    }

    private List<Individual> cruzamento(Individual ind1, Individual ind2, double taxa, Random random){
        List<Individual> filhos = new ArrayList<>();
        int indice;
        if(Math.random() < taxa){
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


