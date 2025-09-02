package org.sbpo2025.challenge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloObjective;

import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.Status;

public class ParametricSolver {
    protected List<Map<Integer, Integer>> orders;
    protected List<Map<Integer, Integer>> aisles;

    private IloCplex cplex;
    private IloNumVar[] x;
    private IloNumVar[] y;
    private IloObjective currentObjective;

    private Integer fixedAisleCount = null;

    private InfoCallback cb;
    private boolean callbackDestroyed = false;

    // Configurações gerais
    private static final double DEFAULT_MIP_GAP = 0.01;
    private static final long IMPROVEMENT_TIMEOUT_SECONDS = 30;

    public ParametricSolver(List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, 
                           int nItems, int waveSizeLB, int waveSizeUB, Integer fixedAisleCount) {
        this.orders = orders;
        this.aisles = aisles;
        this.fixedAisleCount = fixedAisleCount;

        try {
            this.cb = new InfoCallback();
            initializeModel();
            createDecisionVariables();
            addConstraints(nItems, waveSizeLB, waveSizeUB);
        } catch (IloException e) {
            System.err.println("Erro ao criar o modelo: " + e.getMessage());
            e.printStackTrace();
        }
    }

    class InfoCallback extends IloCplex.MIPInfoCallback {
        private double currentBestQ = 0.0;
        private long lastImprovementTime = System.currentTimeMillis();
        private boolean firstSolution = true;
        private boolean useTimeoutCheck = true;
        private boolean abortedForTimeout = false;

        @Override
        protected void main() throws IloException {
            if (hasIncumbent()) {
                double currentQ = calculateCurrentQ();
                
                if (currentQ > currentBestQ) {
                    handleImprovement(currentQ);
                } else if (useTimeoutCheck && shouldAbortForTimeout()) {
                    //System.out.println("Abortando: nenhuma melhoria em " + IMPROVEMENT_TIMEOUT_SECONDS + "s após última melhoria de q");
                    abortedForTimeout = true;
                    abort();
                }
            }
        }

        private double calculateCurrentQ() throws IloException {
            int totalPicked = 0;
            int totalAisles = 0;

            for (int o = 0; o < orders.size(); o++) {
                if (getIncumbentValue(y[o]) > 0.5) {
                    for (int quantity : orders.get(o).values()) {
                        totalPicked += quantity;
                    }
                }
            }

            for (int a = 0; a < aisles.size(); a++) {
                if (getIncumbentValue(x[a]) > 0.5) {
                    totalAisles++;
                }
            }

            return totalAisles > 0 ? (double) totalPicked / totalAisles : 0.0;
        }

        private void handleImprovement(double currentQ) {
            //System.out.println("\nq melhorou de " + currentBestQ + " para " + currentQ);

            currentBestQ = currentQ;
            lastImprovementTime = System.currentTimeMillis();
            firstSolution = false;
        }

        private boolean shouldAbortForTimeout() {
            if (firstSolution) return false;
            
            long currentTime = System.currentTimeMillis();
            double secondsSinceLast = (currentTime - lastImprovementTime) / 1000.0;
            return secondsSinceLast >= IMPROVEMENT_TIMEOUT_SECONDS;
        }

        public void reset() {
            lastImprovementTime = System.currentTimeMillis();
            firstSolution = true;
            abortedForTimeout = false;
        }

        public void setUseTimeoutCheck(boolean useTimeout) {
            this.useTimeoutCheck = useTimeout;
        }

        public void setInitialQ(double q) {
            this.currentBestQ = q;
        }

        public double getCurrentBestQ() {
            return currentBestQ;
        }

        public boolean hasAbortedForTimeout() {
            return abortedForTimeout;
        }
    }

    private void initializeModel() throws IloException {
        cplex = new IloCplex();
        cplex.setOut(null);
    }

    private void createDecisionVariables() throws IloException {
        x = new IloNumVar[aisles.size()];
        y = new IloNumVar[orders.size()];

        for (int a = 0; a < aisles.size(); a++) {
            x[a] = cplex.boolVar();
        }

        for (int o = 0; o < orders.size(); o++) {
            y[o] = cplex.boolVar();
        }

        cplex.use(cb);
    }

    private void addConstraints(int nItems, int waveSizeLB, int waveSizeUB) throws IloException {
        // Restrição de capacidade da wave
        addWaveCapacityConstraints(waveSizeLB, waveSizeUB);
        
        // Restrições de disponibilidade de itens
        addItemAvailabilityConstraints(nItems);
        
        // Restrições de corredores
        addAisleConstraints();
    }

    private void addWaveCapacityConstraints(int waveSizeLB, int waveSizeUB) throws IloException {
        IloLinearNumExpr totalUnits = cplex.linearNumExpr();
        
        for (int o = 0; o < orders.size(); o++) {
            int orderTotal = orders.get(o).values().stream().mapToInt(Integer::intValue).sum();
            totalUnits.addTerm(orderTotal, y[o]);
        }
        
        cplex.addGe(totalUnits, waveSizeLB);
        cplex.addLe(totalUnits, waveSizeUB);
    }

