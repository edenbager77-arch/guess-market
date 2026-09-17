package guessmarket.engine.model;

public class GMOption {
    private final String name;
    private double totalSharesBought;
    public GMOption(String name) { this.name = name; this.totalSharesBought = 0.0; }
    public String getName() { return name; }
    public double getTotalSharesBought() { return totalSharesBought; }
    public void addShares(double q) { this.totalSharesBought += q; }
}
