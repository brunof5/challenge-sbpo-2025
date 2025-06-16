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

public class ParametricSolver {
    private IloCplex cplex;
    private IloNumVar[] x;
    private IloNumVar[] y;
    private IloObjective currentObjective;

    public ParametricSolver(List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, int nItems, int waveSizeLB, int waveSizeUB) {
        try {
            initializeModel();
            createDecisionVariables(orders, aisles);
            addConstraints(orders, aisles, nItems, waveSizeLB, waveSizeUB);

        } catch (IloException e) {
            System.out.println("Erro ao criar o modelo");
        }
    }

    private void initializeModel() throws IloException {
        cplex = new IloCplex();
        cplex.setOut(null);
        cplex.setParam(IloCplex.Param.MIP.Tolerances.MIPGap, 1);
    }

    private void createDecisionVariables(List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles) throws IloException {
        x = new IloNumVar[aisles.size()];
        y = new IloNumVar[orders.size()];

        for (int a = 0; a < aisles.size(); a++) {
            x[a] = cplex.boolVar("x" + a);
        }

        for (int o = 0; o < orders.size(); o++) {
            y[o] = cplex.boolVar("y" + o);
        }
    }

    private void addConstraints(List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, int nItems, int waveSizeLB, int waveSizeUB) throws IloException {
        IloLinearNumExpr totalUnits = cplex.linearNumExpr();
        for (int o = 0; o < orders.size(); o++) {
            int sum = 0;
            for (Map.Entry<Integer, Integer> entry : orders.get(o).entrySet()) {
                sum += entry.getValue();
            }
            totalUnits.addTerm(sum, y[o]);
        }
        cplex.addGe(totalUnits, waveSizeLB);
        cplex.addLe(totalUnits, waveSizeUB);

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
        
        IloLinearNumExpr sumXa = cplex.linearNumExpr();
        for (int a = 0; a < aisles.size(); a++) {
            sumXa.addTerm(1.0, x[a]);
        }
        cplex.addGe(sumXa, 1);
    }

    public void updateObjectiveFunction(List<Map<Integer, Integer>> orders, List<Map<Integer, Integer>> aisles, double q) throws IloException {
        if (currentObjective != null) {
            cplex.delete(currentObjective);
        }

        IloLinearNumExpr obj = cplex.linearNumExpr();
        for (int o = 0; o < orders.size(); o++) {
            int sum = 0;
            for (Map.Entry<Integer, Integer> entry : orders.get(o).entrySet()) {
                sum += entry.getValue();
            }
            obj.addTerm(sum, y[o]);
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

        for (int a = 0; a < x.length; a++) {
            vars.add(x[a]);
            values.add(selectedAisles.contains(a) ? 1.0 : 0.0);
        }

        for (int o = 0; o < y.length; o++) {
            vars.add(y[o]);
            values.add(selectedOrders.contains(o) ? 1.0 : 0.0);
        }

        cplex.addMIPStart(vars.toArray(new IloNumVar[0]), values.stream().mapToDouble(Double::doubleValue).toArray());
    }

    public void setTimeLimit(long timeRemaining) throws IloException {
        cplex.setParam(IloCplex.Param.TimeLimit, timeRemaining);
    }

    public ChallengeSolution solveModel() throws IloException {
        if (!cplex.solve()) return null;

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

        return new ChallengeSolution(selectedOrders, selectedAisles);
    }

    public void endModel() {
        if (cplex != null) {
            cplex.end();
        }
    }
}
