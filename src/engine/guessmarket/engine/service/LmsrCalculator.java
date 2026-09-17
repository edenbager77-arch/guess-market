package guessmarket.engine.service;

/**
 * LMSR math for the binary case.
 *   C(q1,q2) = b * ln( e^(q1/b) + e^(q2/b) )
 *   price_i  = e^(qi/b) / sum
 *
 * Both are computed with the log-sum-exp shift so that large quantities
 * (where e^(q/b) would overflow to Infinity) still give exact results.
 */
public final class LmsrCalculator {
    private LmsrCalculator() {}

    public static double cost(int b, double q1, double q2) {
        double x1 = q1 / b, x2 = q2 / b;
        double m = Math.max(x1, x2);
        return b * (m + Math.log(Math.exp(x1 - m) + Math.exp(x2 - m)));
    }

    public static double price(int b, double qThis, double qOther) {
        double x1 = qThis / b, x2 = qOther / b;
        double m = Math.max(x1, x2);
        double e1 = Math.exp(x1 - m), e2 = Math.exp(x2 - m);
        return e1 / (e1 + e2);
    }
}
