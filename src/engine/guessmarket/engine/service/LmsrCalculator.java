package guessmarket.engine.service;

/**
 * Logarithmic Market Scoring Rule math, for the binary (2-option) case used
 * throughout Ex1. See course appendix A for the derivation.
 *
 *   Cost function:  C(q1, q2) = b * ln( e^(q1/b) + e^(q2/b) )
 *   Price of option 1:  price1 = e^(q1/b) / ( e^(q1/b) + e^(q2/b) )
 *
 * The cost of a trade is C(after) - C(before). The initial subsidy required
 * to open an event is C(0, 0) = b * ln(2).
 */
public final class LmsrCalculator {

    private LmsrCalculator() {
    }

    public static double cost(int b, double q1, double q2) {
        double e1 = Math.exp(q1 / b);
        double e2 = Math.exp(q2 / b);
        return b * Math.log(e1 + e2);
    }

    public static double price(int b, double qThis, double qOther) {
        double eThis = Math.exp(qThis / b);
        double eOther = Math.exp(qOther / b);
        return eThis / (eThis + eOther);
    }
}
