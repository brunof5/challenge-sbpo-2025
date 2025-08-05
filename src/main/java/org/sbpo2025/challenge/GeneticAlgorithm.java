package org.sbpo2025.challenge;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        BitSet cadeiaOrders = new BitSet(orders.size());
        BitSet cadeiaAisles = new BitSet(aisles.size());
        double resultado = 0, numGeracoes=5;
        double taxaMutacao = 0.2;

        // Inicializar população genética
        int nOrders = random.nextInt(orders.size());
        for (int i = 0; i<nOrders; i++){
            int indice = random.nextInt(orders.size());
            cadeiaOrders.set(indice);
        }
        int nAisles = random.nextInt(aisles.size());
        for (int i=0; i<nAisles; i++){
            int indice = random.nextInt(aisles.size());
            cadeiaAisles.set(indice);
        }
        // Repetir teste de aptidão e cruzamentos até critério de parada
        for (int i=0; i<numGeracoes; i++) {

            // Testar aptidão da população inicial
            resultado = aptidao(cadeiaOrders, cadeiaAisles);

            // Fazer cruzamento
            for (int j = 0; j < orders.size(); j++) {
                if (random.nextDouble() < taxaMutacao) {
                    cadeiaOrders.flip(j);
                }
            }
            for (int j = 0; j < aisles.size(); j++) {
                if (random.nextDouble() < taxaMutacao) {
                    cadeiaAisles.flip(j);
                }
            }
        }

        // Retornar melhor solução encontrada
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

    private Integer aptidao(BitSet p, BitSet c){
        int numItens, numCorredores=0;
        Map<Integer, Integer> relacaoPedidoQuantidade = new HashMap<>();
        for (int i = p.nextSetBit(0); i >= 0; i = p.nextSetBit(i + 1)) {
            Map<Integer, Integer> pedido = orders.get(i);
            for (Map.Entry<Integer, Integer> entrada : pedido.entrySet()) {
                Integer chave = entrada.getKey();
                Integer valor = entrada.getValue();
                relacaoPedidoQuantidade.put(chave, relacaoPedidoQuantidade.getOrDefault(chave, 0)+valor);
            }
        }

        numItens = relacaoPedidoQuantidade.size();
        if (numItens < waveSizeLB || numItens > waveSizeUB){
            return -1;
        }
        for (int i = c.nextSetBit(0); i >= 0; i = c.nextSetBit(i + 1)) {
            Map<Integer, Integer> corredor = aisles.get(i);
            for (Map.Entry<Integer, Integer> entrada : corredor.entrySet()) {
                Integer chave = entrada.getKey();
                Integer valor = entrada.getValue();
                relacaoPedidoQuantidade.put(chave, relacaoPedidoQuantidade.getOrDefault(chave, 0)-valor);
            }
            numCorredores++;
        }
        for (Map.Entry<Integer, Integer> entrada : relacaoPedidoQuantidade.entrySet()) {
            if(entrada.getValue() < 0){
                return -1;
            }
        }
        return numItens/numCorredores;
    }
}

