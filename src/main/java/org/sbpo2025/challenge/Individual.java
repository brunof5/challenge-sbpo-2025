package org.sbpo2025.challenge;

import java.util.BitSet;

public class Individual {
    public BitSet cadeiaOrders;
    public BitSet cadeiaAisles;

    public Individual(int nOrders, int nAisles){
        this.cadeiaOrders = new BitSet(nOrders);
        this.cadeiaAisles = new BitSet(nAisles);
    }

}