    private void addItemAvailabilityConstraints(int nItems) throws IloException {
        for (int i = 0; i < nItems; i++) {
            IloLinearNumExpr pickedUnits = cplex.linearNumExpr();
            IloLinearNumExpr availableUnits = cplex.linearNumExpr();
            
            for (int o = 0; o < orders.size(); o++) {
                if (orders.get(o).containsKey(i)) {
                    pickedUnits.addTerm(orders.get(o).get(i), y[o]);
                }
            }

            for (int a = 0; a < aisles.size(); a++) {
                if (aisles.get(a).containsKey(i)) {
                    availableUnits.addTerm(aisles.get(a).get(i), x[a]);
                }
            }
            
            cplex.addLe(pickedUnits, availableUnits);
        }
    }

    private void addAisleConstraints() throws IloException {
        IloLinearNumExpr sumAisles = cplex.linearNumExpr();
        
        for (int a = 0; a < aisles.size(); a++) {
            sumAisles.addTerm(1.0, x[a]);
        }

        if (fixedAisleCount != null) {
            cplex.addLe(sumAisles, fixedAisleCount);
        }
        
        cplex.addGe(sumAisles, 1);
    }

    public void updateObjectiveFunction(double q) throws IloException {
        if (currentObjective != null) {
            cplex.delete(currentObjective);
        }

        IloLinearNumExpr obj = cplex.linearNumExpr();
        
        for (int o = 0; o < orders.size(); o++) {
            int orderTotal = orders.get(o).values().stream().mapToInt(Integer::intValue).sum();
            obj.addTerm(orderTotal, y[o]);
        }

        for (int a = 0; a < aisles.size(); a++) {
            obj.addTerm(-q, x[a]);
        }

        currentObjective = cplex.addMaximize(obj);
    }

    public void setInitialSolution(ChallengeSolution initialSolution) throws IloException {
        if (initialSolution == null) return;

        List<IloNumVar> vars = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        Set<Integer> selectedAisles = initialSolution.aisles();
        Set<Integer> selectedOrders = initialSolution.orders();

        // Adicionar variáveis de corredor
        for (int a = 0; a < x.length; a++) {
            vars.add(x[a]);
            values.add(selectedAisles.contains(a) ? 1.0 : 0.0);
        }

        // Adicionar variáveis de pedido
        for (int o = 0; o < y.length; o++) {
            vars.add(y[o]);
            values.add(selectedOrders.contains(o) ? 1.0 : 0.0);
        }

        cplex.addMIPStart(
            vars.toArray(new IloNumVar[0]), 
            values.stream().mapToDouble(Double::doubleValue).toArray()
        );
    }
    
    public void configureForImprove(long timeLimit) throws IloException {
        cplex.setDefaults();

        if (callbackDestroyed) {
            cb = new InfoCallback();
            cplex.use(cb);
            callbackDestroyed = false;
        }
        
        cb.setUseTimeoutCheck(true);
        cb.reset();

        cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 1);
        cplex.setParam(IloCplex.Param.TimeLimit, timeLimit);
    }

    public void configureForPolish(long timeLimit) throws IloException {
        destroyCallback();
        
        cplex.setDefaults();
        
        // Parâmetros obrigatórios
        cplex.setParam(IloCplex.Param.MIP.PolishAfter.Time, 0); // Iniciar polish imediatamente
        cplex.setParam(IloCplex.Param.MIP.Strategy.Search, 1);  // B&B tradicional

        // Outro parâmetros
        cplex.setParam(IloCplex.Param.TimeLimit, timeLimit);
    }

    public void configureForProof(long timeLimit) throws IloException {
        if (!callbackDestroyed) {
            destroyCallback();
        }

        cplex.setDefaults();

        // Parâmetros
        cplex.setParam(IloCplex.Param.Emphasis.MIP, 2); // otimalidade
        cplex.setParam(IloCplex.Param.MIP.Strategy.HeuristicFreq, -1);  // desativa heurísticas
        cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, DEFAULT_MIP_GAP);
        cplex.setParam(IloCplex.Param.TimeLimit, timeLimit);
    }

    private void destroyCallback() throws IloException {
        if (cb != null && !callbackDestroyed) {
            cplex.remove(cb);
            cb = null;
            callbackDestroyed = true;
            //System.out.println("Callback removido do modelo CPLEX");
        }
    }

    public ChallengeSolution solveModel() throws IloException {
        if (!cplex.solve()) {
            System.out.println("Modelo não resolvido. Status: " + cplex.getStatus());
            return null;
        }

        Set<Integer> selectedOrders = new HashSet<>();
        Set<Integer> selectedAisles = new HashSet<>();

        for (int o = 0; o < y.length; o++) {
            if (cplex.getValue(y[o]) > 0.5) {
                selectedOrders.add(o);
            }
        }

        for (int a = 0; a < x.length; a++) {
            if (cplex.getValue(x[a]) > 0.5) {
                selectedAisles.add(a);
            }
        }

        System.out.println("Status da solução: " + cplex.getStatus());
        System.out.println("Gap: " + cplex.getMIPRelativeGap() * 100 + "%");

        return new ChallengeSolution(selectedOrders, selectedAisles);
    }

    public void endModel() {
        try {
            if (!callbackDestroyed) {
                destroyCallback();
            }
        } catch (IloException e) {
            System.err.println("Erro ao destruir callback: " + e.getMessage());
        }

        if (cplex != null) {
            cplex.end();
        }
    }

    public Status getStatus() {
        try {
            return cplex.getStatus();
        } catch (IloException e) {
            System.err.println("Erro ao destruir callback: " + e.getMessage());
        }
        return null;
    }

    public InfoCallback getCb() {
        return cb;
    }
}
